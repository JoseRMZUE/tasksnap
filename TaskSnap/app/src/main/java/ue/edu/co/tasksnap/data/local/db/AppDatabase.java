package ue.edu.co.tasksnap.data.local.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import ue.edu.co.tasksnap.data.local.dao.ClienteDao;
import ue.edu.co.tasksnap.data.local.dao.EvidenciaDao;
import ue.edu.co.tasksnap.data.local.dao.OrdenDao;
import ue.edu.co.tasksnap.data.local.dao.ServicioDao;
import ue.edu.co.tasksnap.data.local.dao.TecnicoDao;
import ue.edu.co.tasksnap.data.local.entity.Cliente;
import ue.edu.co.tasksnap.data.local.entity.Evidencia;
import ue.edu.co.tasksnap.data.local.entity.Orden;
import ue.edu.co.tasksnap.data.local.entity.Servicio;
import ue.edu.co.tasksnap.data.local.entity.Tecnico;

/**
 * Base de datos Room de TaskSnap: punto de acceso único a SQLite local.
 *
 * Registra las 5 entidades del modelo E-R v4.1 y expone los 5 DAOs.
 * Aplica patrón SINGLETON: toda la app comparte UNA sola instancia de conexión
 * (una sola apertura del archivo tasksnap.db), lo que evita estados
 * inconsistentes, bloqueos y corrupción del archivo.
 *
 * El esquema se exporta a la carpeta schemas/ gracias a exportSchema = true
 * combinado con room.schemaLocation configurado en build.gradle.kts
 * (documentación técnica automática del proyecto).
 *
 * Fase 5 (Turno 7.3): version 1 -> 2 mediante MIGRATION_1_2, que agrega la columna
 * num_orden_remoto a la tabla ordenes (RN-7). ALTER TABLE ADD COLUMN es la unica
 * evolucion no destructiva de un esquema SQLite vivo: conserva todas las filas
 * existentes y la columna nueva nace en NULL (= "nunca sincronizada").
 */
@Database(
        entities = {Tecnico.class, Cliente.class, Servicio.class, Orden.class, Evidencia.class},
        version = 2,               // <- subido de 1 a 2 (Fase 5, Turno 7.3)
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    /** Instancia única; volatile garantiza que todos los hilos vean el mismo valor. */
    private static volatile AppDatabase instancia;

    /** @return DAO de la tabla tecnicos. */
    public abstract TecnicoDao tecnicoDao();

    /** @return DAO de la tabla clientes. */
    public abstract ClienteDao clienteDao();

    /** @return DAO de la tabla servicios. */
    public abstract ServicioDao servicioDao();

    /** @return DAO de la tabla ordenes. */
    public abstract OrdenDao ordenDao();

    /** @return DAO de la tabla evidencias. */
    public abstract EvidenciaDao evidenciaDao();

    /**
     * Migracion 1 -> 2: columna de ID remoto para ordenes (RN-7, Fase 5).
     *
     * ALTER TABLE ADD COLUMN es la unica forma no destructiva de evolucionar
     * un esquema SQLite vivo: las filas existentes conservan sus datos y la
     * columna nueva nace en NULL (= "nunca sincronizada").
     *
     * El nombre de la columna debe coincidir EXACTO con el @ColumnInfo de la
     * entidad Orden (num_orden_remoto) y con las queries nuevas del OrdenDao
     * (listarNoSincronizadas / marcarSincronizada).
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE ordenes ADD COLUMN num_orden_remoto INTEGER");
        }
    };

    /**
     * Devuelve la instancia única de la base de datos, creándola solo la
     * primera vez (double-checked locking).
     *
     * @param context cualquier contexto; se usa getApplicationContext() para
     *                no retener Activities en memoria (fuga de memoria).
     * @return la instancia única de AppDatabase.
     */
    public static AppDatabase getInstance(Context context) {
        if (instancia == null) {                       // 1) vía rápida sin candado
            synchronized (AppDatabase.class) {         // 2) candado: un hilo a la vez
                if (instancia == null) {               // 3) re-check tras esperar
                    instancia = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "tasksnap.db")
                            .addMigrations(MIGRATION_1_2)   // <- agregado Fase 5
                            .build();                  // 4) apertura del archivo .db
                }
            }
        }
        return instancia;
    }
}