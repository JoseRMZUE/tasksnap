package ue.edu.co.tasksnap.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;
import ue.edu.co.tasksnap.data.local.dao.ServicioDao;
import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.entity.Servicio;
import ue.edu.co.tasksnap.network.ApiService;
import ue.edu.co.tasksnap.network.RetrofitClient;
import ue.edu.co.tasksnap.network.ServicioDto;

/**
 * Repositorio de servicios: lecturas de la caché local para la UI
 * (spnTipoServicio del formulario de órdenes) + integración API Fase 5.
 *
 * Rol en la arquitectura: Servicios es API-first; este repositorio implementa
 * la estrategia "API primero, caché local como fallback" (Oportunidad 1, Acta v5).
 * Cuando hay conexión, los servicios vienen del backend centralizado (datos frescos).
 * Cuando falla la API (timeout, servidor dormido, sin red), usa la caché Room local.
 * La app NUNCA se queda sin datos para el spinner.
 *
 * Traza: Objetivo específico 3 y tarea S5 "Integración app-API" (Acta v5,
 *        ServiPro S.A.S., 2026). Mitiga Riesgo 2 (servidor gratuito dormido).
 *
 * Patrón de hilos: ExecutorService de un hilo + Handler de regreso a UI,
 * idéntico al de OrdenRepository y AuthRepository.
 */
public class ServicioRepository {

    private static final String TAG = "SERVICIO_REPO";

    /** Contrato de respuesta asíncrona genérica (onExito/onError siempre en hilo de UI). */
    public interface Callback<T> {
        void onExito(T dato);
        void onError(String mensaje);
    }

    /**
     * Callback específico para listado de servicios con fallback.
     * onExito: lista de servicios obtenida (de API o caché local).
     * onError: mensaje legible si falló tanto API como caché.
     */
    public interface CallbackListadoServicios {
        void onExito(List<Servicio> servicios);
        void onError(String mensaje);
    }

    private final ServicioDao servicioDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** @param context cualquier contexto; obtiene el singleton de AppDatabase. */
    public ServicioRepository(Context context) {
        this.servicioDao = AppDatabase.getInstance(context).servicioDao();
    }

    /** @return entrega un resultado de éxito en el hilo de UI. */
    private <T> void exito(Callback<T> callback, T dato) {
        mainHandler.post(() -> callback.onExito(dato));
    }

    /**
     * Lista los servicios activos desde caché local (método legacy, sin API).
     * Mantenido para compatibilidad con código existente que no necesita fallback.
     *
     * @param callback recibe la lista de servicios activos (RN-6 aplicado).
     */
    public void listarActivos(Callback<List<Servicio>> callback) {
        executor.execute(() -> exito(callback, servicioDao.listarActivos()));
    }

    /**
     * Obtiene un servicio por ID (validaciones puntuales de la UI).
     *
     * @param id clave primaria del servicio.
     * @param callback recibe el servicio o error si no existe.
     */
    public void obtenerPorId(long id, Callback<Servicio> callback) {
        executor.execute(() -> {
            Servicio servicio = servicioDao.obtenerPorId(id);
            if (servicio == null) {
                mainHandler.post(() -> callback.onError("El servicio no existe."));
            } else {
                exito(callback, servicio);
            }
        });
    }

    // ===== FASE 5: Integración API con fallback local (Oportunidad 1, Acta v5) =====

