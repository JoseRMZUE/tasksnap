package ue.edu.co.tasksnap.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Evidencia;

/**
 * Data Access Object (DAO) de la tabla "evidencias".
 *
 * Un DAO es la única "ventanilla" por la que el resto de la aplicación accede a la
 * tabla: el repositorio llama a estos métodos y Room genera automáticamente la
 * implementación SQL real al compilar (clase generada: EvidenciaDao_Impl).
 * Ningún Activity debe llamar este DAO directamente (RFN-11).
 *
 * Rol en la arquitectura:
 *  - Evidencias es una entidad LOCAL-FIRST: esta tabla guarda los METADATOS de las
 *    fotografías; el archivo de imagen vive en el almacenamiento interno del
 *    dispositivo (requisito "Archivos" del curso).
 *  - Forma parte del flujo de órdenes: el técnico captura la foto al cerrar una
 *    orden (RF-12) y todo funciona sin conexión (RF-17, RFN-10).
 *  - El EvidenciaRepository orchesta: escribe metadatos aquí, guarda/borra el
 *    archivo en disco, y marca sincronizado = 0 para el push a la API (RN-7).
 *
 * CRUD completo (4 operaciones) según la matriz de cumplimiento del curso:
 *  - CREATE: {@link #insert(Evidencia)}
 *  - READ:   {@link #listarPorOrden(long)}, {@link #obtenerPorId(long)},
 *            {@link #contarActivasPorOrden(long)}, {@link #listarPendientesSync()}
 *  - UPDATE: {@link #update(Evidencia)}, {@link #marcarSincronizada(long)}
 *  - DELETE: {@link #eliminarLogico(long)} y {@link #eliminarLogicasPorOrden(long)}
 *            (borrado lógico, RN-6)
 *
 * Reglas de negocio relacionadas:
 *  - RN-2: {@link #contarActivasPorOrden(long)} es la consulta que el
 *    OrdenRepository usa para validar que una orden COMPLETADA tenga al menos
 *    una evidencia activa antes de permitir el cierre.
 *  - RN-6: los borrados son lógicos; el archivo en disco NO se elimina al dar de
 *    baja (permanece auditable). La eliminación física del archivo, si algún día
 *    se requiere, la hace el repositorio, nunca este DAO.
 *  - RN-7: listarPendientesSync alimenta la cola de subida de fotos a la API.
 *
 * Nota de hilos: todos estos métodos son síncronos; deben invocarse desde un hilo
 * secundario (el repositorio usará ExecutorService), nunca desde el hilo de UI.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Dao
public interface EvidenciaDao {

    // --- CREATE (C del CRUD) ---

    /**
     * Inserta los metadatos de una evidencia fotográfica en la base de datos local.
     *
     * Estrategia de conflicto ABORT (igual que OrdenDao): esta tabla es FUENTE DE
     * VERDAD local, así que un choque de PK sería un bug real y preferimos que
     * falle con excepción. Con evidenciaId = 0L no hay conflicto posible: Room
     * autogenera el ID (RN-Técnica-01).
     *
     * Importante: este método solo guarda metadatos. El archivo de imagen ya debe
     * existir en disco (lo guarda el repositorio antes de llamar aquí), y la
     * rutaLocal debe apuntar a ese archivo.
     *
     * @param evidencia evidencia a insertar; el repositorio debe llegar aquí con
     *                  ordenId válido (FK), rutaLocal y fechaCaptura asignadas.
     * @return el ID (rowId) asignado por SQLite.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Evidencia evidencia);

    // --- READ (R del CRUD) ---

    /**
     * Devuelve todas las evidencias activas de una orden, ordenadas por fecha de
     * captura (la galería fotográfica de la orden en la UI).
     *
     * @param ordenId clave primaria de la orden consultada (FK orden_id).
     * @return lista de evidencias activas de esa orden; vacía si no tiene ninguna.
     */
    @Query("SELECT * FROM evidencias WHERE orden_id = :ordenId AND activo = 1 "
            + "ORDER BY fecha_captura ASC")
    List<Evidencia> listarPorOrden(long ordenId);

    /**
     * Devuelve una única evidencia a partir de su clave primaria.
     * Se usa para ver el detalle de una foto o precargar su edición.
     *
     * @param evidenciaId clave primaria de la evidencia buscada.
     * @return la evidencia encontrada, o null si no existe.
     */
    @Query("SELECT * FROM evidencias WHERE evidencia_id = :evidenciaId LIMIT 1")
    Evidencia obtenerPorId(long evidenciaId);

    /**
     * Cuenta cuántas evidencias activas tiene una orden.
     * ES LA CONSULTA CLAVE DE LA RN-2: el OrdenRepository la usa antes de permitir
     * que una orden pase a COMPLETADA; si devuelve 0, el cierre se rechaza con un
     * mensaje pidiendo capturar al menos una foto.
     *
     * @param ordenId clave primaria de la orden consultada.
     * @return número de evidencias activas de esa orden (0 o más).
     */
    @Query("SELECT COUNT(*) FROM evidencias WHERE orden_id = :ordenId AND activo = 1")
    int contarActivasPorOrden(long ordenId);

    /**
     * Devuelve las evidencias que aún no han sido subidas a la API (RN-7).
     * Es la cola de trabajo del módulo de sincronización de fotos: cada evidencia
     * devuelta aquí debe enviarse (Base64 comprimido) y luego marcarse sincronizada.
     *
     * @return lista de evidencias activas con sincronizado = 0, en orden de captura.
     */
    @Query("SELECT * FROM evidencias WHERE sincronizado = 0 AND activo = 1 "
            + "ORDER BY evidencia_id ASC")
    List<Evidencia> listarPendientesSync();

    // --- UPDATE (U del CRUD) ---

    /**
     * Actualiza la fila cuya clave primaria coincida con la del objeto recibido.
     * Se usa, por ejemplo, si el técnico re-toma una foto y el repositorio
     * actualiza rutaLocal y fechaCaptura de la evidencia existente.
     *
     * @param evidencia evidencia con los datos nuevos (su evidenciaId define la fila).
     * @return número de filas afectadas; 0 significa que el registro ya no existía.
     */
    @Update
    int update(Evidencia evidencia);

    /**
     * Marca una evidencia como ya subida a la API (sincronizado = 1, RN-7).
     * La llama el módulo de sincronización tras recibir respuesta exitosa.
     *
     * @param evidenciaId clave primaria de la evidencia sincronizada.
     * @return número de filas afectadas.
     */
    @Query("UPDATE evidencias SET sincronizado = 1 WHERE evidencia_id = :evidenciaId")
    int marcarSincronizada(long evidenciaId);

    // --- DELETE (D del CRUD, borrado lógico RN-6) ---

    /**
     * Da de baja una evidencia marcándola con activo = 0 (borrado lógico).
     * El archivo en disco permanece (auditable); la foto deja de mostrarse en la
     * galería de la orden y deja de contar para la RN-2.
     * En términos de negocio, esta es la operación DELETE del CRUD.
     *
     * @param evidenciaId clave primaria de la evidencia a dar de baja.
     * @return número de filas afectadas (1 si existía, 0 si no).
     */
    @Query("UPDATE evidencias SET activo = 0 WHERE evidencia_id = :evidenciaId")
    int eliminarLogico(long evidenciaId);

    /**
     * Da de baja TODAS las evidencias activas de una orden (cascada lógica).
     * La llama el OrdenRepository inmediatamente después de eliminarLogico(numOrden),
     * para que una orden dada de baja no deje evidencias "vivas" huérfanas.
     * No es un DELETE físico: respeta RN-6 y mantiene la auditoría.
     *
     * @param ordenId clave primaria de la orden cuyas evidencias se darán de baja.
     * @return número de filas afectadas.
     */
    @Query("UPDATE evidencias SET activo = 0 WHERE orden_id = :ordenId AND activo = 1")
    int eliminarLogicasPorOrden(long ordenId);
}