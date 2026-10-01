package ue.edu.co.tasksnap.network;

import com.google.gson.annotations.SerializedName;

/**
 * DTO DE LECTURA de Cliente (Fase 5, Tarea A1).
 *
 * Rol: molde del JSON que el backend Spring Boot devuelve para un cliente
 *      (GET /api/clientes). Mismo patron que ServicioDto.
 *
 * Nota: el backend serializa el campo como "clienteId" (asi se llama el
 * atributo en la entidad JPA Cliente), no "id" como pasa con Servicio.
 */
public class ClienteDto {

    @SerializedName("clienteId")  private long clienteId;
    @SerializedName("nombre")     private String nombre;
    @SerializedName("direccion")  private String direccion;
    @SerializedName("telefono")   private String telefono;
    @SerializedName("activo")     private boolean activo;

    /** Constructor vacio obligatorio para Gson (instancia por reflexion). */
    public ClienteDto() {
    }

    public long getClienteId() { return clienteId; }
    public void setClienteId(long clienteId) { this.clienteId = clienteId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}