    /**
     * Obtiene servicios activos con estrategia API-first + fallback local.
     *
     * Flujo:
     *  1. Intenta GET /api/servicios vía Retrofit (15s timeout configurado en RetrofitClient).
     *  2. Si éxito HTTP 2xx: convierte DTOs a entidades Room y devuelve lista en hilo de UI.
     *  3. Si fallo (timeout, servidor dormido, sin red, HTTP 4xx/5xx): lee de caché local (Room).
     *  4. Si caché también vacía: devuelve error genérico en hilo de UI.
     *
     * Traza: Oportunidad 1 del Acta v5 -> app funcional con conectividad limitada.
     *        Mitiga Riesgo 2 (servidor gratuito dormido) mediante fallback automático.
     *
     * @param callback notifica éxito (lista de Servicios) o error (String), SIEMPRE en hilo de UI.
     */
    public void listarActivosConFallback(CallbackListadoServicios callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();

        api.obtenerServicios().enqueue(new retrofit2.Callback<List<ServicioDto>>() {
            @Override
            public void onResponse(Call<List<ServicioDto>> call, Response<List<ServicioDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Éxito API: convertir DTOs a entidades Room
                    List<Servicio> servicios = new ArrayList<>();
                    for (ServicioDto dto : response.body()) {
                        Servicio servicio = new Servicio(
                                dto.getServicioId(),
                                dto.getNombre(),
                                dto.isActivo()
                        );
                        servicios.add(servicio);
                    }
                    Log.i(TAG, "Servicios obtenidos de API: " + servicios.size());
                    // Guarda también en caché local: si no, una orden creada con un
                    // servicio que solo existe "de paso" en el spinner (nunca insertado
                    // en Room) queda invisible en la lista, porque el JOIN de
                    // listarResumenActivas() exige que también exista localmente.
                    //
                    // Importante: actualizar si ya existe, insertar solo si es nuevo.
                    // insertAll() usa REPLACE (borra + crea), y borrar un servicio que
                    // ya tiene órdenes locales viola la FK RESTRICT y tumba la app.
                    executor.execute(() -> {
                        for (Servicio s : servicios) {
                            if (servicioDao.update(s) == 0) {
                                servicioDao.insert(s);
                            }
                        }
                    });
                    // CRÍTICO: regresar al hilo de UI antes de tocar el callback
                    mainHandler.post(() -> callback.onExito(servicios));
                } else {
                    // API respondió pero con error (4xx, 5xx): fallback a local
                    Log.w(TAG, "API respondió con código: " + response.code() + ", usando caché local");
                    cargarDesdeCacheLocal(callback);
                }
            }

            @Override
            public void onFailure(Call<List<ServicioDto>> call, Throwable t) {
                // Fallo de red (timeout, sin conexión, servidor dormido): fallback a local
                Log.w(TAG, "Fallo de API: " + t.getMessage() + ", usando caché local");
                cargarDesdeCacheLocal(callback);
            }
        });
    }

    /**
     * Carga servicios desde la caché local (Room) como fallback.
     * Usado cuando la API no está disponible (Riesgo 2 del Acta: servidor dormido).
     * El resultado se entrega en el hilo de UI mediante mainHandler.post().
     */
    private void cargarDesdeCacheLocal(CallbackListadoServicios callback) {
        executor.execute(() -> {
            try {
                List<Servicio> servicios = servicioDao.listarActivos();
                if (servicios != null && !servicios.isEmpty()) {
                    Log.i(TAG, "Servicios cargados desde caché local: " + servicios.size());
                    mainHandler.post(() -> callback.onExito(servicios));
                } else {
                    Log.e(TAG, "Caché local vacía, no hay servicios disponibles");
                    mainHandler.post(() -> callback.onError("No hay servicios disponibles (API y caché local vacías)."));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error leyendo caché local", e);
                mainHandler.post(() -> callback.onError("Error al cargar servicios locales."));
            }
        });
    }

    // ===== Escritura contra la API (CRUD completo) =====
    // Se llaman desde la UI, por eso usan .enqueue() y no .execute().
    // Cada una también actualiza la caché local, para que la lista
    // se vea igual si después se abre la pantalla sin conexión.

    public void crear(String nombre, Callback<Servicio> callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();
        ServicioDto dto = new ServicioDto();
        dto.setNombre(nombre);
        dto.setActivo(true);

        api.crearServicio(dto).enqueue(new retrofit2.Callback<ServicioDto>() {
            @Override
            public void onResponse(Call<ServicioDto> call, Response<ServicioDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ServicioDto creado = response.body();
                    Servicio servicio = new Servicio(creado.getServicioId(), creado.getNombre(), creado.isActivo());
                    executor.execute(() -> servicioDao.insert(servicio));
                    mainHandler.post(() -> callback.onExito(servicio));
                } else {
                    mainHandler.post(() -> callback.onError("No se pudo crear el servicio (HTTP " + response.code() + ")."));
                }
            }

            @Override
            public void onFailure(Call<ServicioDto> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Sin conexión con el servidor."));
            }
        });
    }

    public void editar(long id, String nombre, Callback<Servicio> callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();
        ServicioDto dto = new ServicioDto();
        dto.setServicioId(id);
        dto.setNombre(nombre);
        dto.setActivo(true);

        api.actualizarServicio(id, dto).enqueue(new retrofit2.Callback<ServicioDto>() {
            @Override
            public void onResponse(Call<ServicioDto> call, Response<ServicioDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ServicioDto actualizado = response.body();
                    Servicio servicio = new Servicio(actualizado.getServicioId(), actualizado.getNombre(), actualizado.isActivo());
                    executor.execute(() -> servicioDao.update(servicio));
                    mainHandler.post(() -> callback.onExito(servicio));
                } else {
                    mainHandler.post(() -> callback.onError("No se pudo editar el servicio (HTTP " + response.code() + ")."));
                }
            }

            @Override
            public void onFailure(Call<ServicioDto> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Sin conexión con el servidor."));
            }
        });
    }

    public void eliminarLogico(long id, Callback<Void> callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();

        api.eliminarServicio(id).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> servicioDao.eliminarLogico(id));
                    mainHandler.post(() -> callback.onExito(null));
                } else {
                    mainHandler.post(() -> callback.onError("No se pudo eliminar el servicio (HTTP " + response.code() + ")."));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Sin conexión con el servidor."));
            }
        });
    }
}