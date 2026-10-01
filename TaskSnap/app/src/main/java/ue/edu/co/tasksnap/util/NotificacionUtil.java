package ue.edu.co.tasksnap.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * Utilidad de notificaciones locales (RF-13, RF-14).
 *
 * Responsabilidades:
 *  - Crear el canal de notificaciones (obligatorio desde Android 8.0 / API 26).
 *  - Disparar notificaciones inmediatas (ej: "orden creada exitosamente").
 *  - Las notificaciones programadas (recordatorios) las maneja RecordatorioScheduler.
 *
 * Decisión de diseño: el canal se crea una sola vez al arrancar la app
 * (en MainActivity.onCreate o en una clase Application si la tuviéramos).
 */
public final class NotificacionUtil {

    /** ID del canal de notificaciones (único por app). */
    public static final String CANAL_ID = "tasksnap_recordatorios";

    /** Nombre visible del canal en los ajustes del sistema. */
    private static final String CANAL_NOMBRE = "Recordatorios TaskSnap";

    /** Descripción del canal en los ajustes del sistema. */
    private static final String CANAL_DESCRIPCION = "Notificaciones de próximas citas y órdenes";

    /** Constructor privado: clase de utilidades, no debe instanciarse. */
    private NotificacionUtil() {
    }

    /**
     * Crea el canal de notificaciones si aún no existe (Android 8.0+).
     * Debe llamarse una sola vez al arrancar la app, antes de disparar
     * cualquier notificación.
     *
     * @param context contexto para obtener el NotificationManager.
     */
    public static void crearCanal(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL_ID,
                    CANAL_NOMBRE,
                    NotificationManager.IMPORTANCE_HIGH
            );
            canal.setDescription(CANAL_DESCRIPCION);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }

    /**
     * Dispara una notificación inmediata con título y mensaje.
     *
     * @param context    contexto para construir la notificación.
     * @param id         ID único de la notificación (para actualizarla o cancelarla después).
     * @param titulo     título de la notificación.
     * @param mensaje    cuerpo de la notificación.
     */
    public static void mostrarNotificacion(Context context, int id, String titulo, String mensaje) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CANAL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)  // Icono del sistema
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);  // Se elimina al tocarla

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(id, builder.build());
        }
    }

    /**
     * Cancela una notificación por su ID (si el usuario ya completó la orden,
     * por ejemplo, y queremos quitar el recordatorio).
     *
     * @param context contexto para obtener el NotificationManager.
     * @param id      ID de la notificación a cancelar.
     */
    public static void cancelarNotificacion(Context context, int id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(id);
        }
    }
}