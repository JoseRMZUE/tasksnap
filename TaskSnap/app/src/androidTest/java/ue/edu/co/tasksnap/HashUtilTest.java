package ue.edu.co.tasksnap;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ue.edu.co.tasksnap.util.HashUtil;

/**
 * Prueba unitaria pura de HashUtil (RFN-05).
 *
 * Vive en src/test porque HashUtil NO depende de Android: es lógica Java
 * (MessageDigest + SecureRandom), por lo que corre en la JVM sin emulador.
 *
 * Qué blinda:
 *  - Determinismo del hash (misma entrada -> mismo hash).
 *  - Unicidad del salt por llamada (SecureRandom, no Random).
 *  - Que el hash NUNCA revele el texto plano (defensa básica).
 *  - Que password + salt produzcan un hash distinto al de password solo
 *    (razón de ser del salt frente a tablas arcoíris).
 */
public class HashUtilTest {

    /** El hash SHA-256 en hexadecimal siempre mide 64 caracteres. */
    @Test
    public void sha256_devuelveHexDe64Caracteres() {
        String hash = HashUtil.sha256("cualquier texto");
        assertNotNull(hash);
        assertTrue("SHA-256 hex debe medir 64", hash.length() == 64);
    }

    /** Misma entrada -> mismo hash: condición para poder comparar en el login. */
    @Test
    public void sha256_esDeterminista() {
        String h1 = HashUtil.sha256("1234");
        String h2 = HashUtil.sha256("1234");
        assertTrue("El hash debe ser reproducible", h1.equals(h2));
    }

    /** Entradas distintas -> hashes distintos (propiedad de avalancha esperada). */
    @Test
    public void sha256_diferentesEntradas_diferentesHashes() {
        String h1 = HashUtil.sha256("1234");
        String h2 = HashUtil.sha256("1235");
        assertNotEquals("Un bit de diferencia debe cambiar el hash", h1, h2);
    }

    /** El salt debe ser único en cada llamada (SecureRandom, no predecible). */
    @Test
    public void generarSalt_esUnicoPorLlamada() {
        String s1 = HashUtil.generarSalt();
        String s2 = HashUtil.generarSalt();
        assertNotEquals("Dos salts seguidos no pueden coincidir", s1, s2);
        assertTrue("Salt hex de 16 bytes = 32 caracteres", s1.length() == 32);
    }

    /** hashConSalt debe equivaler a sha256(password + salt): contrato del login. */
    @Test
    public void hashConSalt_equivale_a_concatenarPasswordYSalt() {
        String salt = HashUtil.generarSalt();
        String esperado = HashUtil.sha256("1234" + salt);
        String real = HashUtil.hashConSalt("1234", salt);
        assertTrue("El repositorio comparará contra esta fórmula", esperado.equals(real));
    }

    /** Misma contraseña con distinto salt -> distinto hash (utilidad del salt). */
    @Test
    public void hashConSalt_mismaPasswordDistintoSalt_distintoHash() {
        String h1 = HashUtil.hashConSalt("1234", HashUtil.generarSalt());
        String h2 = HashUtil.hashConSalt("1234", HashUtil.generarSalt());
        assertNotEquals("El salt debe separar hashes de la misma clave", h1, h2);
    }

    /** El hash almacenado NUNCA contiene la contraseña en texto plano. */
    @Test
    public void hashConSalt_noExponeTextoPlano() {
        String hash = HashUtil.hashConSalt("1234", HashUtil.generarSalt());
        assertTrue("El hash no debe revelar la clave", !hash.contains("1234"));
    }
}