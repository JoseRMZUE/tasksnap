package ue.edu.co.tasksnap.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ue.edu.co.tasksnap.data.local.dao.EvidenciaDao;
import ue.edu.co.tasksnap.data.local.dao.OrdenDao;
import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.entity.Orden;
import ue.edu.co.tasksnap.data.local.pojo.OrdenResumen;
import ue.edu.co.tasksnap.network.ApiService;
import ue.edu.co.tasksnap.network.RetrofitClient;
import ue.edu.co.tasksnap.util.FormatoFecha;
import ue.edu.co.tasksnap.util.RecordatorioScheduler;
import ue.edu.co.tasksnap.util.SesionLocal;

/**
 * Repositorio de órdenes de trabajo: ÚNICA clase autorizada a operar la tabla
 * "ordenes" y a aplicar sus reglas de negocio (RFN-11).
 *
 * Patrón de hilos del proyecto:
 *  - Toda operación de base de datos corre en un ExecutorService de un solo hilo
 *    (las consultas Room son síncronas y bloquearían el hilo de UI).
 *  - Toda respuesta viaja de vuelta al hilo de UI con un Handler, para que la
 *    Activity pueda tocar vistas sin excepción.
 *
 * Reglas de negocio que viven aquí (y en ningún otro lugar):
 *  - RN-1: solo se aceptan estados PENDIENTE o COMPLETADA.
 *  - RN-2: no se permite pasar a COMPLETADA sin al menos una evidencia activa.
 *  - RN-3: fecha_cierre se graba al completar y se limpia al reabrir.
 *  - RN-4: el tecnico_id se toma de SesionLocal, nunca de parámetros de UI.
 *  - RN-6: el borrado es lógico y arrastra cascada lógica de evidencias.
 *  - RN-7: toda escritura deja sincronizado = 0 (cola de sync).
 *  - RN-8: fecha_creacion y fecha_cierre se fabrican con FormatoFecha.
 *
 * Efectos de dispositivo (RF-13, Riesgo 1):
 *  - El repositorio programa/cancela el recordatorio de cita como efecto
 *    secundario cohesivo de las transiciones de estado, NUNCA desde la UI.
 *  - Para no acoplar las pruebas al AlarmManager, el contexto de aplicación es
 *    nullable: el constructor inyectable (db, sesion) pasa context = null y los
 *    hooks se saltan, preservando el comportamiento observable ya validado.
 *
 * Este repositorio constituye el CRUD local #1 de la matriz de cumplimiento
 * (Requisito 2 del curso): Create, Read, Update y Delete completos offline.
 */
public class OrdenRepository {

    /**
     * Contrato de respuesta asíncrona para todas las operaciones del repositorio.
     * onExito y onError se invocan SIEMPRE en el hilo de UI.
     *
     * @param <T> tipo del dato devuelto en caso de éxito.
     */
    public interface Callback<T> {
        /** @param dato resultado de la operación (ID, filas afectadas o lista). */
        void onExito(T dato);

        /** @param mensaje descripción legible del fallo, lista para mostrar al usuario. */
        void onError(String mensaje);
    }

    /** Acceso a la tabla ordenes. */
    private final OrdenDao ordenDao;

    /** Acceso a la tabla evidencias (necesario para RN-2 y cascada lógica). */
    private final EvidenciaDao evidenciaDao;

    /** Sesión activa: fuente del tecnicoId (RN-4). */
    private final SesionLocal sesion;

    /** Contexto de aplicación para efectos de dispositivo (AlarmManager, RF-13); null en pruebas. */
    private final Context context;

    /** Hilo secundario único y serializado para todas las operaciones de BD. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Puente de regreso al hilo de UI para los callbacks. */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Constructor de PRODUCCIÓN: singleton real + sesión real + contexto de app.
     * Lo usan MainActivity y cualquier componente de UI.
     *
     * @param context cualquier contexto de la app.
     */
    public OrdenRepository(Context context) {
        this(AppDatabase.getInstance(context),
                new SesionLocal(context),
                context.getApplicationContext());
    }

