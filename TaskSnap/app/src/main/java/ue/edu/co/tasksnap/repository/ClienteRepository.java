package ue.edu.co.tasksnap.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import ue.edu.co.tasksnap.network.ApiService;
import ue.edu.co.tasksnap.network.ClienteDto;
import ue.edu.co.tasksnap.network.RetrofitClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ue.edu.co.tasksnap.data.local.dao.ClienteDao;
import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.entity.Cliente;

/**
 * Repositorio de clientes: lecturas de la caché local para la UI
 * (spnCliente del formulario de órdenes).
 *
 * Rol en la arquitectura: Clientes es API-first; hoy este repositorio solo
 * LEE la caché. En la Fase 5 crecerá con las escrituras vía Retrofit
 * (CRUD #2 de la matriz de cumplimiento) manteniendo esta misma fachada.
 */
public class ClienteRepository {

    /** Contrato de respuesta asíncrona (onExito/onError siempre en hilo de UI). */
    public interface Callback<T> {
        void onExito(T dato);
        void onError(String mensaje);
    }

    private final ClienteDao clienteDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** @param context cualquier contexto; obtiene el singleton de AppDatabase. */
    public ClienteRepository(Context context) {
        this.clienteDao = AppDatabase.getInstance(context).clienteDao();
    }

    /** @return entrega un resultado de éxito en el hilo de UI. */
    private <T> void exito(Callback<T> callback, T dato) {
        mainHandler.post(() -> callback.onExito(dato));
    }

    /**
     * Lista los clientes activos para alimentar el spnCliente.
     *
     * @param callback recibe la lista de clientes activos (RN-6 y RN-9 aplicadas).
     */
    public void listarActivos(Callback<List<Cliente>> callback) {
        executor.execute(() -> exito(callback, clienteDao.listarActivos()));
    }

    /**
     * Obtiene un cliente por ID (validaciones puntuales de la UI).
     *
     * @param id clave primaria del cliente.
     * @param callback recibe el cliente o error si no existe.
     */
    public void obtenerPorId(long id, Callback<Cliente> callback) {
        executor.execute(() -> {
            Cliente cliente = clienteDao.obtenerPorId(id);
            if (cliente == null) {
                mainHandler.post(() -> callback.onError("El cliente no existe."));
            } else {
                exito(callback, cliente);
            }
        });
    }

    // ===== Nombre del tipo callback nuevo, para distinguirlo del Callback<T> generico =====
    public interface CallbackListadoClientes {
        void onExito(List<Cliente> clientes);
        void onError(String mensaje);
    }

    /**
     * FASE 5 (A2): API-first con respaldo local, mismo patron que
     * ServicioRepository.listarActivosConFallback(). Intenta la API primero;
     * si falla (sin red, timeout, servidor dormido), cae a la cache Room.
     */
    public void listarActivosConFallback(CallbackListadoClientes callback) {
        ApiService api = ue.edu.co.tasksnap.network.RetrofitClient.getInstance().getApiService();

        api.obtenerClientes().enqueue(new retrofit2.Callback<List<ue.edu.co.tasksnap.network.ClienteDto>>() {
            @Override
            public void onResponse(retrofit2.Call<List<ue.edu.co.tasksnap.network.ClienteDto>> call,
                                   retrofit2.Response<List<ue.edu.co.tasksnap.network.ClienteDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Cliente> clientes = new ArrayList<>();
                    for (ue.edu.co.tasksnap.network.ClienteDto dto : response.body()) {
                        clientes.add(new Cliente(dto.getClienteId(), dto.getNombre(),
                                dto.getDireccion(), dto.getTelefono(), dto.isActivo()));
                    }
                    // Guarda también en caché local: si no, una orden creada con un
                    // cliente que solo existe "de paso" en el spinner (nunca insertado
                    // en Room) queda invisible en la lista, porque el JOIN que arma
                    // el listado de órdenes exige que también exista localmente.
                    //
                    // Importante: actualizar si ya existe, insertar solo si es nuevo.
                    // insertAll() usa REPLACE (borra + crea), y borrar un cliente que
                    // ya tiene órdenes locales viola la FK RESTRICT (RN-9) y tumba la app.
                    executor.execute(() -> {
                        for (Cliente c : clientes) {
                            if (clienteDao.update(c) == 0) {
                                clienteDao.insert(c);
                            }
                        }
                    });
                    mainHandler.post(() -> callback.onExito(clientes));
                } else {
                    cargarDesdeCacheLocal(callback);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<ue.edu.co.tasksnap.network.ClienteDto>> call, Throwable t) {
                cargarDesdeCacheLocal(callback);
            }
        });
    }

    private void cargarDesdeCacheLocal(CallbackListadoClientes callback) {
        executor.execute(() -> {
            List<Cliente> cache = clienteDao.listarActivos();
            mainHandler.post(() -> callback.onExito(cache));
        });
    }

    // ===== Escritura contra la API (CRUD completo, igual que Servicios) =====

    public void crear(String nombre, String direccion, String telefono, Callback<Cliente> callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();
        ClienteDto dto = new ClienteDto();
        dto.setNombre(nombre);
        dto.setDireccion(direccion);
        dto.setTelefono(telefono);
        dto.setActivo(true);

        api.crearCliente(dto).enqueue(new retrofit2.Callback<ClienteDto>() {
            @Override
            public void onResponse(retrofit2.Call<ClienteDto> call, retrofit2.Response<ClienteDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ClienteDto creado = response.body();
                    Cliente cliente = new Cliente(creado.getClienteId(), creado.getNombre(),
                            creado.getDireccion(), creado.getTelefono(), creado.isActivo());
                    executor.execute(() -> clienteDao.insert(cliente));
                    mainHandler.post(() -> callback.onExito(cliente));
                } else {
                    mainHandler.post(() -> callback.onError("No se pudo crear el cliente (HTTP " + response.code() + ")."));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ClienteDto> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Sin conexión con el servidor."));
            }
        });
    }

    public void editar(long id, String nombre, String direccion, String telefono, Callback<Cliente> callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();
        ClienteDto dto = new ClienteDto();
        dto.setClienteId(id);
        dto.setNombre(nombre);
        dto.setDireccion(direccion);
        dto.setTelefono(telefono);
        dto.setActivo(true);

        api.actualizarCliente(id, dto).enqueue(new retrofit2.Callback<ClienteDto>() {
            @Override
            public void onResponse(retrofit2.Call<ClienteDto> call, retrofit2.Response<ClienteDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ClienteDto actualizado = response.body();
                    Cliente cliente = new Cliente(actualizado.getClienteId(), actualizado.getNombre(),
                            actualizado.getDireccion(), actualizado.getTelefono(), actualizado.isActivo());
                    executor.execute(() -> clienteDao.update(cliente));
                    mainHandler.post(() -> callback.onExito(cliente));
                } else {
                    mainHandler.post(() -> callback.onError("No se pudo editar el cliente (HTTP " + response.code() + ")."));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ClienteDto> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Sin conexión con el servidor."));
            }
        });
    }

    public void eliminarLogico(long id, Callback<Void> callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();

        api.eliminarCliente(id).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> clienteDao.eliminarLogico(id));
                    mainHandler.post(() -> callback.onExito(null));
                } else {
                    mainHandler.post(() -> callback.onError("No se pudo eliminar el cliente (HTTP " + response.code() + ")."));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Sin conexión con el servidor."));
            }
        });
    }
}