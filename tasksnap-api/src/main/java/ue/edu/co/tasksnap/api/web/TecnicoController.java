package ue.edu.co.tasksnap.api.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ue.edu.co.tasksnap.api.dominio.Tecnico;
import ue.edu.co.tasksnap.api.repositorio.TecnicoRepository;

/**
 * CONTROLADOR REST: TecnicoController.
 *
 * A diferencia de Cliente y Servicio, este controlador es SOLO LECTURA
 * a proposito: no expone POST/PUT/DELETE porque la gestion de cuentas
 * de tecnico (crear, cambiar contraseña) no es parte del alcance v1
 * (ver Parte 9 del Acta - "Fuera de alcance"). Las dos operaciones GET
 * existen unicamente para que el equipo de operaciones pueda consultar
 * el catalogo centralizado de tecnicos.
 *
 * Seguridad: passwordHash y salt nunca aparecen en el JSON de salida
 * (marcados @JsonIgnore en la entidad, Tecnico.java) - ningun endpoint
 * de esta clase puede filtrarlos, sin importar qué se agregue despues.
 */
@RestController
@RequestMapping("/api/tecnicos")
public class TecnicoController {

    private final TecnicoRepository repo;

    public TecnicoController(TecnicoRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Tecnico> listarActivos() {
        return repo.findByActivoTrueOrderByNombreAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tecnico> obtener(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}