    /**
     * Constructor INYECTABLE usado por los tests (CS-02): context = null, por lo
     * que los hooks de recordatorio se saltan y el comportamiento observable de
     * las reglas de negocio es idéntico al ya validado (4/4 verdes).
     *
     * @param db     base de datos (real o in-memory).
     * @param sesion sesión activa de donde se toma el tecnicoId (RN-4).
     */
    public OrdenRepository(AppDatabase db, SesionLocal sesion) {
        this(db, sesion, null);
    }

    /**
     * Constructor completo: permite inyectar contexto de aplicación para los
     * efectos secundarios de dispositivo (programar/cancelar recordatorios, RF-13).
     *
     * @param db         base de datos.
     * @param sesion     sesión activa (RN-4).
     * @param appContext contexto de aplicación para AlarmManager; null desactiva
     *                   los recordatorios (modo prueba).
     */
    public OrdenRepository(AppDatabase db, SesionLocal sesion, Context appContext) {
        this.ordenDao = db.ordenDao();
        this.evidenciaDao = db.evidenciaDao();
        this.sesion = sesion;
        this.context = appContext;
    }

    /** Entrega un resultado de éxito en el hilo de UI. */
    private <T> void exito(Callback<T> callback, T dato) {
        mainHandler.post(() -> callback.onExito(dato));
    }

    /** Entrega un mensaje de error en el hilo de UI. */
    private <T> void error(Callback<T> callback, String mensaje) {
        mainHandler.post(() -> callback.onError(mensaje));
    }

    // --- EFECTOS DE DISPOSITIVO (RF-13) ---

    /**
     * Programa el recordatorio de cita si hay contexto de dispositivo (RF-13).
     * En modo prueba (context == null) es no-op, preservando los tests.
     * Convención UD-02: hora de cita por defecto "08:00" (modelo sin hora real).
     *
     * @param numOrden      número de orden.
     * @param fechaServicio fecha del servicio en ISO 8601 (yyyy-MM-dd).
     */
    private void programarRecordatorioSiAplica(long numOrden, String fechaServicio) {
        if (context != null && fechaServicio != null && !fechaServicio.isEmpty()) {
            RecordatorioScheduler.programarRecordatorio(context, numOrden, fechaServicio, "08:00");
        }
    }

    /** Cancela el recordatorio de una orden (al completar o dar de baja, RF-13). */
    private void cancelarRecordatorioSiAplica(long numOrden) {
        if (context != null) {
            RecordatorioScheduler.cancelarRecordatorio(context, numOrden);
        }
    }

    // --- CREATE (C del CRUD) ---

    /**
     * Crea una orden nueva aplicando RN-4, RN-1, RN-3, RN-7 y RN-8.
     * El número de orden lo asigna Room (RN-5); el técnico sale de la sesión.
     *
     * @param servicioId    ID del tipo de servicio seleccionado (spnTipoServicio).
     * @param clienteId     ID del cliente seleccionado (spnCliente).
     * @param descripcion   descripción del trabajo (obligatoria).
     * @param fechaServicio fecha del servicio en ISO 8601 (yyyy-MM-dd).
     * @param callback      recibe el num_orden asignado o el mensaje de error.
     */
    public void crearOrden(long servicioId, long clienteId, String descripcion,
                           String fechaServicio, Callback<Long> callback) {
        executor.execute(() -> {
            long tecnicoId = sesion.obtenerTecnicoId();               // RN-4
            if (tecnicoId <= 0L) {
                error(callback, "No hay sesión activa: no se puede asignar técnico (RN-4).");
                return;
            }
            if (servicioId <= 0L || clienteId <= 0L
                    || descripcion == null || descripcion.trim().isEmpty()
                    || fechaServicio == null || fechaServicio.isEmpty()) {
                error(callback, "Complete los campos obligatorios: servicio, cliente, descripción y fecha.");
                return;
            }
            Orden orden = new Orden();
            orden.setNumOrden(0L);                                    // Room autogenera
            orden.setTecnicoId(tecnicoId);                            // RN-4
            orden.setServicioId(servicioId);
            orden.setClienteId(clienteId);
            orden.setDescripcion(descripcion.trim());
            orden.setFechaServicio(fechaServicio);
            orden.setEstado("PENDIENTE");                             // RN-1
            orden.setFechaCreacion(FormatoFecha.ahora());             // RN-8
            orden.setFechaCierre(null);                               // RN-3
            orden.setSincronizado(false);                             // RN-7
            orden.setActivo(true);
            long id = ordenDao.insert(orden);
            programarRecordatorioSiAplica(id, fechaServicio);         // RF-13
            exito(callback, id);
        });
    }

