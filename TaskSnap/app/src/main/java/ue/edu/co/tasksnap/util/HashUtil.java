package ue.edu.co.tasksnap.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Utilidad de seguridad criptográfica del proyecto (RFN-05).
 *
 * Responsabilidades:
 *  - Generar un salt aleatorio criptográficamente seguro por usuario.
 *  - Calcular hashes SHA-256 en hexadecimal (el formato que almacena la tabla
 *    tecnicos en password_hash).
 *
 * Regla de oro del proyecto: ninguna clase fuera de esta utilidades y del
 * AuthRepository (Fase 3) toca contraseñas; aquí nunca entra ni sale texto plano
 * de almacenamiento, solo el hash resultante.
 *
 * @see <a href="https://developer.android.com/reference/java/security/MessageDigest">MessageDigest</a>
 */
public final class HashUtil {

    /** Constructor privado: clase de utilidades, no debe instanciarse. */
    private HashUtil() {
    }

    /**
     * Genera un salt aleatorio de 16 bytes en representación hexadecimal.
     * Se usa SecureRandom (no Random) porque Random es predecible y un salt
     * predecible anularía su propósito de defensa ante tablas arcoíris.
     *
     * @return salt hexadecimal de 32 caracteres, único por usuario.
     */
    public static String generarSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        return bytesHex(saltBytes);
    }

    /**
     * Calcula el hash SHA-256 de un texto y lo devuelve en hexadecimal.
     *
     * @param texto texto a hashear (en login será: contraseña + salt).
     * @return hash SHA-256 hexadecimal de 64 caracteres.
     */
    public static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            return bytesHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda plataforma Java/Android: si esto
            // fallara, sería un error de plataforma, no recuperable en negocio.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * Calcula el hash de una contraseña combinada con su salt:
     * hash = SHA-256(contraseña + salt). Es la operación exacta que usará el
     * login para comparar contra password_hash (RFN-05).
     *
     * @param textoPlano contraseña digitada por el usuario.
     * @param salt       salt almacenado del usuario.
     * @return hash resultante en hexadecimal.
     */
    public static String hashConSalt(String textoPlano, String salt) {
        return sha256(textoPlano + salt);
    }

    /**
     * Convierte un arreglo de bytes a su representación hexadecimal minúscula
     * (ej: byte 15 -> "0f"). Es el formato legible y comparable que guardamos.
     *
     * @param bytes bytes a convertir.
     * @return cadena hexadecimal.
     */
    private static String bytesHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}