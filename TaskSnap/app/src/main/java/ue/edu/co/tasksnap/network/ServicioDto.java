package ue.edu.co.tasksnap.network;

import com.google.gson.annotations.SerializedName;

/**
 * DTO (Data Transfer Object) de Servicio para la capa de red (Fase 5).
 *
 * Rol: Representar EXACTAMENTE el JSON que produce GET /api/servicios del backend
 *      Spring Boot, sin contaminar el modelo de persistencia local (Room).
 * Traza: Objetivo específico 3 y tarea S5 "Integración app-API" (Acta v5,
 *        ServiPro S.A.S., 2026).
 *
 * Contrato v4.2 (congelado): el JSON del backend usa los nombres de atributo de la
 * entidad JPA serializados por Jackson -> {"id":..., "nombre":..., "activo":...}.
 * Cada campo se enlaza con @SerializedName para que un cambio futuro del backend
 * se absorba SOLO aqui (mitigacion del Riesgo 3 del Acta).
 *
 * Separacion DTO vs Entidad Room (decision de arquitectura):
 *  - ServicioDto = formato de TRANSPORTE (red). Paquete network.
 *  - Servicio    = formato de PERSISTENCIA (Room). Paquete data.local.entity.
 *  Convertir entre ambos es responsabilidad del repositorio, no de la UI (RFN-11).
 */
public class ServicioDto {

    /**
     * Clave primaria asignada por el backend (H2/PostgreSQL).
     * El JSON la trae como "id"; localmente la tratamos como servicioId.
     */
    @SerializedName("id")
    private long servicioId;

    /** Nombre del servicio, tal como lo muestra el spinner (spnTipoServicio). */
    @SerializedName("nombre")
    private String nombre;

    /** Flag de borrado logico RN-6 replicado desde el backend. */
    @SerializedName("activo")
    private boolean activo;

    /** Constructor vacio obligatorio para que Gson instancie el DTO por reflexion. */
    public ServicioDto() {
    }

    // --- Getters/setters: Gson lee/escribe por reflexion; la UI usa getters ---

    public long getServicioId() {
        return servicioId;
    }

    public void setServicioId(long servicioId) {
        this.servicioId = servicioId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}