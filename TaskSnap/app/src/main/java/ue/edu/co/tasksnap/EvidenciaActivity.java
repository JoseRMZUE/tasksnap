package ue.edu.co.tasksnap;

import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import ue.edu.co.tasksnap.data.local.entity.Evidencia;
import ue.edu.co.tasksnap.presentation.EvidenciaAdapter;
import ue.edu.co.tasksnap.repository.EvidenciaRepository;

/**
 * Galería de evidencias de una orden (cierra el comentario "carga de galería
 * histórica = mejora v2" que había quedado en MainActivity.cargarEnFormulario).
 *
 * Lista las fotos ya guardadas de una orden y permite eliminarlas
 * individualmente (borrado lógico, RN-6). Mismo patrón que
 * ServiciosActivity/ClientesActivity: Activity secundaria con su propio
 * repositorio, que no toca el estado en memoria de MainActivity (RFN-11).
 *
 * Recibe el número de orden por Intent extra (EXTRA_NUM_ORDEN); sin ese
 * extra no tiene sentido abrir esta pantalla (la FK orden_id es obligatoria).
 */
public class EvidenciaActivity extends AppCompatActivity {

    public static final String EXTRA_NUM_ORDEN = "numOrden";

    private long numOrden;
    private EvidenciaRepository evidenciaRepo;

    private final List<Evidencia> cache = new ArrayList<>();
    private EvidenciaAdapter adapter;

    private GridView gvEvidencias;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evidencias);

        numOrden = getIntent().getLongExtra(EXTRA_NUM_ORDEN, -1L);
        if (numOrden <= 0) {
            // Defensa: esta pantalla no tiene sentido sin una orden válida.
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvEvidenciasTitle);
        tvTitle.setText("Evidencias — Orden #" + numOrden);

        gvEvidencias = findViewById(R.id.gvEvidencias);
        tvEmpty = findViewById(R.id.tvEvidenciasEmpty);

        evidenciaRepo = new EvidenciaRepository(this);
        adapter = new EvidenciaAdapter(this, cache, evidenciaRepo, this::confirmarEliminar);
        gvEvidencias.setAdapter(adapter);

        cargarLista();
    }

    /** Recarga las evidencias activas de esta orden desde Room (READ). */
    private void cargarLista() {
        evidenciaRepo.listarPorOrden(numOrden, new EvidenciaRepository.Callback<List<Evidencia>>() {
            @Override public void onExito(List<Evidencia> evidencias) {
                cache.clear();
                cache.addAll(evidencias);
                adapter.notifyDataSetChanged();

                boolean vacio = evidencias.isEmpty();
                gvEvidencias.setVisibility(vacio ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(vacio ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(String mensaje) {
                Toast.makeText(EvidenciaActivity.this, mensaje, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Pide confirmación antes de dar de baja una evidencia (RN-6). */
    private void confirmarEliminar(Evidencia evidencia) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar esta evidencia")
                .setMessage("La foto quedará inactiva (borrado lógico); el archivo permanece auditable en disco.")
                .setPositiveButton("Eliminar", (dialog, which) ->
                        evidenciaRepo.eliminarEvidencia(evidencia.getEvidenciaId(),
                                new EvidenciaRepository.Callback<Integer>() {
                                    @Override public void onExito(Integer filas) {
                                        Toast.makeText(EvidenciaActivity.this,
                                                "Evidencia eliminada", Toast.LENGTH_SHORT).show();
                                        cargarLista();
                                    }
                                    @Override public void onError(String mensaje) {
                                        Toast.makeText(EvidenciaActivity.this, mensaje, Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }
}