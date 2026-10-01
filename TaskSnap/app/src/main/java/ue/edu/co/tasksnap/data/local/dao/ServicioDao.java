package ue.edu.co.tasksnap.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Servicio;

/**
 * Data Access Object (DAO) de la tabla "servicios".
 *
 * Un DAO es la única "ventanilla" por la que el resto de la aplicación accede a la
 * tabla: el repositorio llama a estos métodos y Room genera automáticamente la
 * implementación SQL real al compilar (clase generada: ServicioDao_Impl).
 * Ningún Activity debe llamar este DAO directamente (RFN-11).
 *
 * Rol en la arquitectura:
 *  - Servicios es una entidad API-first: su fuente de verdad es Spring Boot + PostgreSQL.
 *  - Este DAO mantiene la caché local que alimenta el spnTipoServicio y permite
 *    crear órdenes sin conexión (RF-17, RFN-10).
 *  - Las escrituras las orchesta el ServicioRepository: primero golpea la API con
 *    Retrofit y, si tiene éxito, refleja el cambio en esta caché.
 *
 * CRUD completo (4 operaciones) según la matriz de cumplimiento del curso:
 *  - CREATE: {@link #insert(Servicio)} y {@link #insertAll(List)}
 *  - READ:   {@link #listarActivos()} y {@link #obtenerPorId(long)}
 *  - UPDATE: {@link #update(Servicio)}
 *  - DELETE: {@link #eliminarLogico(long)} (borrado lógico, RN-6)
 *  - Mantenimiento de caché: {@link #vaciarCache()}
 *
 * Nota de hilos: todos estos métodos son síncronos; deben invocarse desde un hilo
 * secundario (el repositorio usará ExecutorService), nunca desde el hilo de UI.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Dao
public interface ServicioDao {

    // --- CREATE (C del CRUD) ---

    /**
     * Inserta un servicio en la caché local.
     * Si ya existe un registro con la misma PK o el mismo nombre (índice único),
     * la estrategia REPLACE borra la fila vieja e inserta la nueva, lo que permite
     * refrescar la caché desde la API sin excepciones de conflicto.
     *
     * @param servicio servicio a insertar; con servicioId = 0L Room autogenera el ID
     *                 (RN-Técnica-01), con ID real se reemplaza la fila existente.
     * @return el ID (rowId) asignado o reemplazado por SQLite.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Servicio servicio);

    /**
     * Inserta varios servicios en una sola operación (inserción por lotes).
     * Se usa al refrescar la caché completa desde GET /api/servicios.
     *
     * @param servicios lista de servicios recibida desde la API.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Servicio> servicios);

    // --- READ (R del CRUD) ---

    /**
     * Devuelve todos los servicios activos ordenados alfabéticamente.
     * Aplica RN-6: los registros con activo = 0 (dados de baja) nunca se listan.
     * Alimenta el spnTipoServicio del formulario de órdenes.
     *
     * @return lista de servicios activos; vacía si no hay ninguno.
     */
    @Query("SELECT * FROM servicios WHERE activo = 1 ORDER BY nombre ASC")
    List<Servicio> listarActivos();

    /**
     * Devuelve un único servicio a partir de su clave primaria.
     *
     * @param id clave primaria (servicio_id) del servicio buscado.
     * @return el servicio encontrado, o null si no existe.
     */
    @Query("SELECT * FROM servicios WHERE servicio_id = :id LIMIT 1")
    Servicio obtenerPorId(long id);

    // --- UPDATE (U del CRUD) ---

    /**
     * Actualiza la fila cuya clave primaria coincida con la del objeto recibido.
     *
     * @param servicio servicio con los datos nuevos (su servicioId define la fila).
     * @return número de filas afectadas; 0 significa que el registro ya no existía.
     */
    @Update
    int update(Servicio servicio);

    // --- DELETE (D del CRUD, borrado lógico RN-6) ---

    /**
     * Da de baja un servicio marcándolo con activo = 0 (borrado lógico).
     * El registro permanece auditable y no rompe FKs de órdenes históricas.
     * En términos de negocio, esta es la operación DELETE del CRUD.
     *
     * @param id clave primaria del servicio a dar de baja.
     * @return número de filas afectadas (1 si existía, 0 si no).
     */
    @Query("UPDATE servicios SET activo = 0 WHERE servicio_id = :id")
    int eliminarLogico(long id);

    // --- MANTENIMIENTO DE CACHÉ (exclusivo de entidades API-first) ---

    /**
     * Vacía por completo la caché local de servicios (borrado físico).
     * Es seguro porque esta tabla NO es fuente de verdad: su contenido se
     * reconstruye desde la API. Se usa antes de un refrescado total
     * (vaciarCache + insertAll) cuando se detectan inconsistencias.
     */
    @Query("DELETE FROM servicios")
    void vaciarCache();
}