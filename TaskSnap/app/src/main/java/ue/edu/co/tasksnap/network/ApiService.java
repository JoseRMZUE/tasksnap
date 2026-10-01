package ue.edu.co.tasksnap.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * INTERFAZ RETROFIT: ApiService
 *
 * Rol: declarar los endpoints HTTP que la app Android consume del backend
 *      Spring Boot. Retrofit genera la implementacion en tiempo de ejecucion.
 * Traza: Objetivo especifico 3, RN-7 y tarea S5 "Integracion app-API"
 *        (Acta v5, ServiPro S.A.S., 2026).
 *
 * Contrato v4.2 (congelado): las rutas y nombres de campo JSON coinciden con los
 * controllers y entidades JPA del backend. Los DTOs absorben cualquier desviacion.
 */
public interface ApiService {

    // ===== LECTURA DE CATALOGOS (Turno 6, ya verificado en dispositivo) =====

    /**
     * GET /api/servicios
     * Lista servicios activos para poblar spnTipoServicio.
     * Verificado end-to-end: 200 OK + JSON en Samsung SM-S901E.
     */
    @GET("/api/servicios")
    Call<List<ServicioDto>> obtenerServicios();

    @POST("/api/servicios")
    Call<ServicioDto> crearServicio(@Body ServicioDto servicio);

    @PUT("/api/servicios/{id}")
    Call<ServicioDto> actualizarServicio(@Path("id") long id, @Body ServicioDto servicio);

    @DELETE("/api/servicios/{id}")
    Call<Void> eliminarServicio(@Path("id") long id);

// ===== LECTURA DE CLIENTES (Turno A1) =====

    /**
     * GET /api/clientes
     * Lista clientes activos para poblar spnCliente.
     */
    @GET("/api/clientes")
    Call<List<ClienteDto>> obtenerClientes();

    @POST("/api/clientes")
    Call<ClienteDto> crearCliente(@Body ClienteDto cliente);

    @PUT("/api/clientes/{id}")
    Call<ClienteDto> actualizarCliente(@Path("id") long id, @Body ClienteDto cliente);

    @DELETE("/api/clientes/{id}")
    Call<Void> eliminarCliente(@Path("id") long id);

    // ===== ESCRITURA DE ORDENES: push de la RN-7 (Turno 7.2) =====

    /**
     * POST /api/ordenes
     * Crea una orden en el servidor (primer push de una orden local).
     *
     * @Body: Gson serializa el OrdenPushDto al cuerpo JSON de la peticion.
     *        El DTO omite numOrden a proposito: la PK la asigna el servidor.
     * @return la orden creada (201) con su numOrden real, para que el
     *         SyncManager lo guarde como num_orden_remoto en Room.
     */
    @POST("/api/ordenes")
    Call<OrdenDto> crearOrden(@Body OrdenPushDto orden);

    /**
     * PUT /api/ordenes/{numOrden}
     * Actualiza una orden que YA existe en el servidor
     * (ej. cambio de estado PENDIENTE -> COMPLETADA, RN-1 a RN-3).
     *
     * @Path: inyecta el num_orden_remoto local en el placeholder {numOrden} de la URL.
     * @return la orden actualizada, para confirmar el estado final en el cliente.
     */
    @PUT("/api/ordenes/{numOrden}")
    Call<OrdenDto> actualizarOrden(@Path("numOrden") Long numOrden, @Body OrdenPushDto orden);

    /**
     * GET /api/ordenes
     * Lista todas las órdenes que ya existen en el servidor (lado "pull" de
     * la sincronización). Hoy se usa para informar al técnico cuántas
     * órdenes hay centralizadas; una v2 podría usarla para traer de vuelta
     * órdenes creadas desde otro dispositivo.
     */
    @GET("/api/ordenes")
    Call<List<OrdenDto>> listarOrdenesRemotas();

    /**
     * DELETE /api/ordenes/{numOrden}
     * Da de baja lógica la orden en el servidor (espejo del RN-6 local).
     * Se llama solo cuando la orden local ya tenía num_orden_remoto, es
     * decir, cuando ya había cruzado la frontera alguna vez.
     */
    @DELETE("/api/ordenes/{numOrden}")
    Call<Void> eliminarOrdenRemota(@Path("numOrden") Long numOrden);
}