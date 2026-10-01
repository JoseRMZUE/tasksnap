package ue.edu.co.tasksnap.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entidad que representa la tabla "clientes" en la base de datos local (Room).
 *
 * Esta tabla almacena los clientes que ServiPro S.A.S. atiende mediante sus
 * órdenes de trabajo. El técnico en campo selecciona un cliente al crear una
 * orden desde el spinner (spnCliente) del formulario principal.
 *
 * Rol en la arquitectura:
 *  - Fuente de verdad: API Spring Boot + PostgreSQL (CRUD completo remoto, RF-18).
 *  - Rol local: caché de solo lectura para alimentar el spnCliente y permitir
 *    crear órdenes sin conexión a internet (RF-17, RFN-10).
 *
 * Reglas de negocio aplicadas:
 *  - RN-9: todo cliente debe tener nombre, dirección y teléfono registrados;
 *    son la base del despacho y contacto del técnico en campo.
 *  - RN-6: borrado lógico mediante el atributo "activo" (1 = activo, 0 = dado de baja).
 *
 * Convenciones del proyecto:
 *  - RN-Técnica-01: al crear un cliente nuevo desde la UI, pase 0L como clienteId
 *    en el constructor parametrizado; Room lo autogenera al insertar.
 *  - RFN-13: el esquema local es equivalente 1:1 con el de PostgreSQL.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Entity(tableName = "clientes")
public class Cliente {

    // --- ATRIBUTOS (mapeados 1:1 con la tabla "clientes" del modelo E-R v4.1) ---

    /**
     * Clave primaria autogenerada de la tabla.
     * Equivalente al SERIAL de PostgreSQL.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "cliente_id")
    private long clienteId;

    /**
     * Nombre completo o razón social del cliente.
     * Obligatorio según RN-9: toda orden necesita saber a quién atiende.
     */
    @NonNull
    @ColumnInfo(name = "nombre")
    private String nombre = "";

    /**
     * Dirección del cliente: destino físico del despacho del técnico.
     * Obligatorio según RN-9: sin dirección, la orden no es ejecutable.
     */
    @NonNull
    @ColumnInfo(name = "direccion")
    private String direccion = "";

    /**
     * Teléfono de contacto del cliente.
     * Obligatorio según RN-9: el técnico en campo necesita comunicarse.
     */
    @NonNull
    @ColumnInfo(name = "telefono")
    private String telefono = "";

    /**
     * Indicador de borrado lógico (RN-6).
     * true (1) = activo (visible en el spnCliente), false (0) = dado de baja.
     */
    @ColumnInfo(name = "activo", defaultValue = "1")
    private boolean activo = true;

    // --- CONSTRUCTORES ---

    /**
     * Constructor vacío, obligatorio para Room.
     * Room lo utiliza mediante reflexión al leer filas de la tabla y convertirlos en objetos.
     */
    public Cliente() {
        // Constructor vacío: Room lo usa para leer filas de la tabla
    }

    /**
     * Constructor con parámetros para objetos ya inicializados.
     *
     * @param clienteId identificador único; pase 0L para registros locales nuevos
     *                  (Room lo autogenera al insertar), o el ID real para
     *                  actualizaciones y para la caché sincronizada desde la API.
     * @param nombre    nombre del cliente (no nulo, RN-9).
     * @param direccion dirección del cliente (no nulo, RN-9).
     * @param telefono  teléfono del cliente (no nulo, RN-9).
     * @param activo    estado lógico del registro.
     */
    @Ignore
    public Cliente(long clienteId, @NonNull String nombre, @NonNull String direccion,
                   @NonNull String telefono, boolean activo) {
        this.clienteId = clienteId;
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
        this.activo = activo;
    }

    // --- GETTERS Y SETTERS ---

    /** @return el identificador único del cliente. */
    public long getClienteId() {
        return clienteId;
    }

    /** @param clienteId identificador a asignar. */
    public void setClienteId(long clienteId) {
        this.clienteId = clienteId;
    }

    /** @return el nombre del cliente (nunca nulo). */
    public String getNombre() {
        return nombre;
    }

    /** @param nombre nombre a asignar (no nulo). */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** @return la dirección del cliente (nunca nulo). */
    public String getDireccion() {
        return direccion;
    }

    /** @param direccion dirección a asignar (no nulo). */
    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    /** @return el teléfono del cliente (nunca nulo). */
    public String getTelefono() {
        return telefono;
    }

    /** @param telefono teléfono a asignar (no nulo). */
    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    /** @return true si el cliente está activo, false si fue dado de baja. */
    public boolean isActivo() {
        return activo;
    }

    /** @param activo estado lógico a asignar. */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }


    @Override
    public String toString() {
        return nombre;
    }
}
