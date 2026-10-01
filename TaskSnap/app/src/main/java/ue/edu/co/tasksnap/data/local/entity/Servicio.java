package ue.edu.co.tasksnap.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entidad que representa la tabla "servicios" en la base de datos local (Room).
 *
 * Esta tabla almacena los tipos de servicio que ofrece ServiPro S.A.S.
 * (por ejemplo: "Plomería", "Electricidad", "Mantenimiento general").
 * El técnico en campo selecciona un tipo de servicio al crear una orden
 * desde el spinner (spnTipoServicio) del formulario principal.
 *
 * Rol en la arquitectura:
 *  - Fuente de verdad: API Spring Boot + PostgreSQL (CRUD completo remoto, RF-19).
 *  - Rol local: caché de solo lectura para alimentar el spnTipoServicio y permitir
 *    crear órdenes sin conexión a internet (RF-17, RFN-10).
 *
 * Reglas de negocio aplicadas:
 *  - El nombre del servicio debe ser único (índice único declarado en @Entity),
 *    porque es un catálogo y no pueden existir dos tipos de servicio con el mismo nombre.
 *  - RN-6: borrado lógico mediante el atributo "activo" (1 = activo, 0 = dado de baja).
 *
 * Convenciones del proyecto:
 *  - RN-Técnica-01: al crear un servicio nuevo desde la UI, pase 0L como servicioId
 *    en el constructor parametrizado; Room lo autogenera al insertar.
 *  - RFN-13: el esquema local es equivalente 1:1 con el de PostgreSQL.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Entity(tableName = "servicios", indices = {@Index(value = "nombre", unique = true)})
public class Servicio {

    // --- ATRIBUTOS (mapeados 1:1 con la tabla "servicios" del modelo E-R v4.1) ---

    /**
     * Clave primaria autogenerada de la tabla.
     * Equivalente al SERIAL de PostgreSQL.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "servicio_id")
    private long servicioId;

    /**
     * Nombre único del tipo de servicio (plomería, electricidad, etc.).
     * El índice único declarado en @Entity impide duplicados a nivel de base de datos.
     */
    @NonNull
    @ColumnInfo(name = "nombre")
    private String nombre = "";

    /**
     * Indicador de borrado lógico (RN-6).
     * true (1) = activo (visible en el spnTipoServicio), false (0) = dado de baja.
     */
    @ColumnInfo(name = "activo", defaultValue = "1")
    private boolean activo = true;

    // --- CONSTRUCTORES ---

    /**
     * Constructor vacío, obligatorio para Room.
     * Room lo utiliza mediante reflexión al leer filas de la tabla y convertirlos en objetos.
     */
    public Servicio() {
        // Constructor vacío: Room lo usa para leer filas de la tabla
    }

    /**
     * Constructor con parámetros para objetos ya inicializados.
     *
     * @param servicioId identificador único; pase 0L para registros locales nuevos
     *                   (Room lo autogenera al insertar), o el ID real para
     *                   actualizaciones y para la caché sincronizada desde la API.
     * @param nombre     nombre del tipo de servicio (no nulo, único en la tabla).
     * @param activo     estado lógico del registro.
     */
    @Ignore
    public Servicio(long servicioId, @NonNull String nombre, boolean activo) {
        this.servicioId = servicioId;
        this.nombre = nombre;
        this.activo = activo;
    }

    // --- GETTERS Y SETTERS ---

    /** @return el identificador único del servicio. */
    public long getServicioId() {
        return servicioId;
    }

    /** @param servicioId identificador a asignar. */
    public void setServicioId(long servicioId) {
        this.servicioId = servicioId;
    }

    /** @return el nombre del tipo de servicio (nunca nulo). */
    @NonNull
    public String getNombre() {
        return nombre;
    }

    /** @param nombre nombre a asignar (no nulo). */
    public void setNombre(@NonNull String nombre) {
        this.nombre = nombre;
    }

    /** @return true si el servicio está activo, false si fue dado de baja. */
    public boolean isActivo() {
        return activo;
    }

    /** @param activo estado lógico a asignar. */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }


    @Override
    public String toString() {
        return nombre;
    }
}