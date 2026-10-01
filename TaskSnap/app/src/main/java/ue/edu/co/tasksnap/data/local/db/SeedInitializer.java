package ue.edu.co.tasksnap.data.local.db;

import android.util.Log;

import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Cliente;
import ue.edu.co.tasksnap.data.local.entity.Servicio;
import ue.edu.co.tasksnap.data.local.entity.Tecnico;
import ue.edu.co.tasksnap.util.HashUtil;

/**
 * Inicializador idempotente de datos maestros (Lección 4).
 *
 * PROPÓSITO: Garantizar que la app nazca utilizable sin conexión a API.
 * Inserta un técnico de pruebas, servicios básicos y clientes iniciales SOLO si las tablas están vacías.
 *
 * SEGURIDAD: La contraseña "1234" NO se guarda en texto plano. Se genera un salt aleatorio
 * real y se calcula el hash SHA-256(password + salt) usando HashUtil, igual que hará el login.
 */
public final class SeedInitializer {

    private static final String TAG = "TASKSNAP_DB";

    /** Contraseña de prueba para el técnico sembrado. */
    private static final String PASSWORD_PRUEBA = "1234";

    private SeedInitializer() {}

    /**
     * Ejecuta el seeding si y solo si las tablas maestro están vacías.
     * Es seguro llamarlo múltiples veces (idempotencia).
     *
     * @param db Instancia activa de AppDatabase.
     */
    public static void sembrarSiVacio(AppDatabase db) {
        // 1. Verificar si ya hay técnicos activos
        List<Tecnico> tecnicosExistentes = db.tecnicoDao().listarActivos();

        if (tecnicosExistentes != null && !tecnicosExistentes.isEmpty()) {
            Log.i(TAG, "Seed omitido: Ya existen técnicos registrados.");
            return;
        }

        Log.i(TAG, "Iniciando Seed de datos maestros...");

        try {
            // --- SEMBRAR TÉCNICO DE PRUEBAS ---
            String salt = HashUtil.generarSalt();
            String passwordHash = HashUtil.hashConSalt(PASSWORD_PRUEBA, salt);

            Tecnico tecnicoPruebas = new Tecnico(
                    0L,                       // ID autogenerado por Room
                    "Técnico de Pruebas",     // Nombre visible
                    "tecnico",                // USUARIO EXACTO PARA LOGIN
                    passwordHash,             // Hash calculado con salt real
                    salt,                     // Salt almacenado para futuras validaciones
                    true                      // Activo (RN-6)
            );
            long idTec = db.tecnicoDao().insert(tecnicoPruebas);
            Log.i(TAG, "Técnico sembrado con ID: " + idTec);

            // --- SEMBRAR SERVICIOS BÁSICOS ---
            Servicio s1 = new Servicio(0L, "Plomería", true);
            Servicio s2 = new Servicio(0L, "Electricidad", true);
            Servicio s3 = new Servicio(0L, "Mantenimiento general", true);
            db.servicioDao().insert(s1);
            db.servicioDao().insert(s2);
            db.servicioDao().insert(s3);
            Log.i(TAG, "Servicios sembrados: Plomería, Electricidad, Mantenimiento.");

            // --- SEMBRAR CLIENTES INICIALES ---
            Cliente c1 = new Cliente(0L, "Ana María Gómez", "Calle 45 #12-34", "3001234567", true);
            Cliente c2 = new Cliente(0L, "Oficinas Andinas S.A.S.", "Av. El Dorado #100-20", "6018765432", true);
            db.clienteDao().insert(c1);
            db.clienteDao().insert(c2);
            Log.i(TAG, "Clientes sembrados: Ana María Gómez, Oficinas Andinas.");

            Log.i(TAG, "✅ Seed completado exitosamente.");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error crítico durante el Seed: " + e.getMessage(), e);
        }
    }
}