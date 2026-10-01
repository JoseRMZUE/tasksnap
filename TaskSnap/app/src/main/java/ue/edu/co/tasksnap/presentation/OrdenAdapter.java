package ue.edu.co.tasksnap.presentation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ue.edu.co.tasksnap.R;
import ue.edu.co.tasksnap.data.local.pojo.OrdenResumen;

/**
 * Adaptador del ListView lvList: pinta cada OrdenResumen en una fila de
 * item_orden.xml (RF-05).
 *
 * Patrón de reciclaje de vistas: si convertView llega no nulo, se reutiliza la
 * fila que sale de pantalla en vez de inflar una nueva (rendimiento en listas
 * largas, RFN-03).
 *
 * Contrato con el diseño UI (borrador de la Lección 6, a cargo del Integrante 2):
 * este adaptador depende de los IDs tvItemNumero, tvItemDetalle, tvItemEstado y
 * tvItemSync de item_orden.xml. Si el diseño renombra esos IDs, solo cambian las
 * cuatro líneas de findViewById; la lógica de datos no se toca.
 */
public class OrdenAdapter extends ArrayAdapter<OrdenResumen> {

    /**
     * @param context  contexto de la pantalla que muestra la lista.
     * @param ordenes  lista de resúmenes a mostrar (viva: se refresca con notifyDataSetChanged).
     */
    public OrdenAdapter(@NonNull Context context, @NonNull List<OrdenResumen> ordenes) {
        super(context, 0, ordenes);
    }

    /**
     * Devuelve la fila pintada para la posición dada, reutilizando vistas cuando
     * el ListView lo permite.
     *
     * @param position    posición del elemento dentro de la lista.
     * @param convertView fila reciclable entrante, o null si hay que inflar.
     * @param parent      el ListView contenedor.
     * @return la fila lista para mostrarse.
     */
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View fila = convertView;
        if (fila == null) {
            fila = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_orden, parent, false);
        }

        OrdenResumen item = getItem(position);
        if (item != null) {
            TextView tvNumero = fila.findViewById(R.id.tvItemNumero);
            TextView tvDetalle = fila.findViewById(R.id.tvItemDetalle);
            TextView tvEstado = fila.findViewById(R.id.tvItemEstado);
            TextView tvSync = fila.findViewById(R.id.tvItemSync);

            tvNumero.setText("Orden #" + item.getNumOrden());
            tvDetalle.setText(item.getServicioNombre() + " – " + item.getClienteNombre());
            tvEstado.setText(item.getEstado());
            tvSync.setText(item.isSincronizado() ? "Sincronizada" : "Pendiente de sync");
        }
        return fila;
    }
}