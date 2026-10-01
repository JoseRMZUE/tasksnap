package ue.edu.co.tasksnap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.entity.Cliente;
import ue.edu.co.tasksnap.data.local.entity.Evidencia;
import ue.edu.co.tasksnap.data.local.entity.Orden;
import ue.edu.co.tasksnap.data.local.entity.Servicio;
import ue.edu.co.tasksnap.data.local.entity.Tecnico;
import ue.edu.co.tasksnap.repository.OrdenRepository;
import ue.edu.co.tasksnap.util.FormatoFecha;
import ue.edu.co.tasksnap.util.HashUtil;
import ue.edu.co.tasksnap.util.SesionLocal;

/**
 * Prueba instrumentada del OrdenRepository: verifica las reglas de negocio
 * (RN-1 a RN-8) contra una base de datos Room EN MEMORIA, aislada de la BD
 * real del dispositivo.
 *
 * Por qué instrumentada y no unitaria pura: Room exige un Context real, que
 * solo existe corriendo en dispositivo/emulador (AndroidJUnit4).
 *
 * Cómo se aísla:
 *  - BD: Room.inMemoryDatabaseBuilder -> cada ejecución arranca vacía y muere
 *    al cerrar; allowMainThreadQueries() se usa SOLO aquí (práctica exclusiva
 *    de testing, prohibida en producción).
 *  - Sesión: SesionLocal real del targetContext, limpiada en @After con
 *    cerrarSesion() para no dejar residuos entre tests.
 *  - Repositorio: se inyecta con el constructor CS-02 (db + sesion), sin tocar
 *    el singleton estático.
 *
 * Cómo se esperan los callbacks asíncronos: CountDownLatch con timeout de 5 s.
 * El repositorio entrega resultados en el hilo de UI vía Handler; el latch
 * bloquea el hilo de prueba hasta que onExito/onError hagan countDown().
 */
@RunWith(AndroidJUnit4.class)
public class OrdenRepositoryTest {

    /** Timeout generoso para cualquier operación de BD/disco en prueba. */
    private static final long TIMEOUT_S = 5;

    private AppDatabase db;
    private OrdenRepository repo;
    private SesionLocal sesion;

    private long tecnicoId;
    private long servicioId;
    private long clienteId;

    @Before
    public void setUp() {
        // CORREGIDO: Se agregó el paréntesis al método getTargetContext()
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // BD en memoria, aislada y volátil (solo para pruebas).
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .allowMainThreadQueries()   // EXCLUSIVO de testing
                .build();

        // Sembrado mínimo para que las FKs de la orden sean válidas.
        String salt = HashUtil.generarSalt();
        tecnicoId = db.tecnicoDao().insert(
                new Tecnico(0L, "Técnico Test", "tecnico",
                        HashUtil.hashConSalt("1234", salt), salt, true));
        servicioId = db.servicioDao().insert(new Servicio(0L, "Plomería", true));
        clienteId = db.clienteDao().insert(
                new Cliente(0L, "Cliente Test", "Calle 1 #2-3", "3001234567", true));

        // Sesión activa (fuente del tecnicoId según RN-4).
        sesion = new SesionLocal(ctx);
        sesion.iniciarSesion(tecnicoId, "tecnico");

        // Inyección de dependencias (CS-02): nada de singleton estático.
        repo = new OrdenRepository(db, sesion);
    }

    @After
    public void tearDown() {
        sesion.cerrarSesion();   // limpia SharedPreferences de la app bajo prueba
        db.close();              // destruye la BD en memoria
    }

    // ---------- HELPERS ----------

