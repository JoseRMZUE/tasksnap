package ue.edu.co.tasksnap.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilidad de fechas del proyecto (RN-8: formato ISO 8601 en TEXT).
 *
 * Centraliza el formato para que ninguna clase invente su propio patrón:
 *  - fechas de día (fecha_servicio):            yyyy-MM-dd
 *  - fechas con hora (creacion, cierre, captura): yyyy-MM-dd'T'HH:mm:ss
 *
 * Usa java.time (no SimpleDateFormat) por dos razones:
 *  - Es inmune a errores de hilo: SimpleDateFormat no es thread-safe y nuestros
 *    repositorios correrán en hilos secundarios.
 *  - java.time existe nativo desde Android 8.0 (API 26), que es exactamente
 *    nuestro mínimo soportado (RFN-08).
 */
public final class FormatoFecha {

    /** Patrón con hora alineado a ISO 8601 para auditoría. */
    private static final DateTimeFormatter FORMATO_COMPLETO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Constructor privado: clase de utilidades, no debe instanciarse. */
    private FormatoFecha() {
    }

    /**
     * Fecha de hoy en formato de día ISO 8601.
     * LocalDate.toString() ya produce yyyy-MM-dd por especificación ISO.
     *
     * @return fecha actual como "2026-09-22".
     */
    public static String hoy() {
        return LocalDate.now().toString();
    }

    /**
     * Fecha y hora actuales en formato ISO 8601 con hora.
     * Se usa para fecha_creacion, fecha_cierre (RN-3) y fecha_captura (RN-8).
     *
     * @return marca de tiempo como "2026-09-22T14:35:07".
     */
    public static String ahora() {
        return LocalDateTime.now().format(FORMATO_COMPLETO);
    }
}