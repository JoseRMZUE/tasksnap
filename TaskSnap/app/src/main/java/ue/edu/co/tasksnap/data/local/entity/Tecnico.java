package ue.edu.co.tasksnap.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entidad que representa la tabla "tecnicos" en la base de datos local (Room).
 *
 * Esta tabla almacena las cuentas de los técnicos de campo de ServiPro S.A.S.
 * que pueden iniciar sesión en la aplicación móvil.
 *
 * Rol en la arquitectura:
 *  - Fuente de verdad: API Spring Boot + PostgreSQL (CRUD completo remoto).
 *  - Rol local: caché de solo lectura que habilita el login offline (RF-01, RF-20).
 *
 * Reglas de negocio aplicadas:
 *  - RN-8: nombre, usuario, password_hash y salt no nulos.
 *  - RN-6: borrado lógico mediante el atributo "activo".
 *  - El usuario debe ser único (índice único) para evitar conflictos en el login.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room">Room documentation</a>
 */
@Entity(tableName = "tecnicos", indices = {@Index(value = "usuario", unique = true)})
public class Tecnico {

    // --- ATRIBUTOS (mapeados 1:1 con la tabla "tecnicos" del modelo E-R v4.0) ---

    /**
     * Clave primaria autogenerada de la tabla.
     * Equivalente al SERIAL de PostgreSQL.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tecnico_id")
    private long tecnicoId;

    /**
     * Nombre completo del técnico. Obligatorio.
     */
    @NonNull
    @ColumnInfo(name = "nombre")
    private String nombre = "";

    /**
     * Nombre de usuario único para iniciar sesión.
     * El índice único declarado en @Entity impide duplicados a nivel de base de datos.
     */
    @NonNull
    @ColumnInfo(name = "usuario")
    private String usuario = "";

    /**
     * Hash SHA-256 de la contraseña (nunca se almacena en texto plano, RFN-05).
     */
    @NonNull
    @ColumnInfo(name = "password_hash")
    private String passwordHash = "";

    /**
     * Sal criptográfica única por usuario, usada junto con password_hash para
     * fortalecer la seguridad ante ataques de tabla arcoíris (RFN-05).
     */
    @NonNull
    @ColumnInfo(name = "salt")
    private String salt = "";

    /**
     * Indicador de borrado lógico (RN-6).
     * true (1) = activo, false (0) = dado de baja (no puede iniciar sesión).
     */
    @ColumnInfo(name = "activo", defaultValue = "1")
    private boolean activo = true;

    // --- CONSTRUCTORES ---

    /**
     * Constructor vacío, obligatorio para Room.
     * Room lo utiliza mediante reflexión al leer filas de la tabla y convertirlos en objetos.
     */
    public Tecnico() {
    }

    /**
     * Constructor con parámetros para objetos ya inicializados.
     *
     * @param tecnicoId    identificador único; pase 0L para registros locales nuevos
     *                     (Room lo autogenera al insertar), o el ID real para
     *                     actualizaciones y para la caché sincronizada desde la API.
     * @param nombre       nombre completo del técnico (no nulo).
     * @param usuario      nombre de usuario único (no nulo).
     * @param passwordHash hash SHA-256 de la contraseña (no nulo).
     * @param salt         sal criptográfica del usuario (no nulo).
     * @param activo       estado lógico del registro.
     */
    @Ignore
    public Tecnico(long tecnicoId, @NonNull String nombre, @NonNull String usuario,
                   @NonNull String passwordHash, @NonNull String salt, boolean activo) {
        this.tecnicoId = tecnicoId;
        this.nombre = nombre;
        this.usuario = usuario;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.activo = activo;
    }

    // --- GETTERS Y SETTERS ---

    /** @return el identificador único del técnico. */
    public long getTecnicoId() {
        return tecnicoId;
    }

    /** @param tecnicoId identificador a asignar. */
    public void setTecnicoId(long tecnicoId) {
        this.tecnicoId = tecnicoId;
    }

    /** @return el nombre completo del técnico (nunca nulo). */
    @NonNull
    public String getNombre() {
        return nombre;
    }

    /** @param nombre nombre a asignar (no nulo). */
    public void setNombre(@NonNull String nombre) {
        this.nombre = nombre;
    }

    /** @return el nombre de usuario único (nunca nulo). */
    @NonNull
    public String getUsuario() {
        return usuario;
    }

    /** @param usuario nombre de usuario a asignar (no nulo). */
    public void setUsuario(@NonNull String usuario) {
        this.usuario = usuario;
    }

    /** @return el hash SHA-256 de la contraseña (nunca nulo). */
    @NonNull
    public String getPasswordHash() {
        return passwordHash;
    }

    /** @param passwordHash hash a asignar (no nulo). */
    public void setPasswordHash(@NonNull String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** @return la sal criptográfica del usuario (nunca nulo). */
    @NonNull
    public String getSalt() {
        return salt;
    }

    /** @param salt sal a asignar (no nulo). */
    public void setSalt(@NonNull String salt) {
        this.salt = salt;
    }

    /** @return true si el técnico está activo, false si fue dado de baja. */
    public boolean isActivo() {
        return activo;
    }

    /** @param activo estado lógico a asignar. */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}