    // --- READ (R del CRUD) ---

    /**
     * Lista todas las órdenes activas ordenadas por fecha próxima (RF-05).
     *
     * @param callback recibe la lista de órdenes activas.
     */
    public void listarActivas(Callback<List<Orden>> callback) {
        executor.execute(() -> exito(callback, ordenDao.listarActivas()));
    }

    /**
     * Busca órdenes activas por número, total o parcial (RF-06).
     * El repositorio construye el patrón LIKE con comodines; la UI solo pasa texto.
     *
     * @param texto    texto digitado en etOrderNumber para buscar.
     * @param callback recibe la lista de coincidencias.
     */
    public void buscarPorNumero(String texto, Callback<List<Orden>> callback) {
        executor.execute(() -> {
            String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
            exito(callback, ordenDao.buscarPorNumero(patron));
        });
    }

    /**
     * Obtiene una orden por su número, para precargar el formulario en edición (RF-07).
     *
     * @param numOrden número de orden buscado.
     * @param callback recibe la orden o un error si no existe.
     */
    public void obtenerPorId(long numOrden, Callback<Orden> callback) {
        executor.execute(() -> {
            Orden orden = ordenDao.obtenerPorId(numOrden);
            if (orden == null) {
                error(callback, "La orden no existe.");
            } else {
                exito(callback, orden);
            }
        });
    }

    /**
     * Lista las órdenes pendientes de sincronización (cola de trabajo de RN-7).
     * La usará el módulo de sincronización de la Fase 5.
     *
     * @param callback recibe la lista de órdenes con sincronizado = 0.
     */
    public void listarPendientesSync(Callback<List<Orden>> callback) {
        executor.execute(() -> exito(callback, ordenDao.listarPendientesSync()));
    }

    /**
     * Listado principal en formato de resumen para la UI (RF-05).
     *
     * @param callback recibe la lista de resúmenes ya con nombres resueltos.
     */
    public void listarResumen(Callback<List<OrdenResumen>> callback) {
        executor.execute(() -> exito(callback, ordenDao.listarResumenActivas()));
    }

    /**
     * Búsqueda por número en formato de resumen para la UI (RF-06).
     *
     * @param texto    texto digitado por el técnico.
     * @param callback recibe las coincidencias en formato de resumen.
     */
    public void buscarResumen(String texto, Callback<List<OrdenResumen>> callback) {
        executor.execute(() -> {
            String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
            exito(callback, ordenDao.buscarResumenPorNumero(patron));
        });
    }

    /**
     * Cuenta las evidencias activas de una orden para mostrarlo en la UI
     * (tvEvidenceCount) y como insumo visible de la RN-2.
     *
     * @param numOrden número de la orden consultada.
     * @param callback recibe el conteo (0 o más).
     */
    public void contarEvidenciasActivas(long numOrden, Callback<Integer> callback) {
        executor.execute(() -> exito(callback, evidenciaDao.contarActivasPorOrden(numOrden)));
    }

    // --- UPDATE (U del CRUD) ---

    /**
     * Actualiza una orden existente (RF-07). Deja sincronizado = 0 para que el
     * cambio entre a la cola de sync (RN-7). Re-programa el recordatorio porque
     * la fecha_servicio pudo cambiar (RF-13).
     *
     * @param orden    orden con los datos modificados (su numOrden define la fila).
     * @param callback recibe las filas afectadas o el mensaje de error.
     */
    public void editarOrden(Orden orden, Callback<Integer> callback) {
        executor.execute(() -> {
            if (orden == null || orden.getNumOrden() <= 0L) {
                error(callback, "Orden inválida para editar.");
                return;
            }
            orden.setSincronizado(false);                             // RN-7
            programarRecordatorioSiAplica(orden.getNumOrden(), orden.getFechaServicio()); // RF-13
            exito(callback, ordenDao.update(orden));
        });
    }

