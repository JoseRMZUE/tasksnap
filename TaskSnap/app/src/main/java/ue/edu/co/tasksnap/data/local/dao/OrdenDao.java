package ue.edu.co.tasksnap.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Orden;
import ue.edu.co.tasksnap.data.local.pojo.OrdenResumen;

/**
 * Data Access Object (DAO) de la tabla "ordenes".
 *
 * Un DAO es la única "ventanilla" por la que el resto de la aplicación accede a la
 * tabla: el repositorio llama a estos métodos y Room genera automáticamente la
 * implementación SQL real al compilar (clase generada: OrdenDao_Impl).
 * Ningún Activity debe llamar este DAO directamente (RFN-11).
 *
 * Rol en la arquitectura:
 *  - Órdenes es una entidad LOCAL-FIRST: esta tabla es la fuente de verdad del
 *    técnico en campo, con CRUD completo offline (RF-17, RFN-10).
 *  - Es el CRUD local que satisface el Requisito 2 del curso (SQLite/Room).
 *  - El OrdenRepository orchesta: escribe aquí primero y luego marca
 *    sincronizado = 0 para que el módulo de sync empuje los cambios a la API (RN-7).
 *
 * CRUD completo (4 operaciones) según la matriz de cumplimiento del curso:
 *  - CREATE: {@link #insert(Orden)}
 *  - READ:   {@link #listarActivas()}, {@link #buscarPorNumero(String)},
 *            {@link #obtenerPorId(long)}, {@link #listarPorEstado(String)},
 *            {@link #listarPendientesSync()}
 *  - UPDATE: {@link #update(Orden)}, {@link #marcarSincronizada(long)},
 *            {@link #marcarPendienteSync(long)}
 *  - DELETE: {@link #eliminarLogico(long)} (borrado lógico, RN-6)
 *
 * Reglas de negocio relacionadas:
 *  - RN-1: los listados por estado solo admiten PENDIENTE o COMPLETADA.
 *  - RN-2: la validación "COMPLETADA exige al menos una evidencia" se hace en el
 *    OrdenRepository combinando {@link #obtenerPorId(long)} con
 *    EvidenciaDao.contarActivasPorOrden(long) (cada DAO consulta su propia tabla).
 *  - RN-5: num_orden no es editable por el usuario; solo lo asigna Room al insertar.
 *  - RN-6: los listados filtran activo = 1; el borrado es lógico.
 *  - RN-7: listarPendientesSync alimenta el módulo de sincronización.
 *
 * Nota de hilos: todos estos métodos son síncronos; deben invocarse desde un hilo
 * secundario (el repositorio usará ExecutorService), nunca desde el hilo de UI.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Dao
public interface OrdenDao {

    // --- CREATE (C del CRUD) ---

    /**
     * Inserta una orden nueva en la base de datos local.
     *
     * Estrategia de conflicto ABORT (a diferencia de las cachés API-first):
     * como esta tabla es FUENTE DE VERDAD, un choque de PK significaría un bug
     * real (por ejemplo, reinsertar una orden existente), y preferimos que falle
     * con excepción en vez de reemplazar datos de campo silenciosamente.
     * Con numOrden = 0L no hay conflicto posible: Room autogenera el número (RN-5,
     * RN-Técnica-01).
     *
     * @param orden orden a insertar; el repositorio debe llegar aquí con
     *              tecnicoId de la sesión (RN-4) y fechaCreacion asignada.
     * @return el número de orden (rowId) asignado por SQLite.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Orden orden);

    // --- READ (R del CRUD) ---

    /**
     * Devuelve todas las órdenes activas ordenadas por fecha de servicio más
     * próxima primero (y por número en caso de empate).
     * Es el listado principal del lvList de la pantalla principal.
     * Aplica RN-6: las dadas de baja nunca se muestran.
     *
     * @return lista de órdenes activas; vacía si no hay ninguna.
     */
    @Query("SELECT * FROM ordenes WHERE activo = 1 "
            + "ORDER BY fecha_servicio ASC, num_orden ASC")
    List<Orden> listarActivas();

    /**
     * Busca órdenes activas cuyo número coincida total o parcialmente con el
     * patrón recibido (RF-06). El repositorio debe pasar el patrón con comodines,
     * por ejemplo "%2026%" para "todas las que contengan 2026".
     * Se usa CAST porque num_orden es INTEGER y LIKE compara texto.
     *
     * @param patron patrón LIKE con comodines (ej: "%001%").
     * @return lista de órdenes que coinciden; vacía si ninguna.
     */
    @Query("SELECT * FROM ordenes WHERE activo = 1 "
            + "AND CAST(num_orden AS TEXT) LIKE :patron "
            + "ORDER BY num_orden DESC")
    List<Orden> buscarPorNumero(String patron);

    /**
     * Devuelve una única orden a partir de su clave primaria.
     * Se usa para precargar el formulario en modo edición (RF-07) y como base de
     * la validación RN-2/RN-3 en el repositorio.
     *
     * @param numOrden clave primaria de la orden buscada.
     * @return la orden encontrada, o null si no existe.
     */
    @Query("SELECT * FROM ordenes WHERE num_orden = :numOrden LIMIT 1")
    Orden obtenerPorId(long numOrden);

    /**
     * Devuelve las órdenes activas filtradas por estado (RN-1).
     * Útil para vistas tipo "pendientes" o "completadas" y para que el módulo de
     * notificaciones (RF-13) tome la próxima cita entre las PENDIENTE.
     *
     * @param estado "PENDIENTE" o "COMPLETADA".
     * @return lista de órdenes activas en ese estado, por fecha próxima.
     */
    @Query("SELECT * FROM ordenes WHERE activo = 1 AND estado = :estado "
            + "ORDER BY fecha_servicio ASC")
    List<Orden> listarPorEstado(String estado);

    /**
     * Devuelve las órdenes que aún no han sido enviadas a la API (RN-7).
     * Es la cola de trabajo del módulo de sincronización: cada orden devuelta
     * aquí debe empujarse a POST/PUT de /api/ordenes y luego marcarse sincronizada.
     *
     * @return lista de órdenes activas con sincronizado = 0, en orden de creación.
     */
    @Query("SELECT * FROM ordenes WHERE sincronizado = 0 AND activo = 1 "
            + "ORDER BY num_orden ASC")
    List<Orden> listarPendientesSync();

    // --- UPDATE (U del CRUD) ---

    /**
     * Actualiza la fila cuya clave primaria coincida con la del objeto recibido.
     * El repositorio la usa para edición (RF-07), cambios de estado (RN-1/RN-3)
     * y cierre con fecha_cierre. Tras un update de negocio, el repositorio debe
     * dejar sincronizado = 0 para re-empujar el cambio a la API.
     *
     * @param orden orden con los datos nuevos (su numOrden define la fila).
     * @return número de filas afectadas; 0 significa que la orden ya no existía.
     */
    @Update
    int update(Orden orden);

    /**
     * Marca una orden como ya enviada a la API (sincronizado = 1, RN-7).
     * La llama el módulo de sincronización tras recibir respuesta exitosa
     * del servidor.
     *
     * @param numOrden clave primaria de la orden sincronizada.
     * @return número de filas afectadas.
     */
    @Query("UPDATE ordenes SET sincronizado = 1 WHERE num_orden = :numOrden")
    int marcarSincronizada(long numOrden);

    /**
     * Marca una orden como pendiente de envío (sincronizado = 0, RN-7).
     * La llama el repositorio después de cada creación o edición local, para que
     * el cambio entre en la cola de sincronización.
     *
     * @param numOrden clave primaria de la orden modificada.
     * @return número de filas afectadas.
     */
    @Query("UPDATE ordenes SET sincronizado = 0 WHERE num_orden = :numOrden")
    int marcarPendienteSync(long numOrden);

    // --- DELETE (D del CRUD, borrado lógico RN-6) ---

    /**
     * Da de baja una orden marcándola con activo = 0 (borrado lógico).
     * El registro permanece auditable y sus evidencias quedan huérfanas pero
     * intactas (también se dan de baja en cascada lógica desde el repositorio).
     * En términos de negocio, esta es la operación DELETE del CRUD (RF-08).
     *
     * @param numOrden clave primaria de la orden a dar de baja.
     * @return número de filas afectadas (1 si existía, 0 si no).
     */
    @Query("UPDATE ordenes SET activo = 0 WHERE num_orden = :numOrden")
    int eliminarLogico(long numOrden);



    // --- PROYECCIONES PARA UI (JOIN: nombres en vez de IDs) ---

    /**
     * Listado principal con nombres resueltos por JOIN (RF-05).
     * Los alias (AS ...) deben coincidir con los campos de OrdenResumen.
     *
     * @return órdenes activas con servicio, cliente, estado y sync, por fecha próxima.
     */
    @Query("SELECT o.num_orden AS numOrden, s.nombre AS servicioNombre, "
            + "c.nombre AS clienteNombre, o.estado AS estado, o.sincronizado AS sincronizado "
            + "FROM ordenes o "
            + "INNER JOIN servicios s ON o.servicio_id = s.servicio_id "
            + "INNER JOIN clientes c ON o.cliente_id = c.cliente_id "
            + "WHERE o.activo = 1 "
            + "ORDER BY o.fecha_servicio ASC, o.num_orden ASC")
    List<OrdenResumen> listarResumenActivas();

    /**
     * Búsqueda por número (total o parcial) con nombres resueltos (RF-06).
     *
     * @param patron patrón LIKE con comodines (el repositorio lo construye).
     * @return coincidencias activas en formato de resumen.
     */
    @Query("SELECT o.num_orden AS numOrden, s.nombre AS servicioNombre, "
            + "c.nombre AS clienteNombre, o.estado AS estado, o.sincronizado AS sincronizado "
            + "FROM ordenes o "
            + "INNER JOIN servicios s ON o.servicio_id = s.servicio_id "
            + "INNER JOIN clientes c ON o.cliente_id = c.cliente_id "
            + "WHERE o.activo = 1 AND CAST(o.num_orden AS TEXT) LIKE :patron "
            + "ORDER BY o.num_orden DESC")
    List<OrdenResumen> buscarResumenPorNumero(String patron);

    /**
     * Cola de sincronizacion (RN-7): ordenes locales que el servidor
     * aun no confirma. El SyncManager (Turno 7.4) consume esta lista.
     * OJO: los nombres dentro de @Query son columnas SQLite (snake_case),
     * no campos Java: num_orden, no numOrden.
     */
    @Query("SELECT * FROM ordenes WHERE sincronizado = 0 AND activo = 1 ORDER BY num_orden ASC")
    List<Orden> listarNoSincronizadas();

    /**
     * Sello de confirmacion tras push exitoso: marca la orden como
     * sincronizada y guarda el ID que asigno el servidor (RN-7).
     */
    @Query("UPDATE ordenes SET sincronizado = 1, num_orden_remoto = :numRemoto WHERE num_orden = :numLocal")
    void marcarSincronizada(long numLocal, long numRemoto);
}