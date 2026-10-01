package ue.edu.co.tasksnap.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utilidad de gestión de archivos fotográficos (RF-12, RFN-09).
 *
 * Responsabilidades:
 *  - Generar nombres de archivo únicos basados en timestamp.
 *  - Crear el archivo físico en almacenamiento interno (privado de la app).
 *  - Comprimir la foto capturada para optimizar espacio y sincronización.
 *
 * Decisión de diseño (RFN-09): las fotos se guardan en getFilesDir() (privado),
 * no en getExternalFilesDir() (accesible por otras apps). Esto garantiza que
 * ningún usuario pueda manipular las evidencias fuera de la app.
 */
public final class ArchivoUtil {

    /** Formato de timestamp para nombres únicos: yyyyMMdd_HHmmss */
    private static final String FORMATO_NOMBRE = "yyyyMMdd_HHmmss";

    /** Calidad de compresión JPEG (0-100): 85 equilibra calidad y tamaño. */
    private static final int CALIDAD_COMPRESION = 85;

    /** Ancho máximo en píxeles: fotos más grandes se redimensionan a este valor. */
    private static final int ANCHO_MAXIMO = 1280;

    /** Constructor privado: clase de utilidades, no debe instanciarse. */
    private ArchivoUtil() {
    }

    /**
     * Genera un nombre de archivo único basado en la hora actual.
     * Ejemplo: "evidencia_20260924_143522.jpg"
     *
     * @return nombre de archivo con extensión .jpg.
     */
    public static String generarNombreArchivo() {
        String timestamp = new SimpleDateFormat(FORMATO_NOMBRE, Locale.US)
                .format(new Date());
        return "evidencia_" + timestamp + ".jpg";
    }

    /**
     * Crea un archivo vacío en almacenamiento interno privado de la app.
     *
     * @param context   contexto para obtener getFilesDir().
     * @param nombreArchivo nombre del archivo a crear.
     * @return el archivo creado (vacío, listo para escribir).
     * @throws IOException si no se puede crear el archivo.
     */
    public static File crearArchivoPrivado(Context context, String nombreArchivo) throws IOException {
        File directorio = context.getFilesDir();
        return new File(directorio, nombreArchivo);
    }

    /**
     * Comprime y guarda un Bitmap en el archivo destino.
     * Si la imagen es más ancha que ANCHO_MAXIMO, la redimensiona proporcionalmente.
     *
     * @param bitmap    imagen capturada por la cámara.
     * @param destino   archivo donde se guardará la imagen comprimida.
     * @throws IOException si no se puede escribir el archivo.
     */
    public static void comprimirYGuardar(Bitmap bitmap, File destino) throws IOException {
        Bitmap finalBitmap = bitmap;

        // Redimensionar si es más ancha que el máximo
        if (bitmap.getWidth() > ANCHO_MAXIMO) {
            float proporcion = (float) ANCHO_MAXIMO / bitmap.getWidth();
            int nuevoAlto = Math.round(bitmap.getHeight() * proporcion);
            finalBitmap = Bitmap.createScaledBitmap(bitmap, ANCHO_MAXIMO, nuevoAlto, true);
        }

        // Guardar como JPEG comprimido
        try (FileOutputStream out = new FileOutputStream(destino)) {
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_COMPRESION, out);
        }
    }

    /**
     * Carga un Bitmap desde un archivo para mostrarlo como miniatura.
     *
     * @param archivo archivo de imagen a cargar.
     * @return el Bitmap cargado, o null si el archivo no existe o está corrupto.
     */
    public static Bitmap cargarBitmap(File archivo) {
        if (!archivo.exists()) {
            return null;
        }
        return BitmapFactory.decodeFile(archivo.getAbsolutePath());
    }

    /**
     * Elimina un archivo de disco. Se usa al dar de baja una evidencia
     * (aunque el borrado lógico solo marca activo=0, el archivo físico
     * puede eliminarse para liberar espacio si la política lo permite).
     *
     * @param archivo archivo a eliminar.
     * @return true si se eliminó, false si no existía o no se pudo borrar.
     */
    public static boolean eliminarArchivo(File archivo) {
        return archivo.exists() && archivo.delete();
    }
}