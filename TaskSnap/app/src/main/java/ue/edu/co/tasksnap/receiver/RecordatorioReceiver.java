package ue.edu.co.tasksnap.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ue.edu.co.tasksnap.util.NotificacionUtil;

/**
 * BroadcastReceiver que recibe las alarmas de RecordatorioScheduler y dispara
 * la notificación de recordatorio (RF-13).
 *
 * Registro en AndroidManifest.xml (dentro de <application>):
 *   <receiver android:name=".receiver.RecordatorioReceiver" android:exported="false" />
 *
 * exported="false": solo esta app puede enviarle broadcasts (seguridad).
 *
 * Riesgo 1 (ciclo de vida del proceso): la alarma puede dispararse cuando la app
 * NO está en foreground y el canal de notificación no se haya creado en esta
 * sesión. Por eso onReceive garantiza el canal ANTES de mostrar la notificación;
 * crearCanal es idempotente, así que llamarlo de más no duplica ni cuesta.
 */
public class RecordatorioReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Garantiza el canal aunque el proceso haya sido recién levantado por la alarma.
        NotificacionUtil.crearCanal(context);

        long numOrden = intent.getLongExtra("numOrden", 0);
        String mensaje = intent.getStringExtra("mensaje");

        if (mensaje == null) {
            mensaje = "Tienes una cita próxima: Orden #" + numOrden;
        }

        NotificacionUtil.mostrarNotificacion(
                context,
                (int) numOrden,
                "Recordatorio TaskSnap",
                mensaje
        );
    }
}