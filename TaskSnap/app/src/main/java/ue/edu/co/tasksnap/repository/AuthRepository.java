package ue.edu.co.tasksnap.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ue.edu.co.tasksnap.data.local.dao.TecnicoDao;
import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.entity.Tecnico;
import ue.edu.co.tasksnap.util.HashUtil;
import ue.edu.co.tasksnap.util.SesionLocal;

/**
 * Repositorio de autenticación offline-first (RF-01, RF-02, RF-03).
 * Única clase autorizada a validar hashes y gestionar sesiones.
 */
public class AuthRepository {

    private static final String TAG = "AUTH_REPO";

    /** Interfaz específica para Login: devuelve Objeto Tecnico en éxito. */
    public interface CallbackLogin {
        void onExito(Tecnico tecnicoAutenticado);
        void onError(String mensaje);
    }

    private final TecnicoDao tecnicoDao;
    private final SesionLocal sesionLocal;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    /** Constructor de Producción. */
    public AuthRepository(Context context) {
        this(AppDatabase.getInstance(context).tecnicoDao(), new SesionLocal(context));
    }

    /** Constructor Inyectable (CS-02). */
    public AuthRepository(TecnicoDao tecnicoDao, SesionLocal sesionLocal) {
        this.tecnicoDao = tecnicoDao;
        this.sesionLocal = sesionLocal;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Valida credenciales contra caché local.
     * Seguridad: Mensaje genérico para evitar enumeración de usuarios.
     */
    public void iniciarSesion(String usuario, String password, CallbackLogin callback) {
        if (usuario == null || usuario.trim().isEmpty() ||
                password == null || password.isEmpty()) {
            mainHandler.post(() -> callback.onError("Campos vacíos."));
            return;
        }

        executorService.execute(() -> {
            try {
                String userTrimmed = usuario.trim();
                final String ERROR_GENERICO = "Usuario o contraseña incorrectos.";

                // 1. Buscar Técnico
                Tecnico tecnico = tecnicoDao.buscarPorUsuario(userTrimmed);

                // 2. Validar Existencia y Actividad (RN-6)
                if (tecnico == null || !tecnico.isActivo()) {
                    Log.w(TAG, "Falló búsqueda/actividad para: " + userTrimmed);
                    mainHandler.post(() -> callback.onError(ERROR_GENERICO));
                    return;
                }

                // 3. Calcular Hash y Comparar
                String hashCalculado = HashUtil.hashConSalt(password, tecnico.getSalt());

                if (hashCalculado.equals(tecnico.getPasswordHash())) {
                    // Éxito: Persistir Sesión
                    sesionLocal.iniciarSesion(tecnico.getTecnicoId(), tecnico.getUsuario());
                    Log.i(TAG, "Login exitoso: " + userTrimmed);
                    mainHandler.post(() -> callback.onExito(tecnico));
                } else {
                    Log.w(TAG, "Hash mismatch para: " + userTrimmed);
                    mainHandler.post(() -> callback.onError(ERROR_GENERICO));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error crítico auth", e);
                mainHandler.post(() -> callback.onError("Error de sistema."));
            }
        });
    }

    public boolean haySesionActiva() {
        return sesionLocal.haySesion();
    }

    public String obtenerUsuarioEnSesion() {
        return sesionLocal.obtenerUsuario();
    }

    public void cerrarSesion() {
        sesionLocal.cerrarSesion();
    }
}