package ue.edu.co.tasksnap.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import ue.edu.co.tasksnap.api.dominio.Cliente;
import ue.edu.co.tasksnap.api.dominio.Servicio;
import ue.edu.co.tasksnap.api.dominio.Tecnico;
import ue.edu.co.tasksnap.api.repositorio.ClienteRepository;
import ue.edu.co.tasksnap.api.repositorio.ServicioRepository;
import ue.edu.co.tasksnap.api.repositorio.TecnicoRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ServicioRepository servicioRepo;
    private final ClienteRepository clienteRepo;
    private final TecnicoRepository tecnicoRepo;

    public DataSeeder(ServicioRepository servicioRepo, ClienteRepository clienteRepo,
                       TecnicoRepository tecnicoRepo) {
        this.servicioRepo = servicioRepo;
        this.clienteRepo = clienteRepo;
        this.tecnicoRepo = tecnicoRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        long count = servicioRepo.count();

        if (count > 0) {
            log.info("✅ Seed omitido: la BD centralizada ya contiene datos maestros.");
            return;
        }

        log.info(" Iniciando seed de datos maestros en BD centralizada...");

        try {
            // --- Servicios (mismos nombres que el seed local de Android) ---
            servicioRepo.save(new Servicio("Plomería", true));
            servicioRepo.save(new Servicio("Electricidad", true));
            servicioRepo.save(new Servicio("Mantenimiento general", true));
            log.info("✅ Servicios sembrados: Plomería, Electricidad, Mantenimiento general.");

            // --- Clientes (mismos nombres que el seed local de Android) ---
            clienteRepo.save(new Cliente("Ana María Gómez", "Calle 45 #12-34", "3001234567", true));
            clienteRepo.save(new Cliente("Oficinas Andinas S.A.S.", "Av. El Dorado #100-20", "6018765432", true));
            log.info("✅ Clientes sembrados: Ana María Gómez, Oficinas Andinas S.A.S.");

            // --- Técnico de catálogo centralizado ---
            // NOTA: la autenticación del Componente 1 del Acta es LOCAL (Android,
            // SHA-256 + salt reales contra Room/SharedPreferences) - este registro
            // del backend es solo informativo, para que el equipo de operaciones
            // vea el catálogo de técnicos centralizado. passwordHash/salt aquí son
            // placeholders que NUNCA se validan contra ningún login real, y además
            // están marcados @JsonIgnore en la entidad: jamás viajan en el JSON.
            tecnicoRepo.save(new Tecnico("Técnico de Pruebas", "tecnico",
                    "NO_VALIDADO_AUTH_ES_LOCAL", "NO_VALIDADO_AUTH_ES_LOCAL", true));
            log.info("✅ Técnico sembrado: tecnico (catálogo informativo, auth real es local en Android).");

            log.info("✅ Seed completado exitosamente.");

        } catch (Exception e) {
            log.error("❌ Error crítico durante el seed de datos: {}", e.getMessage(), e);
            throw e;
        }
    }
}