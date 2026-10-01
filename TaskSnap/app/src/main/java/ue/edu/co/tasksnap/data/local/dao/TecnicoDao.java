package ue.edu.co.tasksnap.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Tecnico;

/**
 * Data Access Object (DAO) de la tabla "tecnicos".
 *
 * Un DAO es la única "ventanilla" por la que el resto de la aplicación accede a la
 * tabla: el repositorio llama a estos métodos y Room genera automáticamente la
 * implementación SQL real al compilar (clase generada: TecnicoDao_Impl).
 * Ningún Activity debe llamar este DAO directamente (RFN-11).
 *
 * Rol en la arquitectura:
 *  - Técnicos es una entidad API-first: su fuente de verdad es Spring Boot + PostgreSQL
 *    (CRUD completo remoto, RF-20, cuarto CRUD de la matriz de cumplimiento).
 *  - Rol local: caché que habilita el LOGIN OFFLINE (RF-01, RF-20): tras la primera
 *    sincronización, el técnico puede iniciar sesión sin internet porque el hash
 *    de su contraseña ya está en esta tabla.
 *  - Las escrituras las orquesta el TecnicoRepository: primero golpea la API con
 *    Retrofit y, si tiene éxito, refleja el cambio en esta caché.
 *
 * CRUD completo (4 operaciones) según la matriz de cumplimiento del curso:
 *  - CREATE: {@link #insert(Tecnico)} y {@link #insertAll(List)}
 *  - READ:   {@link #listarActivos()}, {@link #obtenerPorId(long)} y
 *            {@link #buscarPorUsuario(String)} (consulta de autenticación)
 *  - UPDATE: {@link #update(Tecnico)}
 *  - DELETE: {@link #eliminarLogico(long)} (borrado lógico, RN-6)
 *  - Mantenimiento de caché: {@link #vaciarCache()}
 *
 * Nota de seguridad (RFN-05): este DAO solo lee y escribe hashes y salts; NUNCA
 * maneja contraseñas en texto plano. La comparación de credenciales se hace en el
 * repositorio: SHA-256(contraseña digitada + salt) contra password_hash almacenado.
 *
 * Nota de hilos: todos estos métodos son síncronos; deben invocarse desde un hilo
 * secundario (el repositorio usará ExecutorService), nunca desde el hilo de UI.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Dao
public interface TecnicoDao {

    // --- CREATE (C del CRUD) ---

    /**
     * Inserta un técnico en la caché local.
     * Si ya existe un registro con la misma PK o el mismo usuario (índice único),
     * la estrategia REPLACE borra la fila vieja e inserta la nueva, lo que permite
     * refrescar la caché desde la API sin excepciones de conflicto.
     *
     * @param tecnico técnico a insertar; con tecnicoId = 0L Room autogenera el ID
     *                (RN-Técnica-01), con ID real se reemplaza la fila existente.
     * @return el ID (rowId) asignado o reemplazado por SQLite.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Tecnico tecnico);

    /**
     * Inserta varios técnicos en una sola operación (inserción por lotes).
     * Se usa al refrescar la caché completa desde GET /api/tecnicos.
     *
     * @param tecnicos lista de técnicos recibida desde la API.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Tecnico> tecnicos);

    // --- READ (R del CRUD) ---

    /**
     * Devuelve todos los técnicos activos ordenados alfabéticamente.
     * Aplica RN-6: los dados de baja nunca se listan.
     * Se usa en la pantalla de gestión de técnicos (CRUD #4 consumido desde la API).
     *
     * @return lista de técnicos activos; vacía si no hay ninguno.
     */
    @Query("SELECT * FROM tecnicos WHERE activo = 1 ORDER BY nombre ASC")
    List<Tecnico> listarActivos();

    /**
     * Devuelve un único técnico a partir de su clave primaria.
     * Se usa para precargar los datos en el diálogo de edición del CRUD de técnicos.
     *
     * @param id clave primaria (tecnico_id) del técnico buscado.
     * @return el técnico encontrado, o null si no existe.
     */
    @Query("SELECT * FROM tecnicos WHERE tecnico_id = :id LIMIT 1")
    Tecnico obtenerPorId(long id);

    /**
     * Consulta de autenticación: busca la cuenta por nombre de usuario,
     * solo si está activa (un técnico dado de baja no puede iniciar sesión, RN-6).
     * Devuelve el registro completo (incluidos password_hash y salt) para que el
     * repositorio verifique la contraseña hasheada sin exponerla en la UI.
     *
     * @param usuario nombre de usuario digitado en el login (etUser).
     * @return la cuenta activa encontrada, o null si el usuario no existe
     *         o está dado de baja.
     */
    @Query("SELECT * FROM tecnicos WHERE usuario = :usuario AND activo = 1 LIMIT 1")
    Tecnico buscarPorUsuario(String usuario);

    // --- UPDATE (U del CRUD) ---

    /**
     * Actualiza la fila cuya clave primaria coincida con la del objeto recibido.
     * Se usa para edición de datos y para rotación de hash/salt tras cambio de
     * contraseña (el repositorio genera el hash nuevo antes de llamar este método).
     *
     * @param tecnico técnico con los datos nuevos (su tecnicoId define la fila).
     * @return número de filas afectadas; 0 significa que el registro ya no existía.
     */
    @Update
    int update(Tecnico tecnico);

    // --- DELETE (D del CRUD, borrado lógico RN-6) ---

    /**
     * Da de baja un técnico marcándolo con activo = 0 (borrado lógico).
     * El registro permanece auditable y no rompe FKs de órdenes históricas
     * (las órdenes que ejecutó conservan su tecnico_id válido).
     * En términos de negocio, esta es la operación DELETE del CRUD.
     *
     * @param id clave primaria del técnico a dar de baja.
     * @return número de filas afectadas (1 si existía, 0 si no).
     */
    @Query("UPDATE tecnicos SET activo = 0 WHERE tecnico_id = :id")
    int eliminarLogico(long id);

    // --- MANTENIMIENTO DE CACHÉ (exclusivo de entidades API-first) ---

    /**
     * Vacía por completo la caché local de técnicos (borrado físico).
     * Es seguro porque esta tabla NO es fuente de verdad: su contenido se
     * reconstruye desde la API. Precaución: tras vaciar, el login offline queda
     * indisponible hasta el próximo refrescado exitoso.
     */
    @Query("DELETE FROM tecnicos")
    void vaciarCache();
}