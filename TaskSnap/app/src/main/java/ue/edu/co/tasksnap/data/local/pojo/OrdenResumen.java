package ue.edu.co.tasksnap.data.local.pojo;

/**
 * Proyección de lectura (POJO) para el listado de órdenes.
 *
 * NO es una tabla: es el resultado de un JOIN entre ordenes, servicios y
 * clientes, ya con los nombres resueltos para que la UI no maneje IDs sueltos
 * ni haga búsquedas en memoria (RFN-03: listado < 1 s con 500 órdenes).
 *
 * Regla de Room: los nombres de los campos deben coincidir EXACTAMENTE con los
 * alias de columna del @Query (AS numOrden, AS servicioNombre, ...).
 *
 * Uso: OrdenDao.listarResumenActivas() / buscarResumenPorNumero() ->
 *      OrdenRepository.listarResumen() / buscarResumen() -> OrdenAdapter.
 */
public class OrdenResumen {

    /** Número de orden (PK de ordenes), para mostrar y para operar al seleccionar. */
    private long numOrden;

    /** Nombre del tipo de servicio, resuelto por JOIN con servicios. */
    private String servicioNombre;

    /** Nombre del cliente, resuelto por JOIN con clientes. */
    private String clienteNombre;

    /** Estado de la orden: PENDIENTE o COMPLETADA (RN-1). */
    private String estado;

    /** Indicador de sincronización (RN-7): false = pendiente de push a la API. */
    private boolean sincronizado;

    /** @return número de orden. */
    public long getNumOrden() {
        return numOrden;
    }

    /** @param numOrden número de orden a asignar. */
    public void setNumOrden(long numOrden) {
        this.numOrden = numOrden;
    }

    /** @return nombre del tipo de servicio. */
    public String getServicioNombre() {
        return servicioNombre;
    }

    /** @param servicioNombre nombre a asignar. */
    public void setServicioNombre(String servicioNombre) {
        this.servicioNombre = servicioNombre;
    }

    /** @return nombre del cliente. */
    public String getClienteNombre() {
        return clienteNombre;
    }

    /** @param clienteNombre nombre a asignar. */
    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    /** @return estado de la orden (PENDIENTE o COMPLETADA). */
    public String getEstado() {
        return estado;
    }

    /** @param estado estado a asignar. */
    public void setEstado(String estado) {
        this.estado = estado;
    }

    /** @return true si la orden ya fue sincronizada con la API. */
    public boolean isSincronizado() {
        return sincronizado;
    }

    /** @param sincronizado indicador a asignar. */
    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }
}