    /**
     * Cambia el estado de una orden aplicando RN-1, RN-2 y RN-3.
     * Es el método más "negocio" del proyecto:
     *  - Rechaza estados fuera del dominio (RN-1).
     *  - Bloquea el cierre sin evidencias activas (RN-2).
     *  - Graba fecha_cierre al completar y la limpia al reabrir (RN-3).
     *  - Cancela/re-programa el recordatorio según el nuevo estado (RF-13).
     *
     * @param numOrden    número de la orden a modificar.
     * @param nuevoEstado "PENDIENTE" o "COMPLETADA".
     * @param callback    recibe filas afectadas o el mensaje de rechazo.
     */
    public void cambiarEstado(long numOrden, String nuevoEstado, Callback<Integer> callback) {
        executor.execute(() -> {
            if (!"PENDIENTE".equals(nuevoEstado) && !"COMPLETADA".equals(nuevoEstado)) {
                error(callback, "Estado no válido (RN-1): solo PENDIENTE o COMPLETADA.");
                return;
            }
            Orden orden = ordenDao.obtenerPorId(numOrden);
            if (orden == null) {
                error(callback, "La orden no existe.");
                return;
            }
            if ("COMPLETADA".equals(nuevoEstado)) {
                int evidencias = evidenciaDao.contarActivasPorOrden(numOrden);   // RN-2
                if (evidencias == 0) {
                    error(callback,
                            "No se puede completar: capture al menos una evidencia fotográfica (RN-2).");
                    return;
                }
                orden.setFechaCierre(FormatoFecha.ahora());           // RN-3
                cancelarRecordatorioSiAplica(numOrden);               // RF-13: ya no es cita futura
            } else {
                orden.setFechaCierre(null);                           // RN-3
                programarRecordatorioSiAplica(numOrden, orden.getFechaServicio()); // RF-13: reabierta
            }
            orden.setEstado(nuevoEstado);                             // RN-1
            orden.setSincronizado(false);                             // RN-7
            exito(callback, ordenDao.update(orden));
        });
    }

    /**
     * Marca una orden como sincronizada tras el push exitoso a la API (RN-7).
     * Sin callback: el módulo de sync la invoca y no requiere respuesta en UI.
     *
     * @param numOrden número de la orden sincronizada.
     */
    public void marcarSincronizada(long numOrden) {
        executor.execute(() -> ordenDao.marcarSincronizada(numOrden));
    }

    // --- DELETE (D del CRUD, borrado lógico RN-6) ---

    /**
     * Da de baja una orden y, en cascada lógica, sus evidencias activas (RF-08).
     * Ningún registro se destruye: todo permanece auditable. Cancela el
     * recordatorio asociado (RF-13).
     *
     * @param numOrden número de la orden a dar de baja.
     * @param callback recibe las filas afectadas (1 si existía).
     */
    public void eliminarOrden(long numOrden, Callback<Integer> callback) {
        executor.execute(() -> {
            Orden orden = ordenDao.obtenerPorId(numOrden);
            int filas = ordenDao.eliminarLogico(numOrden);            // RN-6
            evidenciaDao.eliminarLogicasPorOrden(numOrden);           // cascada lógica
            cancelarRecordatorioSiAplica(numOrden);                   // RF-13
            eliminarEnServidorSiAplica(orden);                        // DELETE remoto si ya estaba sincronizada
            exito(callback, filas);
        });
    }

    /**
     * Si la orden ya había cruzado la frontera (tiene num_orden_remoto), avisa
     * al servidor que también la dé de baja. Dispara y olvida: la baja local
     * ya quedó confirmada arriba; esto solo mantiene al servidor al día.
     */
    private void eliminarEnServidorSiAplica(Orden orden) {
        if (orden == null || orden.getNumOrdenRemoto() == null) {
            return;
        }
        try {
            ApiService api = RetrofitClient.getInstance().getApiService();
            api.eliminarOrdenRemota(orden.getNumOrdenRemoto()).execute();
        } catch (Exception e) {
            // Sin red o servidor dormido: la baja local ya es la verdad para
            // el técnico; el servidor queda desactualizado hasta un intento futuro.
        }
    }
}