package ue.edu.co.tasksnap.debug;

import android.content.Context;
import android.util.Log;

import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.db.SeedInitializer;
import ue.edu.co.tasksnap.data.local.entity.Tecnico;
import ue.edu.co.tasksnap.repository.OrdenRepository;
import ue.edu.co.tasksnap.util.FormatoFecha;
import ue.edu.co.tasksnap.util.SesionLocal;

/**
 * Prueba de humo (smoke test) de la Fase 1 y Lección 5 del proyecto TaskSnap.
 *
 * PROPÓSITO:
 *  Verificar en dispositivo real, sin interfaz gráfica, que toda la capa de
 *  datos local funciona de punta a punta: base de datos singleton, seed
 *  idempotente, sesión activa, repositorio de órdenes y reglas de negocio
 *  RN-2, RN-3, RN-4 y RN-7.
 *
 * VERIFICACIONES Y RESULTADO ESPERADO EN LOGCAT:
 *  1. Seed:     tag TASKSNAP_DB   -> "Tecnicos: 1 | Servicios: 3 | Clientes: 2"
 *  2. Sesión:   sin log; si no hay sesión, inicia una con el técnico sembrado.
 *  3. Creación: tag TASKSNAP_REPO -> "Orden creada: 1"
 *  4. RN-2:     tag TASKSNAP_REPO -> "RN-2 actuó: No se puede completar: ..."
 *     (demuestra que el cierre sin evidencia fotográfica queda bloqueado)
 *  5. Database Inspector: tabla "ordenes" con 1 fila en estado PENDIENTE,
 *     sincronizado = 0 y fecha_cierre NULL.
 *
 * CICLO DE VIDA (por qué vive en el paquete "debug" y no en el producto):
 *  - Es andamio de verificación, no código de producto.
 *  - Se ELIMINARÁ en la Fase 2, cuando MainActivity conecte el repositorio a
 *    la UI real. Retiro = borrar la llamada en onCreate y este paquete.
 *  - Su intención vivirá después como prueba unitaria en el source set (test)
 *    con Room en memoria, siguiendo la práctica profesional.
 *
 * USO:
 *  Una sola línea en MainActivity.onCreate: VerificacionFase1.ejecutar(this);
 *  La clase abre su propio hilo secundario: nunca bloquea el hilo de UI.
 */
public final class VerificacionFase1 {

    /** Tag de logs del seed (continuidad con la verificación de la Lección 4). */
    private static final String TAG_SEED = "TASKSNAP_DB";

    /** Tag de logs del repositorio (continuidad con la Lección 5). */
    private static final String TAG_REPO = "TASKSNAP_REPO";

    /** Constructor privado: clase de utilidades, no debe instanciarse. */
    private VerificacionFase1() {
    }

    /**
     * Ejecuta todas las verificaciones de la Fase 1 en un hilo secundario.
     *
     * @param context cualquier contexto; MainActivity pasa "this".
     */
    public static void ejecutar(Context context) {
        new Thread(() -> {
            verificarSeedYSesion(context);
            verificarRepositorioYRN2(context);
        }).start();
    }

    /**
     * Verificación 1: la base de datos singleton abre, el seed idempotente
     * puebla las tablas maestro y la sesión queda activa para RN-4.
     *
     * @param context contexto para obtener AppDatabase y SesionLocal.
     */
    private static void verificarSeedYSesion(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        SeedInitializer.sembrarSiVacio(db);
        Log.i(TAG_SEED,
                "Tecnicos: " + db.tecnicoDao().listarActivos().size()
                        + " | Servicios: " + db.servicioDao().listarActivos().size()
                        + " | Clientes: " + db.clienteDao().listarActivos().size());

        SesionLocal sesion = new SesionLocal(context);
        if (!sesion.haySesion()) {
            Tecnico tecnico = db.tecnicoDao().buscarPorUsuario("tecnico");
            sesion.iniciarSesion(tecnico.getTecnicoId(), tecnico.getUsuario());
        }
    }

    /**
     * Verificación 2: el repositorio crea una orden completa (RN-4, RN-7, RN-8)
     * y luego intenta cerrarla sin evidencias, lo que DEBE ser rechazado por
     * RN-2. Si el cierre fuera permitido, el log mostraría "FALLO DE PRUEBA".
     *
     * @param context contexto para construir el OrdenRepository.
     */
    private static void verificarRepositorioYRN2(Context context) {
        OrdenRepository repo = new OrdenRepository(context);
        repo.crearOrden(1L, 1L, "Revisión de fuga en cocina",
                FormatoFecha.hoy(), new OrdenRepository.Callback<Long>() {
                    @Override
                    public void onExito(Long numOrden) {
                        Log.i(TAG_REPO, "Orden creada: " + numOrden);
                        repo.cambiarEstado(numOrden, "COMPLETADA",
                                new OrdenRepository.Callback<Integer>() {
                                    @Override
                                    public void onExito(Integer filas) {
                                        Log.w(TAG_REPO, "FALLO DE PRUEBA: se cerró sin evidencia");
                                    }

                                    @Override
                                    public void onError(String mensaje) {
                                        Log.i(TAG_REPO, "RN-2 actuó: " + mensaje);
                                    }
                                });
                    }

                    @Override
                    public void onError(String mensaje) {
                        Log.w(TAG_REPO, "Error creación: " + mensaje);
                    }
                });
    }
}