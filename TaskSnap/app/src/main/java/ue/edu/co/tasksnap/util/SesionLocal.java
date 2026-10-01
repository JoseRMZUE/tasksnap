package ue.edu.co.tasksnap.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Envoltorio de SharedPreferences para la sesión activa del técnico (RF-02, RFN-07).
 *
 * Responsabilidades:
 *  - Guardar el tecnicoId y el usuario de la sesión vigente.
 *  - Permitir consultar la sesión (RN-4: el repositorio toma el técnico de aquí,
 *    nunca de la UI) y cerrarla (RF-03).
 *
 * Seguridad (RFN-07): aquí NO se guardan contraseñas ni hashes; solo el ID y el
 * nombre de usuario. La credencial nunca persiste en el dispositivo.
 *
 * Esta clase se crea en la Lección 5 porque RN-4 la exige hoy, y se reutilizará
 * sin cambios en la Fase 3 (login) y en toda la app.
 */
public final class SesionLocal {

    /** Nombre del archivo de preferencias privadas de la sesión. */
    private static final String PREFS_NAME = "tasksnap_sesion";

    /** Clave bajo la que se guarda el ID del técnico en sesión. */
    private static final String KEY_TECNICO_ID = "tecnico_id";

    /** Clave bajo la que se guarda el nombre de usuario en sesión. */
    private static final String KEY_USUARIO = "usuario";

    /** Preferencias privadas de la aplicación (MODE_PRIVATE). */
    private final SharedPreferences prefs;

    /**
     * @param context cualquier contexto; se usa getApplicationContext() para
     *                no retener Activities en memoria.
     */
    public SesionLocal(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Registra la sesión activa tras un login exitoso.
     *
     * @param tecnicoId ID del técnico autenticado.
     * @param usuario   nombre de usuario autenticado.
     */
    public void iniciarSesion(long tecnicoId, String usuario) {
        prefs.edit()
                .putLong(KEY_TECNICO_ID, tecnicoId)
                .putString(KEY_USUARIO, usuario)
                .apply();
    }

    /**
     * @return el ID del técnico en sesión, o 0L si no hay sesión activa.
     */
    public long obtenerTecnicoId() {
        return prefs.getLong(KEY_TECNICO_ID, 0L);
    }

    /**
     * @return el nombre de usuario en sesión, o cadena vacía si no hay sesión.
     */
    public String obtenerUsuario() {
        return prefs.getString(KEY_USUARIO, "");
    }

    /**
     * @return true si existe una sesión activa (tecnicoId > 0).
     */
    public boolean haySesion() {
        return obtenerTecnicoId() > 0L;
    }

    /**
     * Cierra la sesión eliminando todo el contenido de las preferencias (RF-03).
     */
    public void cerrarSesion() {
        prefs.edit().clear().apply();
    }
}