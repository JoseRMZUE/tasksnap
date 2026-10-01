package ue.edu.co.tasksnap.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;
import ue.edu.co.tasksnap.data.local.dao.OrdenDao;
import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.entity.Orden;
import ue.edu.co.tasksnap.network.ApiService;
import ue.edu.co.tasksnap.network.OrdenDto;
import ue.edu.co.tasksnap.network.OrdenPushDto;
import ue.edu.co.tasksnap.network.RetrofitClient;

/**
 * SYNC MANAGER: corazon de la sincronizacion remota (RN-7, componente 4 del Acta v5).
 *
 * Responsabilidad: tomar la cola de salida de Room (ordenes con sincronizado = 0),
 * empujarla al backend Spring Boot y, al recibir confirmacion (2xx), estampar el
 * sello: sincronizado = 1 + num_orden_remoto con el ID asignado por el servidor.
 *
 * Estrategia (trazas al Acta v5, ServiPro S.A.S., 2026):
 *  - Secuencial y en background (ExecutorService de 1 hilo): una orden a la vez,
 *    nunca bloqueando el hilo de UI; el orden de la cola se respeta.
 *  - Fail-safe (Oportunidad 1): una orden que falla queda con sincronizado = 0
 *    y el resto de la cola continua; el proximo sync la recoge. Nada se pierde.
 *  - POST si nunca fue sincronizada; PUT si ya tiene ID remoto; y si el PUT
 *    responde 404 (el backend con H2 en memoria fue reiniciado y perdio la fila),
 *    se re-crea con POST: el ID remoto es una cache, no una promesa eterna.
 *  - Timeouts de 15 s heredados de RetrofitClient: fail-fast ante servidor
 *    lento o dormido (mitigacion del Riesgo 2 del Acta).
 *
 * Equivalencia conceptual: es el "cartero" entre Room (fuente de verdad del
 * dispositivo) y la API (fuente de verdad del equipo).
 */
public class SyncManager {

    private static final String TAG = "SYNC_MANAGER";

    /** Contrato de respuesta del sync, siempre notificado en el hilo de UI. */
    public interface CallbackSync {
        void onExito(int exitosas, int fallidas, int totalEnServidor);
        void onError(String mensaje);
    }

    private final OrdenDao ordenDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** @param context cualquier contexto; obtiene el singleton de AppDatabase. */
    public SyncManager(Context context) {
        this.ordenDao = AppDatabase.getInstance(context).ordenDao();
    }

    /**
     * Procesa la cola de ordenes pendientes (sincronizado = 0, activo = 1).
     * Se ejecuta en background; el resultado llega al callback en hilo de UI.
     *
     * @param callback recibe conteo de exitosas/fallidas, o un error de sistema.
     */
    public void sincronizarOrdenesPendientes(CallbackSync callback) {
        executor.execute(() -> {
            try {
                List<Orden> pendientes = ordenDao.listarNoSincronizadas();

                int exitosas = 0;
                int fallidas = 0;

                if (!pendientes.isEmpty()) {
                    Log.i(TAG, "Iniciando sync de " + pendientes.size() + " orden(es)...");
                    for (Orden orden : pendientes) {
                        if (enviarOrden(orden)) {
                            exitosas++;
                        } else {
                            fallidas++;
                        }
                    }
                }

                // Lado "pull": consulta cuántas órdenes hay en total en el servidor.
                // Si falla (sin red, servidor dormido), simplemente se informa 0;
                // no afecta el resultado del push de arriba, que ya quedó sellado.
                int totalRemoto = contarOrdenesRemotas();

                final int e = exitosas;
                final int f = fallidas;
                final int t = totalRemoto;
                Log.i(TAG, "Sync finalizado: " + e + " exitosa(s), " + f + " fallida(s), "
                        + t + " orden(es) en el servidor.");
                mainHandler.post(() -> callback.onExito(e, f, t));

            } catch (Exception ex) {
                Log.e(TAG, "Error critico en SyncManager", ex);
                mainHandler.post(() -> callback.onError("Error de sincronizacion: " + ex.getMessage()));
            }
        });
    }

    /**
     * Empuja UNA orden al backend (llamada sincrona, solo desde background).
     *
     * Decision de verbo HTTP:
     *  - num_orden_remoto == null -> POST /api/ordenes (nunca cruzo la frontera).
     *  - num_orden_remoto != null -> PUT /api/ordenes/{id} (actualizar recurso).
     *    Si el PUT responde 404 (backend H2 reiniciado perdio la fila),
     *    se re-crea con POST y se actualiza el ID remoto al sellar.
     *
     * @return true si el servidor confirmo (2xx) y la fila local fue sellada.
     */
    private boolean enviarOrden(Orden orden) {
        try {
            ApiService api = RetrofitClient.getInstance().getApiService();
            OrdenPushDto push = aDto(orden);

            Response<OrdenDto> respuesta;
            if (orden.getNumOrdenRemoto() == null) {
                respuesta = api.crearOrden(push).execute();
            } else {
                respuesta = api.actualizarOrden(orden.getNumOrdenRemoto(), push).execute();
                if (respuesta.code() == 404) {
                    Log.w(TAG, "La orden remota #" + orden.getNumOrdenRemoto()
                            + " ya no existe; se re-crea con POST.");
                    respuesta = api.crearOrden(push).execute();
                }
            }

            if (respuesta.isSuccessful() && respuesta.body() != null) {
                Long idRemoto = respuesta.body().getNumOrden();
                ordenDao.marcarSincronizada(orden.getNumOrden(), idRemoto);
                Log.i(TAG, "Orden local #" + orden.getNumOrden()
                        + " sincronizada como remota #" + idRemoto);
                return true;
            }

            Log.w(TAG, "Fallo sync orden #" + orden.getNumOrden()
                    + ": HTTP " + respuesta.code());
            return false;

        } catch (Exception e) {
            // Timeout, sin red, servidor dormido: la orden queda en cola (fail-safe).
            Log.w(TAG, "Fallo de red orden #" + orden.getNumOrden() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Consulta GET /api/ordenes y devuelve cuántas hay en total en el servidor.
     * Llamada síncrona (.execute()), igual que enviarOrden: ya estamos en background.
     */
    private int contarOrdenesRemotas() {
        try {
            ApiService api = RetrofitClient.getInstance().getApiService();
            Response<List<OrdenDto>> respuesta = api.listarOrdenesRemotas().execute();
            if (respuesta.isSuccessful() && respuesta.body() != null) {
                return respuesta.body().size();
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo consultar el total remoto: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Traduce la entidad Room al contrato de escritura del backend.
     * El DTO omite numOrden y sincronizado a proposito: el servidor gobierna ambos.
     */
    private OrdenPushDto aDto(Orden orden) {
        OrdenPushDto push = new OrdenPushDto();
        push.setTecnicoId(orden.getTecnicoId());
        push.setServicioId(orden.getServicioId());
        push.setClienteId(orden.getClienteId());
        push.setDescripcion(orden.getDescripcion());
        push.setFechaServicio(orden.getFechaServicio());
        push.setEstado(orden.getEstado());
        push.setFechaCreacion(orden.getFechaCreacion());
        push.setFechaCierre(orden.getFechaCierre());
        push.setActivo(orden.isActivo());
        return push;
    }
}