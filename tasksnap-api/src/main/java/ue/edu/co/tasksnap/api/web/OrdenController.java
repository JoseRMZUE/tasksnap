package ue.edu.co.tasksnap.api.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ue.edu.co.tasksnap.api.dominio.Orden;
import ue.edu.co.tasksnap.api.repositorio.OrdenRepository;

/**
 * CONTROLADOR REST: OrdenController
 *
 * Rol: punto de entrada HTTP para gestion de ordenes de trabajo (Req. 2, RN-1..8).
 * Traza: Objetivo especifico 3 y tarea S5 "Integracion app-API" (Acta v5,
 *        ServiPro S.A.S., 2026).
 *
 * Contrato v4.2 (congelado): los nombres de campo JSON coinciden con los atributos
 * de la entidad JPA. Jackson serializa tal cual; el cliente Android (Retrofit + Gson)
 * deserializa con @SerializedName en los DTOs (Turno 7.2).
 *
 * Endpoints expuestos:
 *  - GET    /api/ordenes       -> listar activas (mas recientes primero)
 *  - GET    /api/ordenes/{id}  -> detalle por ID
 *  - POST   /api/ordenes       -> crear (el cliente envia numOrden=null; la BD asigna PK)
 *  - PUT    /api/ordenes/{id}  -> actualizar (ej: cambio de estado PENDIENTE->COMPLETADA)
 *  - DELETE /api/ordenes/{id}  -> borrado LOGICO (activo=false, RN-6)
 *
 * Decisiones de diseno:
 *  - Inyeccion por constructor (patron recomendado en Spring moderno).
 *  - ResponseEntity para control total de codigos HTTP (201 Created, 204 No Content).
 *  - Borrado logico explicito: nunca DELETE FROM, siempre UPDATE activo=false.
 */
@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    private final OrdenRepository repo;

    public OrdenController(OrdenRepository repo) {
        this.repo = repo;
    }

    /**
     * GET /api/ordenes
     * Lista todas las ordenes activas, mas recientes primero.
     * Usado por el area de operaciones de ServiPro para monitoreo (Acta v5).
     */
    @GetMapping
    public List<Orden> listarActivas() {
        return repo.findByActivoTrueOrderByFechaCreacionDesc();
    }

    /**
     * GET /api/ordenes/{id}
     * Obtiene detalle de una orden especifica por su numOrden.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Orden> obtener(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/ordenes
     * Crea una nueva orden (push desde Android, RN-7).
     * El cliente envia numOrden=null; la BD asigna la PK auto-generada.
     * Devuelve 201 Created con la orden ya identificada.
     *
     * Flujo tipico:
     *  1. Android crea orden local con sincronizado=0.
     *  2. SyncManager envia POST /api/ordenes con el DTO (sin numOrden).
     *  3. Backend asigna PK, guarda, responde 201 con numOrden real.
     *  4. Android marca sincronizado=1 y guarda numOrdenRemoto.
     */
    @PostMapping
    public ResponseEntity<Orden> crear(@RequestBody Orden orden) {
        orden.setNumOrden(null);        // la BD asigna la PK
        orden.setSincronizado(true);    // al subir, queda sincronizada
        Orden guardada = repo.save(orden);
        return ResponseEntity.status(201).body(guardada);
    }

    /**
     * PUT /api/ordenes/{id}
     * Actualiza una orden existente (ej: cambio de estado PENDIENTE->COMPLETADA, RN-1..3).
     * Solo modifica los campos presentes en el cuerpo JSON recibido.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Orden> editar(@PathVariable Long id, @RequestBody Orden cuerpo) {
        return repo.findById(id).map(existente -> {
            existente.setDescripcion(cuerpo.getDescripcion());
            existente.setFechaServicio(cuerpo.getFechaServicio());
            existente.setEstado(cuerpo.getEstado());
            existente.setFechaCierre(cuerpo.getFechaCierre());
            existente.setSincronizado(true);
            return ResponseEntity.ok(repo.save(existente));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/ordenes/{id}
     * Borrado LOGICO (RN-6): marca activo=false, nunca borra fisicamente.
     * Preserva integridad referencial con evidencias y auditorias historicas.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarLogico(@PathVariable Long id) {
        return repo.findById(id).map(existente -> {
            existente.setActivo(false);
            repo.save(existente);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}