    /**
     * Crea una orden esperando el callback y devuelve su número asignado.
     * Falla la prueba si la creación devuelve error o expira el timeout.
     */
    private long crearOrdenEsperado(String descripcion) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Long> idRef = new AtomicReference<>();
        AtomicReference<String> errRef = new AtomicReference<>();
        repo.crearOrden(servicioId, clienteId, descripcion, "2026-09-25",
                new OrdenRepository.Callback<Long>() {
                    @Override public void onExito(Long dato) {
                        idRef.set(dato); latch.countDown();
                    }
                    @Override public void onError(String mensaje) {
                        errRef.set(mensaje); latch.countDown();
                    }
                });
        awaitOrFail(latch);
        if (errRef.get() != null) fail("No se esperaba error al crear: " + errRef.get());
        assertNotNull("Debe devolverse el número de orden", idRef.get());
        return idRef.get();
    }

    /** Inserta una evidencia activa directa (sin cámara) para probar RN-2. */
    private long insertarEvidenciaActiva(long ordenId) {
        Evidencia ev = new Evidencia();
        ev.setOrdenId(ordenId);
        ev.setRutaLocal("/data/user/0/test/files/evidencia_test.jpg");
        ev.setNombreArchivo("evidencia_test.jpg");
        ev.setFechaCaptura(FormatoFecha.ahora());
        ev.setSincronizado(false);
        ev.setActivo(true);
        return db.evidenciaDao().insert(ev);
    }

    /** Bloquea hasta que el latch llegue a 0 o expire el timeout; falla si expira. */
    private void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_S, TimeUnit.SECONDS)) {
                fail("El callback del repositorio no respondió en " + TIMEOUT_S + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Latch interrumpido: " + e.getMessage());
        }
    }

    // ---------- TESTS ----------

    /**
     * CREATE: una orden nueva nace con las invariantes del modelo:
     * RN-1 (PENDIENTE), RN-3 (sin cierre), RN-4 (técnico de sesión),
     * RN-5 (número autogenerado > 0), RN-7 (sincronizado = 0),
     * RN-8 (fecha_creacion asignada).
     */
    @Test
    public void crearOrden_naceConInvariantesDelModelo() {
        long num = crearOrdenEsperado("Revisión de fuga");

        Orden o = db.ordenDao().obtenerPorId(num);
        assertNotNull(o);
        assertTrue("RN-5: número autogenerado", num > 0);
        assertEquals("RN-1: nace pendiente", "PENDIENTE", o.getEstado());
        assertNull("RN-3: sin fecha de cierre al nacer", o.getFechaCierre());
        assertEquals("RN-4: técnico tomado de la sesión", tecnicoId, o.getTecnicoId());
        assertEquals(servicioId, o.getServicioId());
        assertEquals(clienteId, o.getClienteId());
        assertFalse("RN-7: pendiente de sincronizar", o.isSincronizado());
        assertFalse("RN-8: fecha de creación asignada", o.getFechaCreacion().isEmpty());
        assertTrue("RN-6: nace activa", o.isActivo());
    }

    /**
     * RN-2 (camino de rechazo): intentar cerrar una orden SIN evidencias debe
     * fallar con mensaje que mencione RN-2, y la orden debe quedar intacta
     * (PENDIENTE, sin fecha_cierre, aún pendiente de sync).
     */
    @Test
    public void cambiarEstado_aCompletadaSinEvidencia_fallaPorRN2() {
        long num = crearOrdenEsperado("Orden sin foto");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> okRef = new AtomicReference<>();
        AtomicReference<String> errRef = new AtomicReference<>();
        repo.cambiarEstado(num, "COMPLETADA", new OrdenRepository.Callback<Integer>() {
            @Override public void onExito(Integer dato) {
                okRef.set(dato); latch.countDown();
            }
            @Override public void onError(String mensaje) {
                errRef.set(mensaje); latch.countDown();
            }
        });
        awaitOrFail(latch);

        assertNull("No debía haber éxito al cerrar sin evidencia", okRef.get());
        assertNotNull("Debe rechazarse con mensaje", errRef.get());
        assertTrue("El mensaje debe citar la regla RN-2",
                errRef.get().contains("RN-2"));

        Orden o = db.ordenDao().obtenerPorId(num);
        assertEquals("RN-1: sigue pendiente tras el rechazo", "PENDIENTE", o.getEstado());
        assertNull("RN-3: sin cierre al no completarse", o.getFechaCierre());
    }

    /**
     * RN-2 (camino feliz) + RN-3: con al menos una evidencia activa, el cierre
     * prospera, la orden pasa a COMPLETADA y se graba fecha_cierre (RN-3),
     * quedando pendiente de sync (RN-7).
     */
    @Test
    public void cambiarEstado_aCompletadaConEvidencia_exitoYGrabaCierre() {
        long num = crearOrdenEsperado("Orden con foto");
        insertarEvidenciaActiva(num);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> okRef = new AtomicReference<>();
        AtomicReference<String> errRef = new AtomicReference<>();
        repo.cambiarEstado(num, "COMPLETADA", new OrdenRepository.Callback<Integer>() {
            @Override public void onExito(Integer dato) {
                okRef.set(dato); latch.countDown();
            }
            @Override public void onError(String mensaje) {
                errRef.set(mensaje); latch.countDown();
            }
        });
        awaitOrFail(latch);

        assertNull("No debía fallar con evidencia presente", errRef.get());
        assertNotNull("Debe reportar filas afectadas", okRef.get());

        Orden o = db.ordenDao().obtenerPorId(num);
        assertEquals("RN-1: ahora completada", "COMPLETADA", o.getEstado());
        assertNotNull("RN-3: fecha de cierre grabada", o.getFechaCierre());
        assertFalse("RN-3: la fecha de cierre no es vacía", o.getFechaCierre().isEmpty());
        assertFalse("RN-7: el cambio queda pendiente de sync", o.isSincronizado());
    }

    /**
     * RN-6 + cascada lógica: dar de baja una orden la marca inactiva y baja en
     * cascada sus evidencias activas, SIN destruir físicamente ningún registro
     * (la evidencia sigue legible por su PK, pero con activo = 0).
     */
    @Test
    public void eliminarOrden_borradoLogicoConCascadaDeEvidencias() {
        long num = crearOrdenEsperado("Orden a eliminar");
        long evId = insertarEvidenciaActiva(num);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> okRef = new AtomicReference<>();
        repo.eliminarOrden(num, new OrdenRepository.Callback<Integer>() {
            @Override public void onExito(Integer dato) {
                okRef.set(dato); latch.countDown();
            }
            @Override public void onError(String mensaje) {
                fail("No se esperaba error al eliminar: " + mensaje);
            }
        });
        awaitOrFail(latch);

        Orden o = db.ordenDao().obtenerPorId(num);
        assertFalse("RN-6: orden dada de baja lógicamente", o.isActivo());

        // Cascada: la evidencia activa de esa orden quedó inactiva...
        assertEquals("RN-6: evidencia bajada en cascada",
                0, db.evidenciaDao().contarActivasPorOrden(num));
        // ...pero NO se destruyó físicamente (auditable): sigue por su PK, inactiva.
        Evidencia ev = db.evidenciaDao().obtenerPorId(evId);
        assertNotNull("El registro de evidencia debe persistir (borrado lógico)", ev);
        assertFalse("La evidencia quedó inactiva, no borrada", ev.isActivo());
    }
}