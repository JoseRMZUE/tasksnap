package ue.edu.co.tasksnap.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ue.edu.co.tasksnap.data.local.dao.EvidenciaDao;
import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.entity.Evidencia;
import ue.edu.co.tasksnap.util.ArchivoUtil;
import ue.edu.co.tasksnap.util.FormatoFecha;

/**
 * Repositorio de evidencias fotográficas: ÚNICA clase autorizada a operar la
 * tabla "evidencias" y a gestionar los archivos de imagen en disco (RFN-11).
 *
 * Patrón de hilos: ExecutorService de un solo hilo para todas las operaciones
 * de BD y disco; Handler de regreso al hilo de UI para los callbacks.
 *
 * Reglas de negocio que viven aquí:
 *  - RN-2: contarActivasPorOrden es la consulta que OrdenRepository usa para
 *    validar que una orden COMPLETADA tenga al menos una evidencia activa.
 *  - RN-6: el borrado es lógico (activo=0); el archivo físico se mantiene
 *    auditable (no se elimina de disco).
 *  - RN-7: toda escritura deja sincronizado = 0 (cola de sync).
 *  - RN-8: fecha_captura se fabrica con FormatoFecha.
 *
 * Este repositorio constituye parte del flujo de órdenes local-first:
 * el técnico captura la foto al cerrar una orden, y todo funciona sin conexión.
 */
public class EvidenciaRepository {

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

    /** Acceso a la tabla evidencias. */
    private final EvidenciaDao evidenciaDao;

    /** Contexto de aplicación para acceder al sistema de archivos privado. */
    private final Context context;

    /** Hilo secundario único y serializado para todas las operaciones de BD y disco. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Puente de regreso al hilo de UI para los callbacks. */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Constructor de PRODUCCIÓN: resuelve el singleton de BD y el contexto de app.
     *
     * @param context cualquier contexto; se usa getApplicationContext() para no
     *                retener Activities en memoria.
     */
    public EvidenciaRepository(Context context) {
        this(AppDatabase.getInstance(context), context.getApplicationContext());
    }

    /**
     * Constructor INYECTABLE (CS-02): BD concreta + contexto de aplicación para
     * disco privado. Permite pruebas instrumentadas con Room in-memory sin tocar
     * el singleton estático.
     *
     * @param db         base de datos a usar (real o in-memory).
     * @param appContext contexto de aplicación para getFilesDir(). En pruebas de
     *                   disco debe ser el targetContext real; puede ser null solo
     *                   si NO se van a hacer capturas.
     */
    public EvidenciaRepository(AppDatabase db, Context appContext) {
        this.context = appContext;
        this.evidenciaDao = db.evidenciaDao();
    }

    /** Entrega un resultado de éxito en el hilo de UI. */
    private <T> void exito(Callback<T> callback, T dato) {
        mainHandler.post(() -> callback.onExito(dato));
    }

    /** Entrega un mensaje de error en el hilo de UI. */
    private <T> void error(Callback<T> callback, String mensaje) {
        mainHandler.post(() -> callback.onError(mensaje));
    }

    // --- CREATE (C del CRUD) ---

