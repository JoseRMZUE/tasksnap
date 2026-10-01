package ue.edu.co.tasksnap.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entidad que representa la tabla "evidencias" en la base de datos local (Room).
 *
 * Esta tabla almacena los metadatos de las fotografías capturadas por los técnicos
 * como evidencia del cumplimiento de una orden de trabajo. La imagen en sí se guarda
 * en el sistema de archivos del dispositivo (requisito "Archivos" del curso).
 *
 * Rol en la arquitectura:
 *  - Fuente de verdad: Local (Room + archivos), CRUD completo offline.
 *  - Rol remoto: sincronización push hacia la API Spring Boot + PostgreSQL cuando hay conectividad.
 *
 * Reglas de negocio aplicadas:
 *  - RN-2: una orden COMPLETADA debe tener al menos una evidencia activa (se valida en el repositorio).
 *  - RN-6: borrado lógico mediante el atributo "activo".
 *  - RN-7: sincronizado = 0 marca pendientes de envío a la API.
 *  - RN-8: fecha_captura almacenada como TEXT en formato ISO 8601 (yyyy-MM-dd'T'HH:mm:ss).
 *
 * Relaciones:
 *  - FK hacia ordenes (orden_id), con ON DELETE CASCADE.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Entity(
        tableName = "evidencias",
        foreignKeys = {
                @ForeignKey(entity = Orden.class, parentColumns = "num_orden", childColumns = "orden_id", onDelete = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = "orden_id")
        }
)
public class Evidencia {

    // --- ATRIBUTOS (mapeados 1:1 con la tabla "evidencias" del modelo E-R v4.0) ---

    /**
     * Clave primaria autogenerada de la tabla.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "evidencia_id")
    private long evidenciaId;

    /**
     * FK hacia ordenes. Cada evidencia pertenece a exactamente una orden.
     */
    @ColumnInfo(name = "orden_id")
    private long ordenId;

    /**
     * Ruta local del archivo de imagen en el dispositivo.
     * Se almacena en almacenamiento interno (getFilesDir()) para mayor seguridad.
     */
    @NonNull
    @ColumnInfo(name = "ruta_local")
    private String rutaLocal = "";

    /**
     * Nombre del archivo de imagen (ej: "evidencia_20260921_143022.jpg").
     */
    @NonNull
    @ColumnInfo(name = "nombre_archivo")
    private String nombreArchivo = "";

    /**
     * Fecha y hora de captura de la foto, en formato ISO 8601 (yyyy-MM-dd'T'HH:mm:ss).
     * Obligatorio (RN-8).
     */
    @NonNull
    @ColumnInfo(name = "fecha_captura")
    private String fechaCaptura = "";

    /**
     * Indicador de sincronización con la API (RN-7).
     * 0 = pendiente de envío, 1 = sincronizada.
     */
    @ColumnInfo(name = "sincronizado", defaultValue = "0")
    private boolean sincronizado = false;

    /**
     * Indicador de borrado lógico (RN-6).
     * true (1) = activo, false (0) = dado de baja (no se muestra en listados).
     */
    @ColumnInfo(name = "activo", defaultValue = "1")
    private boolean activo = true;

    // --- CONSTRUCTORES ---

    /**
     * Constructor vacío, obligatorio para Room.
     * Room lo utiliza mediante reflexión al leer filas de la tabla y convertirlos en objetos.
     */
    public Evidencia() {
    }

    /**
     * Constructor con parámetros para objetos ya inicializados.
     *
     * @param evidenciaId   identificador único; pase 0L para registros locales nuevos
     *                      (Room lo autogenera al insertar), o el ID real para
     *                      actualizaciones y para la caché sincronizada desde la API.
     * @param ordenId       ID de la orden a la que pertenece esta evidencia (FK).
     * @param rutaLocal     ruta del archivo de imagen en el dispositivo (no nulo).
     * @param nombreArchivo nombre del archivo (no nulo).
     * @param fechaCaptura  fecha de captura en formato ISO 8601 (no nulo).
     * @param sincronizado  indicador de sincronización (0 = pendiente, 1 = sincronizada).
     * @param activo        estado lógico del registro.
     */
    @Ignore
    public Evidencia(long evidenciaId, long ordenId, @NonNull String rutaLocal,
                     @NonNull String nombreArchivo, @NonNull String fechaCaptura,
                     boolean sincronizado, boolean activo) {
        this.evidenciaId = evidenciaId;
        this.ordenId = ordenId;
        this.rutaLocal = rutaLocal;
        this.nombreArchivo = nombreArchivo;
        this.fechaCaptura = fechaCaptura;
        this.sincronizado = sincronizado;
        this.activo = activo;
    }

    // --- GETTERS Y SETTERS ---

    /** @return el identificador único de la evidencia. */
    public long getEvidenciaId() {
        return evidenciaId;
    }

    /** @param evidenciaId identificador a asignar. */
    public void setEvidenciaId(long evidenciaId) {
        this.evidenciaId = evidenciaId;
    }

    /** @return el ID de la orden a la que pertenece esta evidencia. */
    public long getOrdenId() {
        return ordenId;
    }

    /** @param ordenId ID de la orden a asignar. */
    public void setOrdenId(long ordenId) {
        this.ordenId = ordenId;
    }

    /** @return la ruta local del archivo de imagen (nunca nulo). */
    @NonNull
    public String getRutaLocal() {
        return rutaLocal;
    }

    /** @param rutaLocal ruta a asignar (no nulo). */
    public void setRutaLocal(@NonNull String rutaLocal) {
        this.rutaLocal = rutaLocal;
    }

    /** @return el nombre del archivo de imagen (nunca nulo). */
    @NonNull
    public String getNombreArchivo() {
        return nombreArchivo;
    }

    /** @param nombreArchivo nombre a asignar (no nulo). */
    public void setNombreArchivo(@NonNull String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    /** @return la fecha de captura en formato ISO 8601 (nunca nulo). */
    @NonNull
    public String getFechaCaptura() {
        return fechaCaptura;
    }

    /** @param fechaCaptura fecha de captura a asignar (no nulo). */
    public void setFechaCaptura(@NonNull String fechaCaptura) {
        this.fechaCaptura = fechaCaptura;
    }

    /** @return true si la evidencia está sincronizada, false si está pendiente. */
    public boolean isSincronizado() {
        return sincronizado;
    }

    /** @param sincronizado indicador de sincronización a asignar. */
    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }

    /** @return true si la evidencia está activa, false si fue dada de baja. */
    public boolean isActivo() {
        return activo;
    }

    /** @param activo estado lógico a asignar. */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}