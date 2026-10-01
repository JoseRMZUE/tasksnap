package ue.edu.co.tasksnap;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Cliente;
import ue.edu.co.tasksnap.repository.ClienteRepository;

/**
 * Gestión de clientes: CRUD completo contra la API (Requisito 4 del PDF).
 * Mismo patrón que ServiciosActivity, con 3 campos de formulario en vez de 1.
 */
public class ClientesActivity extends AppCompatActivity {

    private ListViewHolder views;
    private ClienteRepository repo;

    private final List<Cliente> cache = new ArrayList<>();
    private ArrayAdapter<Cliente> adapter;
    private Cliente clienteEnEdicion = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clientes);

        repo = new ClienteRepository(this);
        views = new ListViewHolder();

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, cache);
        views.lvClientes.setAdapter(adapter);
        views.lvClientes.setEmptyView(views.tvEmpty);
        views.lvClientes.setOnItemClickListener((parent, view, position, id) ->
                cargarEnFormulario(cache.get(position)));

        views.btnSave.setOnClickListener(v -> guardar());
        views.btnEdit.setOnClickListener(v -> editar());
        views.btnDelete.setOnClickListener(v -> eliminar());

        cargarLista();
    }

    private void cargarLista() {
        repo.listarActivosConFallback(new ClienteRepository.CallbackListadoClientes() {
            @Override
            public void onExito(List<Cliente> clientes) {
                cache.clear();
                cache.addAll(clientes);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String mensaje) {
                mostrarError(mensaje);
            }
        });
    }

    private void guardar() {
        if (clienteEnEdicion != null) {
            Toast.makeText(this, "Estás editando: usa Editar para guardar cambios", Toast.LENGTH_SHORT).show();
            return;
        }
        String nombre = views.etNombre.getText().toString().trim();
        String direccion = views.etDireccion.getText().toString().trim();
        String telefono = views.etTelefono.getText().toString().trim();
        if (nombre.isEmpty() || direccion.isEmpty() || telefono.isEmpty()) {
            Toast.makeText(this, "Completa nombre, dirección y teléfono", Toast.LENGTH_SHORT).show();
            return;
        }
        setCargando(true);
        repo.crear(nombre, direccion, telefono, new ClienteRepository.Callback<Cliente>() {
            @Override
            public void onExito(Cliente dato) {
                setCargando(false);
                Toast.makeText(ClientesActivity.this, "Cliente creado", Toast.LENGTH_SHORT).show();
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
        if (clienteEnEdicion == null) {
            Toast.makeText(this, "Selecciona un cliente de la lista", Toast.LENGTH_SHORT).show();
            return;
        }
        String nombre = views.etNombre.getText().toString().trim();
        String direccion = views.etDireccion.getText().toString().trim();
        String telefono = views.etTelefono.getText().toString().trim();
        if (nombre.isEmpty() || direccion.isEmpty() || telefono.isEmpty()) {
            Toast.makeText(this, "Completa nombre, dirección y teléfono", Toast.LENGTH_SHORT).show();
            return;
        }
        setCargando(true);
        repo.editar(clienteEnEdicion.getClienteId(), nombre, direccion, telefono,
                new ClienteRepository.Callback<Cliente>() {
                    @Override
                    public void onExito(Cliente dato) {
                        setCargando(false);
                        Toast.makeText(ClientesActivity.this, "Cliente actualizado", Toast.LENGTH_SHORT).show();
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
        if (clienteEnEdicion == null) {
            Toast.makeText(this, "Selecciona un cliente de la lista", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Dar de baja \"" + clienteEnEdicion.getNombre() + "\"")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    setCargando(true);
                    repo.eliminarLogico(clienteEnEdicion.getClienteId(), new ClienteRepository.Callback<Void>() {
                        @Override
                        public void onExito(Void dato) {
                            setCargando(false);
                            Toast.makeText(ClientesActivity.this, "Cliente eliminado", Toast.LENGTH_SHORT).show();
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

    private void cargarEnFormulario(Cliente cliente) {
        clienteEnEdicion = cliente;
        views.etNombre.setText(cliente.getNombre());
        views.etDireccion.setText(cliente.getDireccion());
        views.etTelefono.setText(cliente.getTelefono());
        views.btnEdit.setEnabled(true);
        views.btnDelete.setEnabled(true);
    }

    private void limpiarFormulario() {
        clienteEnEdicion = null;
        views.etNombre.setText("");
        views.etDireccion.setText("");
        views.etTelefono.setText("");
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

    /** Agrupa los findViewById del contrato de activity_clientes.xml. */
    private class ListViewHolder {
        final ListView lvClientes = findViewById(R.id.lvClientes);
        final TextView tvEmpty = findViewById(R.id.tvClientesEmpty);
        final ProgressBar pb = findViewById(R.id.pbClientes);
        final TextView tvError = findViewById(R.id.tvClientesError);
        final TextInputEditText etNombre = findViewById(R.id.etClienteNombre);
        final TextInputEditText etDireccion = findViewById(R.id.etClienteDireccion);
        final TextInputEditText etTelefono = findViewById(R.id.etClienteTelefono);
        final View btnSave = findViewById(R.id.btnSaveCliente);
        final View btnEdit = findViewById(R.id.btnEditCliente);
        final View btnDelete = findViewById(R.id.btnDeleteCliente);
    }
}