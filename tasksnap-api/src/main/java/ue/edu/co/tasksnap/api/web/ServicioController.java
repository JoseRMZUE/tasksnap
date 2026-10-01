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

import ue.edu.co.tasksnap.api.dominio.Servicio;
import ue.edu.co.tasksnap.api.repositorio.ServicioRepository;

/**
 * CONTROLADOR REST: ServicioController
 * 
 * Rol: Punto de entrada HTTP para gestión de catálogos de servicios (RF-19).
 * Traza: Objetivo Específico 3 (Acta v5) -> API REST desplegada en Spring Boot.
 * Contrato Congelado v4.1: Los nombres de campo JSON deben coincidir exactamente
 *                          con los campos de la entidad Room en Android (snake_case).
 * 
 * ARQUITECTURA:
 * Este controlador NO contiene lógica de negocio compleja. Solo orquesta llamadas
 * al repositorio y transforma resultados a ResponseEntity (códigos HTTP + cuerpo JSON).
 * La regla RN-6 (borrado lógico) se aplica aquí explícitamente en el método DELETE.
 */
@RestController
@RequestMapping("/api/servicios") // Prefijo común para todos los endpoints de esta clase
public class ServicioController {

    private final ServicioRepository repo;

    /**
     * Inyección de dependencias vía constructor (patrón recomendado en Spring).
     * Facilita testing unitario mockeando el repositorio si fuera necesario.
     */
    public ServicioController(ServicioRepository repo) {
        this.repo = repo;
    }

    /**
     * GET /api/servicios
     * Lista todos los servicios ACTIVOS ordenados alfabéticamente.
     * Usado por MainActivity para poblar spnTipoServicio vía Retrofit.
     * 
     * @return Lista JSON [{id:1, nombre:"Plomería", activo:true}, ...]
     */
    @GetMapping
    public List<Servicio> listarActivos() {
        return repo.findByActivoTrueOrderByNombreAsc();
    }

    /**
     * GET /api/servicios/{id}
     * Obtiene detalle de un servicio específico por su ID.
     * Útil para pantallas de edición o validación puntual.
     * 
     * @param id Identificador único del servicio.
     * @return Objeto Servicio o 404 Not Found si no existe.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Servicio> obtener(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)           // Si existe -> 200 OK + cuerpo
                .orElse(ResponseEntity.notFound().build()); // Si no -> 404
    }

    /**
     * POST /api/servicios
     * Crea un nuevo servicio (alta catálogo RF-19).
     * El cliente envía JSON {"nombre":"Pintura","activo":true}.
     * La BD asigna el ID autoincremental automáticamente.
     * 
     * @param servicio Datos recibidos en el cuerpo de la petición.
     * @return Servicio creado con ID asignado + código 201 Created.
     */
    @PostMapping
    public ResponseEntity<Servicio> crear(@RequestBody Servicio servicio) {
        servicio.setId(null); // Forzar generación automática de PK por parte de la BD
        Servicio guardado = repo.save(servicio);
        return ResponseEntity.status(201).body(guardado);
    }

    /**
     * PUT /api/servicios/{id}
     * Actualiza parcialmente o totalmente un servicio existente.
     * Solo modifica campos presentes en el cuerpo JSON recibido.
     * 
     * @param id     ID del servicio a modificar.
     * @param cuerpo Nuevos valores parciales/totales.
     * @return Servicio actualizado o 404 si no existe.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Servicio> editar(@PathVariable Long id,
                                           @RequestBody Servicio cuerpo) {
        return repo.findById(id).map(existente -> {
            existente.setNombre(cuerpo.getNombre());
            existente.setActivo(cuerpo.isActivo());
            return ResponseEntity.ok(repo.save(existente));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/servicios/{id}
     * Da de baja lógicamente un servicio (RN-6).
     * NUNCA borra físicamente la fila para preservar integridad referencial
     * con órdenes históricas que aún apuntan a este servicio_id.
     * 
     * Implementación: Cambia flag 'activo' a false y guarda cambios.
     * 
     * @param id ID del servicio a dar de baja.
     * @return 204 No Content si éxito, 404 si no existe.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarLogico(@PathVariable Long id) {
        return repo.findById(id).map(existente -> {
            existente.setActivo(false); // RN-6: Borrado lógico, no físico
            repo.save(existente);
            return ResponseEntity.noContent().<Void>build(); // 204 indica éxito sin cuerpo
        }).orElse(ResponseEntity.notFound().build());
    }
}