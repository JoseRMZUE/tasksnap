package ue.edu.co.tasksnap.network;

import com.google.gson.annotations.SerializedName;

/**
 * DTO DE LECTURA de Orden (Fase 5, Turno 7.2).
 *
 * Rol: molde del JSON que el backend Spring Boot devuelve para una orden
 *      (GET /api/ordenes y respuesta 201 del POST /api/ordenes).
 * Traza: RN-7 y tarea S5 "Integracion app-API" (Acta v5, ServiPro S.A.S., 2026).
 *
 * Contrato v4.2: los nombres de @SerializedName coinciden con los atributos de la
 * entidad JPA Orden del backend (Jackson serializa tal cual).
 *
 * Uso critico: el SyncManager lee getNumOrden() de este DTO para guardar el ID
 * remoto en la fila local tras un push exitoso.
 */
public class OrdenDto {

    @SerializedName("numOrden")      private Long numOrden;
    @SerializedName("tecnicoId")     private Long tecnicoId;
    @SerializedName("servicioId")    private Long servicioId;
    @SerializedName("clienteId")     private Long clienteId;
    @SerializedName("descripcion")   private String descripcion;
    @SerializedName("fechaServicio") private String fechaServicio;
    @SerializedName("estado")        private String estado;
    @SerializedName("fechaCreacion") private String fechaCreacion;
    @SerializedName("fechaCierre")   private String fechaCierre;   // null mientras PENDIENTE (RN-3)
    @SerializedName("sincronizado")  private boolean sincronizado;
    @SerializedName("activo")        private boolean activo;

    /** Constructor vacio obligatorio para Gson (instancia por reflexion). */
    public OrdenDto() {
    }

    public Long getNumOrden() { return numOrden; }
    public void setNumOrden(Long numOrden) { this.numOrden = numOrden; }

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

    public boolean isSincronizado() { return sincronizado; }
    public void setSincronizado(boolean sincronizado) { this.sincronizado = sincronizado; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}