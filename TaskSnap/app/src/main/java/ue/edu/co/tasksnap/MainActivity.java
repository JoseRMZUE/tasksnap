package ue.edu.co.tasksnap;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.os.Build;

import ue.edu.co.tasksnap.data.local.entity.Cliente;
import ue.edu.co.tasksnap.data.local.entity.Orden;
import ue.edu.co.tasksnap.data.local.entity.Servicio;
import ue.edu.co.tasksnap.data.local.pojo.OrdenResumen;
import ue.edu.co.tasksnap.presentation.OrdenAdapter;
import ue.edu.co.tasksnap.repository.ClienteRepository;
import ue.edu.co.tasksnap.repository.EvidenciaRepository;
import ue.edu.co.tasksnap.repository.OrdenRepository;
import ue.edu.co.tasksnap.repository.ServicioRepository;
import ue.edu.co.tasksnap.util.CamaraHelper;
import ue.edu.co.tasksnap.util.NotificacionUtil;
import ue.edu.co.tasksnap.util.SesionLocal;

import android.util.Log;

// FASE 5 (RN-7): motor de sincronizacion remota
import ue.edu.co.tasksnap.sync.SyncManager;

/**
 * Pantalla principal: formulario CRUD de órdenes de trabajo + listado.
 *
 * Esta clase es CONTROLADOR de presentación: no contiene reglas de negocio ni
 * toca DAOs directamente (RFN-11); delega todo en los repositorios y solo
 * pinta resultados y recoge entradas de la UI.
 *
 * CAMBIO FASE 3: esta Activity ya NO gestiona el seed inicial ni abre sesión
 * manualmente. Depende de que exista una sesión válida iniciada desde
 * LoginActivity. Como defensa extra, si se fuerza la entrada sin sesión,
 * se redirige de inmediato al Login.
 *
 * CAMBIO FASE 4: integra la cámara del sistema vía ActivityResultLauncher
 * (RF-12) y garantiza el canal de notificaciones al arrancar (RF-13). La
 * evidencia se persiste con EvidenciaRepository (disco + metadatos, RN-7);
 * el conteo visible de RN-2 se refresca desde OrdenRepository.
 *
 * CAMBIO FASE 5 (RN-7, componente 4 del Acta v5): onResume dispara el
 * SyncManager para empujar la cola de ordenes pendientes al backend.
 *
 * CAMBIO (fix ciclo de vida): onSaveInstanceState + restauración en onCreate
 * para que la orden en edición sobreviva si Android recrea esta Activity
 * mientras la cámara del sistema está en primer plano (dispositivos reales
 * con poca RAM pueden destruirla, a diferencia del emulador).
 *
 * Semántica de botones (contrato de IDs del Brief v1.1):
 *  - btnSave:  CREATE: crea una orden nueva desde el formulario (modo creación).
 *  - btnEdit:  UPDATE: guarda cambios de la orden cargada (modo edición),
 *              incluyendo cambio de estado con RN-1/RN-2/RN-3.
 *  - btnDelete: DELETE: baja lógica de la orden cargada, con confirmación (RN-6).
 *  - btnSearch: READ: filtra el listado por número total o parcial (RF-06).
 *  - btnListing: READ: recarga el listado completo (RF-05).
 *  - btnClearFields: limpia el formulario y sale del modo edición (UD-01).
 *  - btnCamera: adjunta evidencia fotográfica a la orden cargada (RF-12).
 *  - Tap en lvList: carga la orden en el formulario (modo edición).
 *  - Menú ⋮ "Cerrar Sesión": RF-03, cierra sesión y regresa al Login.
 *
 * Decisión UD-01 (RN-5): etOrderNumber se deshabilita en modo edición; en modo
 * creación su contenido se ignora al guardar (Room autogenera) y sirve para buscar.
 */
public class MainActivity extends AppCompatActivity {

