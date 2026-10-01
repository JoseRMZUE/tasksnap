package ue.edu.co.tasksnap.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ue.edu.co.tasksnap.BuildConfig;

import java.util.concurrent.TimeUnit;

/**
 * CLIENTE RETROFIT: RetrofitClient (Singleton)
 *
 * Rol: Configurar y proveer una instancia única de Retrofit para toda la aplicación.
 * Traza: Objetivo específico 3 y tarea S5 "Integración app-API" (Acta v5,
 *        ServiPro S.A.S., 2026).
 *
 * Patrón Singleton: garantiza que solo exista UNA instancia del cliente HTTP en memoria.
 *                   Evita crear múltiples OkHttpClients (cada uno abre pools de conexiones,
 *                   consume memoria y puede agotar recursos en dispositivos móviles).
 *
 * Configuración aplicada:
 *  1. URL base: leída de BuildConfig.API_BASE_URL (inyectada desde build.gradle.kts).
 *               Permite cambiar entre emulador (10.0.2.2) y celular físico (IP real)
 *               sin tocar código Java.
 *  2. Timeouts: 15 segundos para conectar, leer y escribir. Mitiga el Riesgo 2 del Acta
 *               (servidor lento/dormido en planes gratuitos): si el backend no responde
 *               en 15s, la app falla rápido y muestra fallback local en vez de colgarse.
 *  3. Logging interceptor: imprime en Logcat cada petición/respuesta HTTP (nivel BODY).
 *                           Vital para depurar integración app-API en vivo durante pruebas.
 *  4. Converter Gson: deserializa el JSON del backend a objetos Java (DTOs) automáticamente.
 *
 * Uso típico desde un repositorio:
 *   ApiService api = RetrofitClient.getInstance().getApiService();
 *   api.obtenerServicios().enqueue(new Callback<List<ServicioDto>>() { ... });
 */
public class RetrofitClient {

    /** Instancia única del singleton (lazy initialization thread-safe). */
    private static volatile RetrofitClient instance;

    /** Instancia de Retrofit configurada (inmutable tras construcción). */
    private final Retrofit retrofit;

    /**
     * Constructor privado: fuerza el uso de getInstance() para obtener la instancia.
     * Configura OkHttpClient con timeouts, logging y la URL base desde BuildConfig.
     */
    private RetrofitClient() {
        // Interceptor de logging: imprime peticiones/respuestas completas en Logcat.
        // Nivel BODY muestra headers + cuerpo JSON (útil para depurar; en producción usar NONE).
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttpClient: motor HTTP subyacente con timeouts configurados.
        // 15 segundos mitiga el Riesgo 2 del Acta (servidor dormido/lento): fail-fast.
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        // Retrofit: cliente de alto nivel que convierte interfaces Java en peticiones HTTP.
        // baseUrl: leída de BuildConfig (inyectada desde build.gradle.kts).
        //          Para emulador: "http://10.0.2.2:8080"
        //          Para celular físico: "http://<IP_REAL_PC>:8080"
        // addConverterFactory: Gson convierte JSON <-> objetos Java (DTOs).
        this.retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /**
     * Obtiene la instancia única del singleton (double-checked locking thread-safe).
     *
     * @return RetrofitClient singleton configurado.
     * garantiza que solo se cree una instancia incluso en entornos multi-hilo (Android tiene
     * múltiples hilos para red, BD, UI). Sin esto, podrías crear múltiples OkHttpClients y agotar recursos.
     */
    public static RetrofitClient getInstance() {
        if (instance == null) {
            synchronized (RetrofitClient.class) {
                if (instance == null) {
                    instance = new RetrofitClient();
                }
            }
        }
        return instance;
    }

    /**
     * Obtiene la implementación generada por Retrofit de la interfaz ApiService.
     *
     * @return ApiService listo para llamar métodos como obtenerServicios().
     */
    public ApiService getApiService() {
        return retrofit.create(ApiService.class);
    }
}