package ue.edu.co.tasksnap.presentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ue.edu.co.tasksnap.R;
import ue.edu.co.tasksnap.data.local.entity.Evidencia;
import ue.edu.co.tasksnap.repository.EvidenciaRepository;

/**
 * Adaptador del GridView gvEvidencias: pinta cada Evidencia en una celda de
 * item_evidencia.xml (miniatura + fecha + botón eliminar).
 *
 * Carga de imagen: cada celda pide su Bitmap al EvidenciaRepository de forma
 * asíncrona (mismo patrón de callback del resto de la app); no hay caché de
 * bitmaps en esta versión — la colección de evidencias por orden es pequeña,
 * así que no hace falta más para el alcance actual (RFN-03 aplica al listado
 * de órdenes, no a esta galería secundaria).
 *
 * Patrón de reciclaje de vistas: igual que OrdenAdapter, reutiliza convertView
 * cuando el GridView lo permite.
 */
public class EvidenciaAdapter extends ArrayAdapter<Evidencia> {

    /** Callback para el botón Eliminar de cada celda. */
    public interface OnEliminarClickListener {
        void onEliminarClick(Evidencia evidencia);
    }

    private final EvidenciaRepository evidenciaRepo;
    private final OnEliminarClickListener onEliminarClickListener;

    /**
     * @param context       contexto de la pantalla que muestra la galería.
     * @param evidencias    lista viva de evidencias a mostrar.
     * @param evidenciaRepo repositorio usado para cargar cada Bitmap bajo demanda.
     * @param listener      se invoca cuando el usuario toca "Eliminar" en una celda.
     */
    public EvidenciaAdapter(@NonNull Context context, @NonNull List<Evidencia> evidencias,
                            @NonNull EvidenciaRepository evidenciaRepo,
                            @NonNull OnEliminarClickListener listener) {
        super(context, 0, evidencias);
        this.evidenciaRepo = evidenciaRepo;
        this.onEliminarClickListener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View celda = convertView;
        if (celda == null) {
            celda = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_evidencia, parent, false);
        }

        Evidencia item = getItem(position);
        if (item != null) {
            ImageView ivThumb = celda.findViewById(R.id.ivEvidenciaThumb);
            TextView tvFecha = celda.findViewById(R.id.tvEvidenciaFecha);
            View btnEliminar = celda.findViewById(R.id.btnEvidenciaEliminar);

            tvFecha.setText(item.getFechaCaptura());
            ivThumb.setImageDrawable(null); // limpia la miniatura anterior mientras carga la nueva

            evidenciaRepo.cargarImagen(item, new EvidenciaRepository.Callback<Bitmap>() {
                @Override public void onExito(Bitmap bitmap) {
                    if (bitmap != null) {
                        ivThumb.setImageBitmap(bitmap);
                    }
                }
                @Override public void onError(String mensaje) {
                    // Silencioso a propósito: una miniatura que no carga no debe
                    // tumbar el resto de la galería ni interrumpir con un Toast.
                }
            });

            btnEliminar.setOnClickListener(v -> onEliminarClickListener.onEliminarClick(item));
        }
        return celda;
    }
}