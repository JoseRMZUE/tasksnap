package ue.edu.co.tasksnap.api.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ue.edu.co.tasksnap.api.dominio.Orden;

/**
 * REPOSITORIO JPA: OrdenRepository
 *
 * Rol: puerta de acceso a la tabla "ordenes" del backend centralizado.
 * Traza: Requisito 2 y RN-1, RN-4, RN-6 del Acta v5 (ServiPro S.A.S., 2026).
 *
 * Equivalencia Android: mismo patron que OrdenDao en Room; aqui Spring Data
 * genera la implementacion completa del CRUD en tiempo de ejecucion.
 *
 * Consultas derivadas (Spring traduce el nombre del metodo a SQL):
 *  - findByActivoTrueOrderByFechaCreacionDesc -> listado fresco para operaciones.
 *  - findByTecnicoIdAndActivoTrue             -> ordenes de un tecnico (RN-4).
 *  - findByEstadoAndActivoTrue                -> monitoreo por estado (RN-1).
 * Todas ignoran filas con activo=false: borrado logico aplicado en lectura (RN-6).
 */
@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {

    /** Listado general: activas, mas recientes primero. */
    List<Orden> findByActivoTrueOrderByFechaCreacionDesc();

    /** Ordenes activas de un tecnico especifico. */
    List<Orden> findByTecnicoIdAndActivoTrue(Long tecnicoId);

    /** Ordenes activas en un estado dado ("PENDIENTE" o "COMPLETADA"). */
    List<Orden> findByEstadoAndActivoTrue(String estado);
}