package ue.edu.co.tasksnap.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entidad que representa la tabla "ordenes" en la base de datos local (Room).
 *
 * Esta tabla almacena las órdenes de trabajo que los técnicos ejecutan en campo.
 * Es la entidad central del sistema: cada orden está ligada a un técnico, un cliente
 * y un tipo de servicio, y puede tener múltiples evidencias fotográficas asociadas.
 *
 * Rol en la arquitectura:
 *  - Fuente de verdad: Local (Room/SQLite), CRUD completo offline (RF-17, RFN-10).
 *  - Rol remoto: sincronización push hacia la API Spring Boot + PostgreSQL cuando hay conectividad.
 *
 * Reglas de negocio aplicadas:
 *  - RN-1: estado solo admite PENDIENTE o COMPLETADA; toda orden nace PENDIENTE.
 *  - RN-2: una orden no puede quedar COMPLETADA sin al menos una evidencia activa (se valida en el repositorio).
 *  - RN-3: fecha_cierre es NULL mientras la orden no esté COMPLETADA; obligatoria al completarse.
 *  - RN-4: tecnico_id se toma de la sesión activa, nunca de la UI (se valida en el repositorio).
 *  - RN-5: num_orden no es editable por el usuario (UI en solo lectura).
 *  - RN-6: borrado lógico mediante el atributo "activo".
 *  - RN-7: sincronizado = 0 marca pendientes de envío a la API.
 *  - RN-8: fechas almacenadas como TEXT en formato ISO 8601 (yyyy-MM-dd).
 *
 * Relaciones:
 *  - FK hacia tecnicos (tecnico_id)
 *  - FK hacia servicios (servicio_id)
 *  - FK hacia clientes (cliente_id)
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Entity(
        tableName = "ordenes",
        foreignKeys = {
                @ForeignKey(entity = Tecnico.class, parentColumns = "tecnico_id", childColumns = "tecnico_id", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = Servicio.class, parentColumns = "servicio_id", childColumns = "servicio_id", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = Cliente.class, parentColumns = "cliente_id", childColumns = "cliente_id", onDelete = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = "tecnico_id"),
                @Index(value = "servicio_id"),
                @Index(value = "cliente_id"),
                @Index(value = "estado")
        }
)
public class Orden {

    // --- ATRIBUTOS (mapeados 1:1 con la tabla "ordenes" del modelo E-R v4.0) ---

    /**
     * Número de orden autogenerado (PK). No es editable por el usuario (RN-5).
     * Se muestra en la UI en formato "YYYY###" (ej: 2026001) como presentación calculada.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "num_orden")
    private long numOrden;

    /**
     * FK hacia tecnicos. Se toma automáticamente de la sesión activa (RN-4).
     */
    @ColumnInfo(name = "tecnico_id")
    private long tecnicoId;

    /**
     * FK hacia servicios. Define el tipo de trabajo a realizar (plomería, electricidad, etc.).
     */
    @ColumnInfo(name = "servicio_id")
    private long servicioId;

    /**
     * FK hacia clientes. Indica a quién se atiende y dónde (la dirección está en clientes).
     */
    @ColumnInfo(name = "cliente_id")
    private long clienteId;

    /**
     * Descripción del trabajo a realizar. Obligatorio.
     */
    @NonNull
    @ColumnInfo(name = "descripcion")
    private String descripcion = "";

    /**
     * Fecha programada para ejecutar el servicio, en formato ISO 8601 (yyyy-MM-dd).
     * Obligatorio (RN-8).
     */
    @NonNull
    @ColumnInfo(name = "fecha_servicio")
    private String fechaServicio = "";

    /**
     * Estado de la orden. Solo admite PENDIENTE o COMPLETADA (RN-1).
     * Toda orden nueva nace PENDIENTE por defecto.
     */
    @NonNull
    @ColumnInfo(name = "estado", defaultValue = "PENDIENTE")
    private String estado = "PENDIENTE";

    /**
     * Fecha y hora de creación del registro, en formato ISO 8601 (yyyy-MM-dd'T'HH:mm:ss).
     * Se asigna automáticamente al insertar (RN-8).
     */
    @NonNull
    @ColumnInfo(name = "fecha_creacion")
    private String fechaCreacion = "";

    /**
     * Fecha y hora de cierre de la orden. NULL mientras el estado no sea COMPLETADA (RN-3).
     * Obligatorio al pasar a COMPLETADA; se limpia si la orden vuelve a PENDIENTE.
     */
    @Nullable
    @ColumnInfo(name = "fecha_cierre")
    private String fechaCierre;

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

    /**
     * ID asignado por el backend tras un push exitoso (RN-7, Fase 5).
     *
     * Null mientras la orden nunca haya sido sincronizada (= estado "cola de salida").
     * Tras un POST /api/ordenes exitoso, el SyncManager escribe aqui el numOrden
     * real que asigno el servidor, coexistiendo con la PK local sin pisarla
     * (mitigacion del Riesgo 3 del Acta v5: mundos locales y remotos separados).
     *
     * Tipo: Long (envoltorio, no primitivo) para que null sea un estado valido.
     */
    @Nullable
    @ColumnInfo(name = "num_orden_remoto")
    private Long numOrdenRemoto;

    // --- CONSTRUCTORES ---

    /**
     * Constructor vacío, obligatorio para Room.
     * Room lo utiliza mediante reflexión al leer filas de la tabla y convertirlos en objetos.
     */
    public Orden() {
    }

