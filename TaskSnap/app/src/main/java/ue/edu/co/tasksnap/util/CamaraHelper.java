package ue.edu.co.tasksnap.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utilidad de captura fotográfica con la cámara del SISTEMA (RF-12, RFN-09).
 *
 * Responsabilidades (puras, sin estado de launcher):
 *  - Crear el archivo temporal PRIVADO en getFilesDir() donde la cámara escribirá.
 *  - Proveer el Uri vía FileProvider (obligatorio desde API 24 para compartir
 *    archivos con otras apps sin permisos de almacenamiento).
 *  - Decodificar la foto con inSampleSize para NO saturar memoria (Riesgo 1:
 *    variación de RAM entre dispositivos; evita OutOfMemoryError).
 *  - Limpiar el archivo temporal tras la captura.
 *
 * NO guarda la evidencia definitiva ni metadatos: eso lo hace
 * EvidenciaRepository.capturarEvidencia(...), que recibe el Bitmap ya decodificado.
 * Separación de responsabilidades (RFN-11): esta clase solo mueve bytes de/desde
 * la cámara del sistema.
 *
 * El launcher (ActivityResultLauncher<Uri>) se registra en la Activity porque
 * Android exige registrar contratos antes de STARTED; esta utilidad solo aporta
 * los pasos deterministas alrededor de ese lanzamiento.
 */
public final class CamaraHelper {

    /**
     * Ancho objetivo de decodificación en px. Debe coincidir con
     * ArchivoUtil.ANCHO_MAXIMO (1280) para que la compresión final no re-escala:
     * decodificamos una sola vez a <=1280 y de ahí directo a JPEG.
     */
    private static final int ANCHO_DECODE = 1280;

    /** Subcarpeta temporal dentro de files-dir para el disparo de cámara. */
    private static final String SUBCARPETA_TEMP = "tmp_camara";

    /** Authority del FileProvider declarado en AndroidManifest (applicationId + .fileprovider). */
    private static final String AUTHORITY = "ue.edu.co.tasksnap.fileprovider";

    private CamaraHelper() {
    }

    /**
     * Crea el archivo temporal donde la cámara del sistema escribirá la foto.
     *
     * @param context contexto para getFilesDir().
     * @return archivo temporal recién creado (existe, vacío).
     * @throws IOException si no se puede crear el directorio/archivo.
     */
    public static File crearArchivoTemporal(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), SUBCARPETA_TEMP);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("No se pudo crear el directorio temporal de cámara.");
        }
        String nombre = "captura_" + System.currentTimeMillis() + ".jpg";
        File archivo = new File(dir, nombre);
        if (!archivo.createNewFile()) {
            throw new IOException("No se pudo crear el archivo temporal de cámara.");
        }
        return archivo;
    }

    /**
     * Convierte el archivo temporal en un Uri content:// compartible con la cámara.
     *
     * @param context contexto para FileProvider.
     * @param archivo archivo temporal creado por {@link #crearArchivoTemporal(Context)}.
     * @return Uri provisto por FileProvider, listo para TakePicture(Uri).
     */
    public static Uri uriDeArchivo(Context context, File archivo) {
        return androidx.core.content.FileProvider.getUriForFile(context, AUTHORITY, archivo);
    }

    /**
     * Decodifica la foto escrita por la cámara reduciéndola a <= ANCHO_DECODE px
     * con inSampleSize (potencia de 2), para no cargar el bitmap full-res en RAM.
     *
     * @param context contexto para ContentResolver.
     * @param uri     Uri del archivo temporal (salida de la cámara).
     * @return Bitmap decodificado y reducido, o null si no se pudo leer.
     */
    public static Bitmap decodificarSeguro(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        // Paso 1: leer dimensiones SIN cargar pixeles (inJustDecodeBounds).
        BitmapFactory.Options opcionesBounds = new BitmapFactory.Options();
        opcionesBounds.inJustDecodeBounds = true;
        try (InputStream in1 = context.getContentResolver().openInputStream(uri)) {
            if (in1 == null) return null;
            BitmapFactory.decodeStream(in1, null, opcionesBounds);
        } catch (IOException e) {
            return null;
        }
        int anchoOriginal = opcionesBounds.outWidth;
        if (anchoOriginal <= 0) {
            return null; // decode falló (archivo corrupto o no imagen)
        }

        // Paso 2: calcular inSampleSize = mayor potencia de 2 que reduzca a <= ANCHO_DECODE.
        int inSampleSize = 1;
        while (anchoOriginal / (inSampleSize * 2) >= ANCHO_DECODE) {
            inSampleSize *= 2;
        }

        // Paso 3: decodificar de verdad con la reducción aplicada.
        BitmapFactory.Options opcionesDecode = new BitmapFactory.Options();
        opcionesDecode.inSampleSize = inSampleSize;
        try (InputStream in2 = context.getContentResolver().openInputStream(uri)) {
            if (in2 == null) return null;
            return BitmapFactory.decodeStream(in2, null, opcionesDecode);
        } catch (IOException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Elimina el archivo temporal de cámara tras la captura (éxito o cancelación).
     *
     * @param archivo temporal a borrar; puede ser null (no-op).
     */
    public static void limpiarTemporal(File archivo) {
        if (archivo != null && archivo.exists()) {
            //noinspection ResultOfMethodCallIgnored
            archivo.delete();
        }
    }
}