    /**
     * Captura una evidencia fotográfica: guarda el archivo en disco y los
     * metadatos en la base de datos local (RF-12).
     *
     * Flujo:
     *  1. Comprime el Bitmap y lo guarda en almacenamiento interno privado.
     *  2. Crea un registro de evidencia con los metadatos (ruta, nombre, fecha).
     *  3. Inserta el registro en la tabla evidencias.
     *  4. Devuelve el ID asignado.
     *
     * @param ordenId  ID de la orden a la que pertenece esta evidencia (FK).
     * @param bitmap   imagen capturada por la cámara (ya decodificada/reducida).
     * @param callback recibe el evidencia_id asignado o el mensaje de error.
     */
    public void capturarEvidencia(long ordenId, Bitmap bitmap, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                // 1. Generar nombre único y crear archivo
                String nombreArchivo = ArchivoUtil.generarNombreArchivo();
                File archivo = ArchivoUtil.crearArchivoPrivado(context, nombreArchivo);

                // 2. Comprimir y guardar el Bitmap en disco
                ArchivoUtil.comprimirYGuardar(bitmap, archivo);

                // 3. Crear el registro de metadatos
                Evidencia evidencia = new Evidencia();
                evidencia.setOrdenId(ordenId);
                evidencia.setRutaLocal(archivo.getAbsolutePath());
                evidencia.setNombreArchivo(nombreArchivo);
                evidencia.setFechaCaptura(FormatoFecha.ahora());    // RN-8
                evidencia.setSincronizado(false);                  // RN-7
                evidencia.setActivo(true);

                // 4. Insertar en la base de datos
                long id = evidenciaDao.insert(evidencia);
                exito(callback, id);

            } catch (IOException e) {
                error(callback, "Error al guardar la foto: " + e.getMessage());
            }
        });
    }

    // --- READ (R del CRUD) ---

    /**
     * Lista todas las evidencias activas de una orden, ordenadas por fecha de
     * captura (la galería fotográfica de la orden en la UI).
     *
     * @param ordenId  ID de la orden consultada (FK).
     * @param callback recibe la lista de evidencias activas.
     */
    public void listarPorOrden(long ordenId, Callback<List<Evidencia>> callback) {
        executor.execute(() -> exito(callback, evidenciaDao.listarPorOrden(ordenId)));
    }

    /**
     * Obtiene una evidencia por su ID, para ver el detalle de una foto.
     *
     * @param evidenciaId ID de la evidencia buscada.
     * @param callback    recibe la evidencia o error si no existe.
     */
    public void obtenerPorId(long evidenciaId, Callback<Evidencia> callback) {
        executor.execute(() -> {
            Evidencia evidencia = evidenciaDao.obtenerPorId(evidenciaId);
            if (evidencia == null) {
                error(callback, "La evidencia no existe.");
            } else {
                exito(callback, evidencia);
            }
        });
    }

    /**
     * Cuenta cuántas evidencias activas tiene una orden (RN-2).
     * OrdenRepository llama este método antes de permitir el cierre.
     *
     * @param ordenId  ID de la orden consultada.
     * @param callback recibe el conteo (0 o más).
     */
    public void contarActivasPorOrden(long ordenId, Callback<Integer> callback) {
        executor.execute(() -> exito(callback, evidenciaDao.contarActivasPorOrden(ordenId)));
    }

    /**
     * Carga el Bitmap de una evidencia para mostrarlo como miniatura o en detalle.
     *
     * @param evidencia evidencia con la ruta local del archivo.
     * @param callback  recibe el Bitmap cargado o null si el archivo no existe.
     */
    public void cargarImagen(Evidencia evidencia, Callback<Bitmap> callback) {
        executor.execute(() -> {
            File archivo = new File(evidencia.getRutaLocal());
            Bitmap bitmap = ArchivoUtil.cargarBitmap(archivo);
            exito(callback, bitmap);
        });
    }

    // --- DELETE (D del CRUD, borrado lógico RN-6) ---

    /**
     * Da de baja una evidencia marcándola con activo = 0 (borrado lógico).
     * El archivo en disco NO se elimina: permanece auditable (RN-6).
     *
     * @param evidenciaId ID de la evidencia a dar de baja.
     * @param callback    recibe las filas afectadas (1 si existía).
     */
    public void eliminarEvidencia(long evidenciaId, Callback<Integer> callback) {
        executor.execute(() -> {
            int filas = evidenciaDao.eliminarLogico(evidenciaId);
            exito(callback, filas);
        });
    }

    /**
     * Da de baja TODAS las evidencias activas de una orden (cascada lógica).
     * OrdenRepository llama este método al dar de baja una orden.
     *
     * @param ordenId  ID de la orden cuyas evidencias se darán de baja.
     * @param callback recibe las filas afectadas.
     */
    public void eliminarEvidenciasPorOrden(long ordenId, Callback<Integer> callback) {
        executor.execute(() -> {
            int filas = evidenciaDao.eliminarLogicasPorOrden(ordenId);
            exito(callback, filas);
        });
    }
}