    // --- Vistas (contrato de IDs del Brief v1.1) ---
    private EditText etOrderNumber;
    private EditText etDescription;
    private EditText etServiceDate;
    private Spinner spnTipoServicio;
    private Spinner spnCliente;
    private Spinner spnStatus;
    private ImageView ivEvidence;
    private Button btnCamera;
    private TextView tvEvidenceCount;
    private android.widget.Button btnVerEvidencias;
    private TextView tvUsuarioSesion;
    private ListView lvList;

    // --- Lógica ---
    private OrdenRepository ordenRepo;
    private ServicioRepository servicioRepo;
    private ClienteRepository clienteRepo;
    private EvidenciaRepository evidenciaRepo;   // Fase 4
    private SesionLocal sesion;

    // --- Cámara (Fase 4, RF-12) ---
    private ActivityResultLauncher<Uri> camaraLauncher;
    private ActivityResultLauncher<String> permisoCamaraLauncher;
    private ActivityResultLauncher<String> permisoNotificacionesLauncher;
    private File temporalCamara;

    /** Datos vivos del ListView (el adaptador los observa). */
    private final List<OrdenResumen> datosLista = new ArrayList<>();
    private OrdenAdapter adapter;

    /** Cachés en memoria de los spinners: la posición i corresponde al ID i-ésimo. */
    private final List<Servicio> cacheServicios = new ArrayList<>();
    private final List<Cliente> cacheClientes = new ArrayList<>();

