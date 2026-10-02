package ue.edu.co.tasksnap.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import ue.edu.co.tasksnap.receiver.RecordatorioReceiver;

/**
 * Programador de notificaciones de recordatorio (RF-13).
 *
 * Responsabilidades:
 *  - Programar una notificación 1 hora antes de la fecha_servicio de una orden.
 *  - Cancelar recordatorios si la orden se da de baja o se completa antes.
 *
 * Decisión de diseño: usa AlarmManager (no WorkManager) porque los recordatorios
 * deben dispararse en un momento exacto, no "aproximadamente". WorkManager es
 * para tareas diferibles; AlarmManager es para precisión.
 *
 * Compatibilidad: desde Android 12 (API 31) las alarmas exactas requieren el
 * permiso de alarmas exactas; si no está concedido, este programador cae a
 * alarma inexacta para evitar SecurityException en dispositivos modernos.
 */
public final class RecordatorioScheduler {

    /** Milisegundos en 1 hora: para programar el recordatorio 1h antes. */
    private static final long UN_HORA_MS = 60 * 60 * 1000L;

    /** Formato ISO 8601 para parsear fecha_servicio (yyyy-MM-dd, RN-8). */
    private static final String FORMATO_FECHA = "yyyy-MM-dd";

    /** Constructor privado: clase de utilidades, no debe instanciarse. */
    private RecordatorioScheduler() {
    }

    /**
     * Programa una notificación de recordatorio 1 hora antes de la cita.
     *
     * @param context       contexto para obtener AlarmManager.
     * @param numOrden      número de orden (se usa como ID del PendingIntent).
     * @param fechaServicio fecha del servicio en formato ISO 8601 (yyyy-MM-dd).
     * @param horaServicio  hora del servicio en formato HH:mm (ej: "14:30").
     */
    public static void programarRecordatorio(Context context, long numOrden,
                                             String fechaServicio, String horaServicio) {
        try {
            SimpleDateFormat formatoFecha = new SimpleDateFormat(FORMATO_FECHA, Locale.US);
            Date fecha = formatoFecha.parse(fechaServicio);
            if (fecha == null) {
                Log.i("RECORDATORIO", "Fecha nula tras parsear: " + fechaServicio);
                return;
            }

            Calendar calendario = Calendar.getInstance();
            calendario.setTime(fecha);

            String[] partes = horaServicio.split(":");
            if (partes.length != 2) {
                return;
            }
            calendario.set(Calendar.HOUR_OF_DAY, Integer.parseInt(partes[0]));
            calendario.set(Calendar.MINUTE, Integer.parseInt(partes[1]));
            calendario.set(Calendar.SECOND, 0);
            calendario.set(Calendar.MILLISECOND, 0);

            // Restar 1 hora para el recordatorio
            long tiempoRecordatorio = calendario.getTimeInMillis() - UN_HORA_MS;

            Log.i("RECORDATORIO", "numOrden=" + numOrden
                    + " horaServicio(ms)=" + calendario.getTimeInMillis()
                    + " tiempoRecordatorio(ms)=" + tiempoRecordatorio
                    + " ahora(ms)=" + System.currentTimeMillis()
                    + " diferenciaMin=" + ((tiempoRecordatorio - System.currentTimeMillis()) / 60000));

            // Si el momento ya pasó, no programar nada
            if (tiempoRecordatorio < System.currentTimeMillis()) {
                Log.i("RECORDATORIO", "DESCARTADA: el momento ya paso");
                return;
            }

            Intent intent = new Intent(context, RecordatorioReceiver.class);
            intent.putExtra("numOrden", numOrden);
            intent.putExtra("mensaje", "Tienes una cita en 1 hora: Orden #" + numOrden);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) numOrden,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.i("RECORDATORIO", "AlarmManager es null");
                return;
            }

            // Android 12+: sin permiso de alarmas exactas, caer a alarma inexacta
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !alarmManager.canScheduleExactAlarms()) {
                Log.i("RECORDATORIO", "Programando INEXACTA (sin permiso de alarma exacta)");
                alarmManager.set(AlarmManager.RTC_WAKEUP, tiempoRecordatorio, pendingIntent);
            } else {
                Log.i("RECORDATORIO", "Programando EXACTA");
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, tiempoRecordatorio, pendingIntent);
            }

        } catch (ParseException e) {
            Log.i("RECORDATORIO", "ParseException: " + e.getMessage());
        }
    }

    /**
     * Cancela el recordatorio programado para una orden (si se completa o da de baja).
     *
     * @param context  contexto para obtener AlarmManager.
     * @param numOrden número de orden cuyo recordatorio se cancela.
     */
    public static void cancelarRecordatorio(Context context, long numOrden) {
        Intent intent = new Intent(context, RecordatorioReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) numOrden,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}