    /**
     * Constructor con parámetros para objetos ya inicializados.
     *
     * @param numOrden        número de orden; pase 0L para registros locales nuevos
     *                        (Room lo autogenera al insertar), o el número real para
     *                        actualizaciones y para la caché sincronizada desde la API.
     * @param tecnicoId       ID del técnico que ejecuta la orden (FK).
     * @param servicioId      ID del tipo de servicio (FK).
     * @param clienteId       ID del cliente atendido (FK).
     * @param descripcion     descripción del trabajo (no nulo).
     * @param fechaServicio   fecha del servicio en formato ISO 8601 (no nulo).
     * @param estado          estado de la orden (PENDIENTE o COMPLETADA).
     * @param fechaCreacion   fecha de creación en formato ISO 8601 (no nulo).
     * @param fechaCierre     fecha de cierre (nullable, solo si estado = COMPLETADA).
     * @param sincronizado    indicador de sincronización (0 = pendiente, 1 = sincronizada).
     * @param activo          estado lógico del registro.
     * @param numOrdenRemoto  ID asignado por el servidor tras push exitoso (nullable, Fase 5).
     */
    @Ignore
    public Orden(long numOrden, long tecnicoId, long servicioId, long clienteId,
                 @NonNull String descripcion, @NonNull String fechaServicio,
                 @NonNull String estado, @NonNull String fechaCreacion,
                 @Nullable String fechaCierre, boolean sincronizado, boolean activo,
                 @Nullable Long numOrdenRemoto) {
        this.numOrden = numOrden;
        this.tecnicoId = tecnicoId;
        this.servicioId = servicioId;
        this.clienteId = clienteId;
        this.descripcion = descripcion;
        this.fechaServicio = fechaServicio;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.fechaCierre = fechaCierre;
        this.sincronizado = sincronizado;
        this.activo = activo;
        this.numOrdenRemoto = numOrdenRemoto;
    }

    // --- GETTERS Y SETTERS ---

    /** @return el número de orden (PK). */
    public long getNumOrden() {
        return numOrden;
    }

    /** @param numOrden número de orden a asignar. */
    public void setNumOrden(long numOrden) {
        this.numOrden = numOrden;
    }

    /** @return el ID del técnico que ejecuta la orden. */
    public long getTecnicoId() {
        return tecnicoId;
    }

    /** @param tecnicoId ID del técnico a asignar. */
    public void setTecnicoId(long tecnicoId) {
        this.tecnicoId = tecnicoId;
    }

    /** @return el ID del tipo de servicio. */
    public long getServicioId() {
        return servicioId;
    }

    /** @param servicioId ID del servicio a asignar. */
    public void setServicioId(long servicioId) {
        this.servicioId = servicioId;
    }

    /** @return el ID del cliente atendido. */
    public long getClienteId() {
        return clienteId;
    }

    /** @param clienteId ID del cliente a asignar. */
    public void setClienteId(long clienteId) {
        this.clienteId = clienteId;
    }

    /** @return la descripción del trabajo (nunca nulo). */
    @NonNull
    public String getDescripcion() {
        return descripcion;
    }

    /** @param descripcion descripción a asignar (no nulo). */
    public void setDescripcion(@NonNull String descripcion) {
        this.descripcion = descripcion;
    }

    /** @return la fecha del servicio en formato ISO 8601 (nunca nulo). */
    @NonNull
    public String getFechaServicio() {
        return fechaServicio;
    }

    /** @param fechaServicio fecha del servicio a asignar (no nulo). */
    public void setFechaServicio(@NonNull String fechaServicio) {
        this.fechaServicio = fechaServicio;
    }

    /** @return el estado de la orden (PENDIENTE o COMPLETADA). */
    @NonNull
    public String getEstado() {
        return estado;
    }

    /** @param estado estado a asignar (PENDIENTE o COMPLETADA). */
    public void setEstado(@NonNull String estado) {
        this.estado = estado;
    }

    /** @return la fecha de creación en formato ISO 8601 (nunca nulo). */
    @NonNull
    public String getFechaCreacion() {
        return fechaCreacion;
    }

    /** @param fechaCreacion fecha de creación a asignar (no nulo). */
    public void setFechaCreacion(@NonNull String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** @return la fecha de cierre (nullable, solo si estado = COMPLETADA). */
    @Nullable
    public String getFechaCierre() {
        return fechaCierre;
    }

    /** @param fechaCierre fecha de cierre a asignar (nullable). */
    public void setFechaCierre(@Nullable String fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    /** @return true si la orden está sincronizada, false si está pendiente. */
    public boolean isSincronizado() {
        return sincronizado;
    }

    /** @param sincronizado indicador de sincronización a asignar. */
    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }

    /** @return true si la orden está activa, false si fue dada de baja. */
    public boolean isActivo() {
        return activo;
    }

    /** @param activo estado lógico a asignar. */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * @return el ID asignado por el servidor tras un push exitoso,
     *         o null si la orden nunca ha sido sincronizada (RN-7, Fase 5).
     */
    @Nullable
    public Long getNumOrdenRemoto() {
        return numOrdenRemoto;
    }

    /**
     * @param numOrdenRemoto ID asignado por el servidor tras un push exitoso
     *                       (nullable; null = aun no sincronizada).
     */
    public void setNumOrdenRemoto(@Nullable Long numOrdenRemoto) {
        this.numOrdenRemoto = numOrdenRemoto;
    }
}