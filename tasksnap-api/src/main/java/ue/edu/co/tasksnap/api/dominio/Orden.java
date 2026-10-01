package ue.edu.co.tasksnap.api.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * ENTIDAD JPA: Orden (nucleo transaccional del Acta v5).
 * Espejo 1:1 de la entidad Room Orden del Android.
 * Traza: Requisito 2 y RN-1 a RN-8 (ServiPro S.A.S., 2026).
 *
 * Decisiones de diseno:
 *  - FKs (tecnicoId, servicioId, clienteId) como Long planos, NO @ManyToOne:
 *    mismo shape que Room, simplifica el SyncManager (Turno 7.4).
 *  - Fechas como String ISO 8601 (yyyy-MM-dd HH:mm:ss), igual que FormatoFecha
 *    en Android: sin conversiones ni desfases de zona horaria (RN-8).
 */
@Entity
@Table(name = "ordenes", indexes = {
    @Index(name = "idx_ordenes_tecnico", columnList = "tecnico_id"),
    @Index(name = "idx_ordenes_estado",  columnList = "estado"),
    @Index(name = "idx_ordenes_sync",    columnList = "sincronizado")
})
public class Orden {

    /** PK autogenerada por la BD (RN-5). El Android envia null al crear. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "num_orden")
    private Long numOrden;

    /** RN-4: la orden pertenece al tecnico que la ejecuta. */
    @Column(name = "tecnico_id", nullable = false)
    private Long tecnicoId;

    @Column(name = "servicio_id", nullable = false)
    private Long servicioId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    /** Detalle del trabajo (RF-04). */
    @Column(nullable = false, length = 500)
    private String descripcion;

    /** Fecha programada de la visita, ISO 8601 (RN-8). */
    @Column(name = "fecha_servicio", nullable = false)
    private String fechaServicio;

    /** RN-1: ciclo de vida limitado a PENDIENTE | COMPLETADA. */
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    /** Timestamp de creacion, lo fija el cliente (RN-8). */
    @Column(name = "fecha_creacion", nullable = false)
    private String fechaCreacion;

    /** RN-3: solo se llena al pasar a COMPLETADA; null mientras pendiente. */
    @Column(name = "fecha_cierre")
    private String fechaCierre;

    /** RN-7: flag de cola de sincronizacion. */
    @Column(nullable = false)
    private boolean sincronizado = false;

    /** RN-6: borrado logico, nunca DELETE fisico. */
    @Column(nullable = false)
    private boolean activo = true;

    /** Constructor vacio obligatorio para JPA/Hibernate. */
    public Orden() {
    }

    // --- Getters y setters: necesarios para que Jackson serialice el JSON ---

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