    /** Orden cargada en modo edición; null = modo creación. */
    private Orden ordenEnEdicion = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fase 4: canal de notificaciones (RF-13) y launcher de cámara (RF-12).
        // Se registran SIEMPRE al inicio del onCreate, antes de cualquier return
        // condicional, porque registerForActivityResult exige llamarse antes de
        // que la Activity pase a STARTED (Riesgo 1: ciclo de vida).
        NotificacionUtil.crearCanal(this);
        camaraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> onCamaraResultado(success));
        permisoCamaraLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                concedido -> {
                    if (concedido) {
                        lanzarCamaraInterno();
                    } else {
                        avisar("Se necesita permiso de cámara para capturar evidencia.");
                    }
                });
        permisoNotificacionesLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                concedido -> { /* si lo niega, simplemente no vera recordatorios */ });

        // 1. Inicializar sesión ANTES de inflar vistas para validar acceso.
        sesion = new SesionLocal(this);

        // 2. DEFENSA EXTRA: si no hay sesión (deep link, bug o navegación forzada),
        //    redirigir inmediatamente al Login sin mostrar esta pantalla.
        if (!sesion.haySesion()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish(); // Cierra Main para que el botón Atrás no vuelva aquí.
            return;
        }

        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permisoNotificacionesLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        // 3. Inicializar componentes normales.
        enlazarVistas();
        ordenRepo = new OrdenRepository(this);
        servicioRepo = new ServicioRepository(this);
        clienteRepo = new ClienteRepository(this);
        evidenciaRepo = new EvidenciaRepository(this);

        adapter = new OrdenAdapter(this, datosLista);
        lvList.setAdapter(adapter);
        lvList.setOnItemClickListener((parent, view, position, id) ->
                cargarEnFormulario(datosLista.get(position).getNumOrden()));

        conectarBotones();

        // 4. Cargar UI con datos reales (sesión ya garantizada).
        tvUsuarioSesion.setText("Sesión: " + sesion.obtenerUsuario());
        cargarSpinners();
        refrescarLista();

        // 5. Restaura la orden en edición si Android recreó esta Activity
        //    (ej. al volver de la cámara y el sistema liberó memoria).
        if (savedInstanceState != null) {
            long numOrdenGuardado = savedInstanceState.getLong("numOrdenEnEdicion", -1L);
            if (numOrdenGuardado > 0) {
                cargarEnFormulario(numOrdenGuardado);
            }
        }
    }


    /**
     * FASE 5 (RN-7, componente 4 del Acta v5): disparador automatico del sync.
     *
     * Por que onResume y no onCreate: onResume corre CADA vez que la pantalla
     * vuelve a ser visible e interactiva (regresar de crear una orden, reabrir
     * la app tras minimizarla, volver del listado). La cola de sync debe
     * revisarse en cada regreso: es el momento en que el tecnico tipicamente
     * recupera conectividad tras una visita en campo.
     *
     * Seguridad: el SyncManager es idempotente (ordenes selladas salen de la
     * cola) y el trabajo corre en background; la UI nunca se bloquea.
     *
     * UX: el Toast solo aparece si hubo trabajo real (exitosas + fallidas > 0);
     * entrar a la pantalla con cola vacia no genera ruido visual.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Solo si hay sesion activa (defensa: si llegamos aqui sin sesion por
        // alguna via no contemplada, el SyncManager no tiene sentido de ejecutarse).
        if (sesion == null || !sesion.haySesion()) {
            return;
        }

        // Por si se creó/editó un cliente o servicio en otra pantalla y
        // se volvió aquí, los spinners deben reflejar los datos más recientes.
        cargarSpinners();
        if (ordenEnEdicion != null) {
            actualizarConteoEvidencias(ordenEnEdicion.getNumOrden());
        }

        new SyncManager(this).sincronizarOrdenesPendientes(new SyncManager.CallbackSync() {
            @Override
            public void onExito(int exitosas, int fallidas, int totalEnServidor) {
                // UX: el Toast solo aparece si hubo trabajo real; entrar con
                // cola vacía no genera ruido visual (ver javadoc de onResume).
                if (exitosas + fallidas > 0) {
                    Toast.makeText(MainActivity.this,
                            "Sync: " + exitosas + " OK, " + fallidas + " fallida(s) · "
                                    + totalEnServidor + " en el servidor",
                            Toast.LENGTH_LONG).show();
                }
                // Tras un sync (incluso vacio), refrescar el listado por si
                // vinieron ordenes del servidor en un pull futuro (v2).
                refrescarLista();
            }

            @Override
            public void onError(String mensaje) {
                Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Fix de ciclo de vida: guarda qué orden estaba en edición antes de que
     * Android pueda recrear esta Activity (ej. mientras la cámara del sistema
     * está en primer plano). Se usa en onCreate para restaurarla.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (ordenEnEdicion != null) {
            outState.putLong("numOrdenEnEdicion", ordenEnEdicion.getNumOrden());
        }
    }

    /** Enlaza todas las vistas del layout por su ID del contrato. */
    private void enlazarVistas() {
        etOrderNumber = findViewById(R.id.etOrderNumber);
        etDescription = findViewById(R.id.etDescription);
        etServiceDate = findViewById(R.id.etServiceDate);
        spnTipoServicio = findViewById(R.id.spnTipoServicio);
        spnCliente = findViewById(R.id.spnCliente);
        spnStatus = findViewById(R.id.spnStatus);
        ivEvidence = findViewById(R.id.ivEvidence);
        btnCamera = findViewById(R.id.btnCamera);
        tvEvidenceCount = findViewById(R.id.tvEvidenceCount);
        btnVerEvidencias = findViewById(R.id.btnVerEvidencias);
        tvUsuarioSesion = findViewById(R.id.tvUsuarioSesion);
        lvList = findViewById(R.id.lvList);

        // RN-1: el spinner de estado solo admite los dos estados del dominio.
        ArrayAdapter<String> adapterEstados = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                Arrays.asList("PENDIENTE", "COMPLETADA"));
        adapterEstados.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnStatus.setAdapter(adapterEstados);
    }

    /** Asigna el comportamiento de cada botón del contrato UI. */
    private void conectarBotones() {
        findViewById(R.id.btnSave).setOnClickListener(v -> guardar());
        findViewById(R.id.btnEdit).setOnClickListener(v -> editar());
        findViewById(R.id.btnDelete).setOnClickListener(v -> eliminar());
        findViewById(R.id.btnSearch).setOnClickListener(v ->
                ordenRepo.buscarResumen(etOrderNumber.getText().toString(),
                        new OrdenRepository.Callback<List<OrdenResumen>>() {
                            @Override public void onExito(List<OrdenResumen> lista) {
                                pintarLista(lista);
                            }
                            @Override public void onError(String mensaje) {
                                avisar(mensaje);
                            }
                        }));
        findViewById(R.id.btnListing).setOnClickListener(v -> refrescarLista());
        findViewById(R.id.btnClearFields).setOnClickListener(v -> limpiarFormulario());
        findViewById(R.id.btnMenu).setOnClickListener(this::mostrarMenuOpciones);

        // Fase 4 (RF-12): dispara la cámara del sistema sobre archivo temporal privado.
        btnCamera.setOnClickListener(v -> lanzarCamara());
        btnVerEvidencias.setOnClickListener(v -> abrirGaleriaEvidencias());

    }

    /** Carga las cachés de servicios y clientes y alimenta los spinners. */
    private void cargarSpinners() {
// ===== FASE 5: Carga de servicios con estrategia API-first + fallback local =====
// Traza: Oportunidad 1 del Acta v5 -> app funcional con/sin conexión.
//        Mitiga Riesgo 2 (servidor dormido) mediante fallback automático a caché Room.
        servicioRepo.listarActivosConFallback(new ServicioRepository.CallbackListadoServicios() {
            @Override
            public void onExito(List<Servicio> servicios) {
                cacheServicios.clear();
                cacheServicios.addAll(servicios);

                // Éxito (de API o caché local): poblar spinner en hilo de UI
                ArrayAdapter<Servicio> adapter = new ArrayAdapter<>(
                        MainActivity.this,
                        android.R.layout.simple_spinner_item,
                        servicios
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spnTipoServicio.setAdapter(adapter);

                Log.i("MAIN_ACTIVITY", "Spinner de servicios poblado con " + servicios.size() + " elementos");
            }

            @Override
            public void onError(String mensaje) {
                // Error crítico (ni API ni caché tienen datos): mostrar Toast y dejar spinner vacío
                Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_LONG).show();
                Log.e("MAIN_ACTIVITY", "Error cargando servicios: " + mensaje);
            }
        });

        // ===== Carga de clientes (local, Fase 5 A2 pendiente de llevarla a API-first) =====
        // ===== FASE 5 (A2): Carga de clientes con estrategia API-first + fallback local =====
        clienteRepo.listarActivosConFallback(new ClienteRepository.CallbackListadoClientes() {
            @Override
            public void onExito(List<Cliente> clientes) {
                cacheClientes.clear();
                cacheClientes.addAll(clientes);

                ArrayAdapter<Cliente> adapter = new ArrayAdapter<>(
                        MainActivity.this,
                        android.R.layout.simple_spinner_item,
                        clientes
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spnCliente.setAdapter(adapter);

                Log.i("MAIN_ACTIVITY", "Spinner de clientes poblado con " + clientes.size() + " elementos");
            }

            @Override
            public void onError(String mensaje) {
                Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_LONG).show();
                Log.e("MAIN_ACTIVITY", "Error cargando clientes: " + mensaje);
            }
        });
    }

    /** Recarga el listado completo de órdenes activas (RF-05). */
    private void refrescarLista() {
        ordenRepo.listarResumen(new OrdenRepository.Callback<List<OrdenResumen>>() {
            @Override public void onExito(List<OrdenResumen> lista) {
                pintarLista(lista);
            }
            @Override public void onError(String mensaje) { avisar(mensaje); }
        });
    }

    /** Reemplaza los datos del adaptador y notifica el cambio al ListView. */
    private void pintarLista(List<OrdenResumen> lista) {
        datosLista.clear();
        datosLista.addAll(lista);
        adapter.notifyDataSetChanged();
    }

    /** CREATE: guarda una orden nueva con los datos del formulario (RF-04). */
    private void guardar() {
        if (ordenEnEdicion != null) {
            avisar("Estás en modo edición: usa Editar para guardar cambios");
            return;
        }
        long servicioId = idServicioSeleccionado();
        long clienteId = idClienteSeleccionado();
        if (servicioId <= 0 || clienteId <= 0) {
            avisar("Selecciona servicio y cliente");
            return;
        }
        ordenRepo.crearOrden(servicioId, clienteId,
                etDescription.getText().toString(),
                etServiceDate.getText().toString(),
                new OrdenRepository.Callback<Long>() {
                    @Override public void onExito(Long numOrden) {
                        avisar("Orden creada: #" + numOrden);
                        limpiarFormulario();
                        refrescarLista();
                    }
                    @Override public void onError(String mensaje) { avisar(mensaje); }
                });
    }

    /**
     * UPDATE: guarda cambios de la orden cargada, incluyendo cambio de estado.
     * El cambio de estado pasa por cambiarEstado para que RN-1/RN-2/RN-3 vivan
     * en el repositorio y no aquí.
     */
    private void editar() {
        if (ordenEnEdicion == null) {
            avisar("Selecciona una orden del listado para editar");
            return;
        }
        final long numOrden = ordenEnEdicion.getNumOrden();
        final String estadoNuevo = String.valueOf(spnStatus.getSelectedItem());

        ordenRepo.cambiarEstado(numOrden, estadoNuevo,
                new OrdenRepository.Callback<Integer>() {
                    @Override public void onExito(Integer filas) {
                        // Estado validado y aplicado; ahora los campos editables.
                        ordenEnEdicion.setEstado(estadoNuevo);
                        ordenEnEdicion.setServicioId(idServicioSeleccionado());
                        ordenEnEdicion.setClienteId(idClienteSeleccionado());
                        ordenEnEdicion.setDescripcion(etDescription.getText().toString());
                        ordenEnEdicion.setFechaServicio(etServiceDate.getText().toString());
                        ordenRepo.editarOrden(ordenEnEdicion,
                                new OrdenRepository.Callback<Integer>() {
                                    @Override public void onExito(Integer filas) {
                                        avisar("Orden #" + numOrden + " actualizada");
                                        limpiarFormulario();
                                        refrescarLista();
                                    }
                                    @Override public void onError(String mensaje) { avisar(mensaje); }
                                });
                    }
                    @Override public void onError(String mensaje) {
                        // RN-2 llega por aquí: cierre bloqueado sin evidencia.
                        avisar(mensaje);
                    }
                });
    }

    /** DELETE: baja lógica con confirmación explícita (RF-08, RN-6). */
    private void eliminar() {
        if (ordenEnEdicion == null) {
            avisar("Selecciona una orden del listado para eliminar");
            return;
        }
        final long numOrden = ordenEnEdicion.getNumOrden();
        new AlertDialog.Builder(this)
                .setTitle("Dar de baja la orden #" + numOrden)
                .setMessage("La orden y sus evidencias quedarán inactivas (borrado lógico).")
                .setPositiveButton("Eliminar", (d, w) ->
                        ordenRepo.eliminarOrden(numOrden,
                                new OrdenRepository.Callback<Integer>() {
                                    @Override public void onExito(Integer filas) {
                                        avisar("Orden #" + numOrden + " dada de baja");
                                        limpiarFormulario();
                                        refrescarLista();
                                    }
                                    @Override public void onError(String mensaje) { avisar(mensaje); }
                                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Carga una orden en el formulario y entra en modo edición (UD-01). */
    private void cargarEnFormulario(long numOrden) {
        ordenRepo.obtenerPorId(numOrden, new OrdenRepository.Callback<Orden>() {
            @Override public void onExito(Orden orden) {
                ordenEnEdicion = orden;
                findViewById(R.id.btnEdit).setEnabled(true);
                findViewById(R.id.btnDelete).setEnabled(true);
                etOrderNumber.setText(String.valueOf(orden.getNumOrden()));
                etOrderNumber.setEnabled(false);          // RN-5 / UD-01
                etDescription.setText(orden.getDescripcion());
                etServiceDate.setText(orden.getFechaServicio());
                seleccionarSpinner(spnTipoServicio, cacheServicios, orden.getServicioId());
                seleccionarSpinner(spnCliente, cacheClientes, orden.getClienteId());

                // RN-1: el spinner de estados tiene orden fijo definido en
                // enlazarVistas() (posición 0 = PENDIENTE, 1 = COMPLETADA),
                // así que la posición se deduce directo del estado cargado.
                spnStatus.setSelection("COMPLETADA".equals(orden.getEstado()) ? 1 : 0);

                // RF-12/RN-2: refresca el conteo de evidencias activas.
                // (La miniatura de una foto previa se mostrará tras una nueva
                // captura en esta versión; carga de galería histórica = mejora v2.)
                ordenRepo.contarEvidenciasActivas(numOrden,
                        new OrdenRepository.Callback<Integer>() {
                            @Override public void onExito(Integer n) {
                                tvEvidenceCount.setText("Evidencias: " + n);
                            }
                            @Override public void onError(String mensaje) { avisar(mensaje); }
                        });
            }
            @Override public void onError(String mensaje) { avisar(mensaje); }
        });
    }

    /** Sale del modo edición y deja el formulario listo para crear (UD-01). */
    private void limpiarFormulario() {
        ordenEnEdicion = null;
        findViewById(R.id.btnEdit).setEnabled(false);
        findViewById(R.id.btnDelete).setEnabled(false);
        etOrderNumber.setText("");
        etOrderNumber.setEnabled(true);
        etDescription.setText("");
        etServiceDate.setText("");
        spnStatus.setSelection(0);
        tvEvidenceCount.setText("Evidencias: 0");
        // Fase 4: limpia miniatura y archivo temporal de cámara.
        ivEvidence.setImageDrawable(null);
        CamaraHelper.limpiarTemporal(temporalCamara);
        temporalCamara = null;
    }

    /** ID del servicio seleccionado en el spinner, o 0 si la caché está vacía. */
    private long idServicioSeleccionado() {
        int pos = spnTipoServicio.getSelectedItemPosition();
        return (pos >= 0 && pos < cacheServicios.size())
                ? cacheServicios.get(pos).getServicioId() : 0L;
    }

    /** ID del cliente seleccionado en el spinner, o 0 si la caché está vacía. */
    private long idClienteSeleccionado() {
        int pos = spnCliente.getSelectedItemPosition();
        return (pos >= 0 && pos < cacheClientes.size())
                ? cacheClientes.get(pos).getClienteId() : 0L;
    }

    /** Sincroniza un spinner con el ID recibido, buscando su posición en la caché. */
    private void seleccionarSpinner(Spinner spinner, List<?> cache, long id) {
        for (int i = 0; i < cache.size(); i++) {
            long idItem = (cache.get(i) instanceof Servicio)
                    ? ((Servicio) cache.get(i)).getServicioId()
                    : ((Cliente) cache.get(i)).getClienteId();
            if (idItem == id) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    /** Mensaje corto en pantalla (canal único de avisos de la UI). */
    private void avisar(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    // --- CÁMARA (Fase 4, RF-12) ---

    /**
     * RF-12: dispara la cámara del sistema sobre un archivo temporal privado.
     * Solo habilitado con una orden cargada (la evidencia tiene FK orden_id):
     * en modo creación se pide guardar primero (coherente con UD-01).
     */
    private void lanzarCamara() {
        if (ordenEnEdicion == null) {
            avisar("Guarda la orden primero para adjuntar evidencias");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            lanzarCamaraInterno();
        } else {
            permisoCamaraLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }
    /** Abre la galería de evidencias de la orden cargada (ver/eliminar, RN-6). */
    private void abrirGaleriaEvidencias() {
        if (ordenEnEdicion == null) {
            avisar("Selecciona una orden del listado primero");
            return;
        }
        Intent intent = new Intent(this, EvidenciaActivity.class);
        intent.putExtra(EvidenciaActivity.EXTRA_NUM_ORDEN, ordenEnEdicion.getNumOrden());
        startActivity(intent);
    }

    /** Se ejecuta solo una vez el permiso de cámara ya está concedido. */
    private void lanzarCamaraInterno() {
        try {
            temporalCamara = CamaraHelper.crearArchivoTemporal(this);
            Uri uri = CamaraHelper.uriDeArchivo(this, temporalCamara);
            camaraLauncher.launch(uri);
        } catch (IOException e) {
            avisar("No se pudo preparar la cámara: " + e.getMessage());
        }
    }

    /**
     * Callback del launcher de cámara: decodifica la foto, la persiste vía
     * EvidenciaRepository (disco + metadatos, RN-7) y refresca miniatura/conteo.
     *
     * @param success true si la cámara devolvió imagen; false si se canceló.
     */
    private void onCamaraResultado(boolean success) {
        if (!success) {
            CamaraHelper.limpiarTemporal(temporalCamara);
            temporalCamara = null;
            avisar("Captura cancelada");
            return;
        }
        if (ordenEnEdicion == null || temporalCamara == null) {
            CamaraHelper.limpiarTemporal(temporalCamara);
            temporalCamara = null;
            return;
        }
        final long numOrden = ordenEnEdicion.getNumOrden();
        Uri uri = CamaraHelper.uriDeArchivo(this, temporalCamara);
        Bitmap bitmap = CamaraHelper.decodificarSeguro(this, uri);
        if (bitmap == null) {
            CamaraHelper.limpiarTemporal(temporalCamara);
            temporalCamara = null;
            avisar("No se pudo leer la foto capturada");
            return;
        }
        evidenciaRepo.capturarEvidencia(numOrden, bitmap,
                new EvidenciaRepository.Callback<Long>() {
                    @Override public void onExito(Long evidenciaId) {
                        // Miniatura inmediata con el bitmap ya en memoria.
                        ivEvidence.setImageBitmap(bitmap);
                        actualizarConteoEvidencias(numOrden);
                        CamaraHelper.limpiarTemporal(temporalCamara);
                        temporalCamara = null;
                        avisar("Evidencia #" + evidenciaId + " guardada");
                    }
                    @Override public void onError(String mensaje) {
                        CamaraHelper.limpiarTemporal(temporalCamara);
                        temporalCamara = null;
                        avisar(mensaje);
                    }
                });
    }

    /** RF-12/RN-2: refresca tvEvidenceCount desde el repositorio de órdenes. */
    private void actualizarConteoEvidencias(long numOrden) {
        ordenRepo.contarEvidenciasActivas(numOrden,
                new OrdenRepository.Callback<Integer>() {
                    @Override public void onExito(Integer n) {
                        tvEvidenceCount.setText("Evidencias: " + n);
                    }
                    @Override public void onError(String mensaje) { avisar(mensaje); }
                });
    }

    // --- MENÚ SUPERIOR (RF-03: logout funcional) ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Clientes");
        menu.add(0, 2, 1, "Servicios");
        menu.add(0, 4, 3, "Cerrar Sesión");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                startActivity(new Intent(this, ClientesActivity.class));
                break;
            case 2:
                startActivity(new Intent(this, ServiciosActivity.class));
                break;
            case 4: // RF-03: cerrar sesión real.
                cerrarSesionYSalir();
                break;
        }
        return true;
    }

    /**
     * El tema de la app no usa ActionBar, así que onCreateOptionsMenu nunca se
     * dispara solo. Este botón abre el mismo menú a mano con un PopupMenu, y
     * reutiliza onOptionsItemSelected porque tiene la misma firma de método.
     */
    private void mostrarMenuOpciones(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Clientes");
        popup.getMenu().add(0, 2, 1, "Servicios");
        popup.getMenu().add(0, 4, 3, "Cerrar Sesión");
        popup.setOnMenuItemClickListener(this::onOptionsItemSelected);
        popup.show();
    }

    /**
     * RF-03: Cierra la sesión local y retorna al Login.
     * Limpia la pila de actividades para evitar volver a Main con el botón Atrás.
     */
    private void cerrarSesionYSalir() {
        sesion.cerrarSesion();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}