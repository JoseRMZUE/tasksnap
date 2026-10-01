package ue.edu.co.tasksnap.api.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import ue.edu.co.tasksnap.api.dominio.Servicio;

/**
 * REPOSITORIO JPA: ServicioRepository
 * 
 * Rol: Interfaz de acceso a datos para la entidad 'Servicio'.
 * Traza: Objetivo Específico 3 (Acta v5) -> Persistencia centralizada.
 * Equivalencia Android: Esta interfaz reemplaza a tu 'ServicioDao' local.
 * 
 * MAGIA DE SPRING DATA:
 * Al extender JpaRepository<Servicio, Long>, Spring genera automáticamente 
 * TODAS las operaciones CRUD básicas sin escribir una sola línea de implementación:
 * - save(entidad)       -> INSERT o UPDATE inteligente.
 * - findById(id)        -> SELECT por PK.
 * - findAll()           -> SELECT completo.
 * - deleteById(id)      -> DELETE físico (NO LO USAREMOS DIRECTAMENTE por RN-6).
 * - count()             -> COUNT(*).
 * 
 * MÉTODO PERSONALIZADO (Derived Query):
 * findByActivoTrueOrderByNombreAsc(): Spring lee el nombre del método y arma el SQL solo.
 * Traducción literal: "Encuentra todos los Servicios donde activo sea TRUE, ordenados por Nombre Ascendente".
 * Esto equivale a tu query manual en Room: SELECT * FROM servicios WHERE activo=1 ORDER BY nombre ASC.
 */
@Repository // Anotación opcional pero recomendada para claridad semántica
public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    /**
     * Consulta derivada personalizada para poblar spinners/catálogos activos.
     * Ignora servicios dados de baja (activo=false) cumpliendo la regla RN-6 implícitamente en la lectura.
     * Orden alfabético facilita la búsqueda visual en la UI móvil.
     *
     * @return Lista inmutable de servicios activos ordenados alfabéticamente.
     */
    List<Servicio> findByActivoTrueOrderByNombreAsc();
}