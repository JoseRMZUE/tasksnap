package ue.edu.co.tasksnap;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Servicio;
import ue.edu.co.tasksnap.repository.ServicioRepository;

/**
 * Gestión de servicios: CRUD completo contra la API (Requisito 4 del PDF).
 * Mismo patrón de MainActivity: una variable (servicioEnEdicion) decide si
 * "Guardar" crea uno nuevo o si "Editar"/"Eliminar" actúan sobre el que
 * está cargado en el formulario.
 */
public class ServiciosActivity extends AppCompatActivity {

    private ListViewHolder views;
    private ServicioRepository repo;

    private final List<Servicio> cache = new ArrayList<>();
    private ArrayAdapter<Servicio> adapter;
    private Servicio servicioEnEdicion = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servicios);

        repo = new ServicioRepository(this);
        views = new ListViewHolder();

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, cache);
        views.lvServicios.setAdapter(adapter);
        views.lvServicios.setEmptyView(views.tvEmpty);
        views.lvServicios.setOnItemClickListener((parent, view, position, id) ->
                cargarEnFormulario(cache.get(position)));

        views.btnSave.setOnClickListener(v -> guardar());
        views.btnEdit.setOnClickListener(v -> editar());
        views.btnDelete.setOnClickListener(v -> eliminar());

        cargarLista();
    }

    private void cargarLista() {
        repo.listarActivosConFallback(new ServicioRepository.CallbackListadoServicios() {
            @Override
            public void onExito(List<Servicio> servicios) {
                cache.clear();
                cache.addAll(servicios);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String mensaje) {
                mostrarError(mensaje);
            }
        });
    }

    private void guardar() {
        if (servicioEnEdicion != null) {
            Toast.makeText(this, "Estás editando: usa Editar para guardar cambios", Toast.LENGTH_SHORT).show();
            return;
        }
        String nombre = views.etNombre.getText().toString().trim();
        if (nombre.isEmpty()) {
            Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show();
            return;
        }
        setCargando(true);
        repo.crear(nombre, new ServicioRepository.Callback<Servicio>() {
            @Override
            public void onExito(Servicio dato) {
                setCargando(false);
                Toast.makeText(ServiciosActivity.this, "Servicio creado", Toast.LENGTH_SHORT).show();
                limpiarFormulario();
                cargarLista();
            }

            @Override
            public void onError(String mensaje) {
                setCargando(false);
                mostrarError(mensaje);
            }
        });
    }

    private void editar() {
        if (servicioEnEdicion == null) {
            Toast.makeText(this, "Selecciona un servicio de la lista", Toast.LENGTH_SHORT).show();
            return;
        }
        String nombre = views.etNombre.getText().toString().trim();
        if (nombre.isEmpty()) {
            Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show();
            return;
        }
        setCargando(true);
        repo.editar(servicioEnEdicion.getServicioId(), nombre, new ServicioRepository.Callback<Servicio>() {
            @Override
            public void onExito(Servicio dato) {
                setCargando(false);
                Toast.makeText(ServiciosActivity.this, "Servicio actualizado", Toast.LENGTH_SHORT).show();
                limpiarFormulario();
                cargarLista();
            }

            @Override
            public void onError(String mensaje) {
                setCargando(false);
                mostrarError(mensaje);
            }
        });
    }

    private void eliminar() {
        if (servicioEnEdicion == null) {
            Toast.makeText(this, "Selecciona un servicio de la lista", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Dar de baja \"" + servicioEnEdicion.getNombre() + "\"")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    setCargando(true);
                    repo.eliminarLogico(servicioEnEdicion.getServicioId(), new ServicioRepository.Callback<Void>() {
                        @Override
                        public void onExito(Void dato) {
                            setCargando(false);
                            Toast.makeText(ServiciosActivity.this, "Servicio eliminado", Toast.LENGTH_SHORT).show();
                            limpiarFormulario();
                            cargarLista();
                        }

                        @Override
                        public void onError(String mensaje) {
                            setCargando(false);
                            mostrarError(mensaje);
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void cargarEnFormulario(Servicio servicio) {
        servicioEnEdicion = servicio;
        views.etNombre.setText(servicio.getNombre());
        views.btnEdit.setEnabled(true);
        views.btnDelete.setEnabled(true);
    }

    private void limpiarFormulario() {
        servicioEnEdicion = null;
        views.etNombre.setText("");
        views.btnEdit.setEnabled(false);
        views.btnDelete.setEnabled(false);
    }

    private void setCargando(boolean cargando) {
        views.pb.setVisibility(cargando ? View.VISIBLE : View.GONE);
    }

    private void mostrarError(String mensaje) {
        views.tvError.setText(mensaje);
        views.tvError.setVisibility(View.VISIBLE);
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    /** Agrupa los findViewById del contrato de activity_servicios.xml. */
    private class ListViewHolder {
        final android.widget.ListView lvServicios = findViewById(R.id.lvServicios);
        final TextView tvEmpty = findViewById(R.id.tvServiciosEmpty);
        final ProgressBar pb = findViewById(R.id.pbServicios);
        final TextView tvError = findViewById(R.id.tvServiciosError);
        final TextInputEditText etNombre = findViewById(R.id.etServicioNombre);
        final View btnSave = findViewById(R.id.btnSaveServicio);
        final View btnEdit = findViewById(R.id.btnEditServicio);
        final View btnDelete = findViewById(R.id.btnDeleteServicio);
    }
}