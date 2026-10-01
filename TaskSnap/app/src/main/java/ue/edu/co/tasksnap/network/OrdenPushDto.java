package ue.edu.co.tasksnap.network;

import com.google.gson.annotations.SerializedName;

/**
 * DTO DE ESCRITURA de Orden (Fase 5, Turno 7.2).
 *
 * Rol: cuerpo JSON que Android envia a POST /api/ordenes para crear una orden
 *      en el backend centralizado (push de la RN-7).
 * Traza: RN-7 y tarea S5 "Integracion app-API" (Acta v5, ServiPro S.A.S., 2026).
 *
 * Omisiones INTENCIONALES (decision de arquitectura):
 *  - numOrden:     la PK la asigna la BD del servidor; el ID local de Room no viaja.
 *  - sincronizado: el controller lo fuerza a true al crear; la cola de sync
 *                  (sincronizado=0) solo existe del lado Android.
 */
public class OrdenPushDto {

    @SerializedName("tecnicoId")     private Long tecnicoId;
    @SerializedName("servicioId")    private Long servicioId;
    @SerializedName("clienteId")     private Long clienteId;
    @SerializedName("descripcion")   private String descripcion;
    @SerializedName("fechaServicio") private String fechaServicio;
    @SerializedName("estado")        private String estado;
    @SerializedName("fechaCreacion") private String fechaCreacion;
    @SerializedName("fechaCierre")   private String fechaCierre;
    @SerializedName("activo")        private boolean activo;

    /** Constructor vacio obligatorio para Gson al serializar el cuerpo. */
    public OrdenPushDto() {
    }

    public Long getTecnicoId() { return tecnicoId; }
    public void setTecnicoId(Long tecnicoId) { this.tecnicoId = tecnicoId; }

    public Long getServicioId() { return servicioId; }
    public void setServicioId(Long servicioId) { this.servicioId = servicioId; }

    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getFechaServicio() { return fechaServicio; }
    public void setFechaServicio(String fechaServicio) { this.fechaServicio = fechaServicio; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(String fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(String fechaCierre) { this.fechaCierre = fechaCierre; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}