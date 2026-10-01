package ue.edu.co.tasksnap.api.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * ENTIDAD JPA: Servicio
 * 
 * Rol: Representa la tabla "servicios" en la base de datos centralizada (PostgreSQL/H2).
 * Traza: RF-19 (Gestión de Servicios) y Contrato Congelado v4.1 del Acta v5.
 * 
 * IMPORTANCIA ARQUITECTÓNICA:
 * Esta clase es el ESPEJO EXACTO de la entidad Room en Android 
 * (app/src/main/java/ue/edu/co/tasksnap/data/local/entity/Servicio.java).
 * Los nombres de columna (@Column name="...") deben coincidir 1:1 con los campos JSON 
 * que espera el cliente Retrofit en Android. Si aquí cambiamos "nombre", allá falla 
 * la deserialización. Por eso usamos snake_case explícito donde sea necesario, 
 * aunque Java use camelCase internamente.
 */
@Entity
@Table(name = "servicios", uniqueConstraints = @UniqueConstraint(columnNames = "nombre"))
public class Servicio {

    /**
     * Clave Primaria Auto-generada.
     * Equivalente a @PrimaryKey(autoGenerate = true) en Room.
     * GenerationType.IDENTITY deja que la base de datos (H2/Postgres) asigne el número secuencial.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "servicio_id") // Nombre de columna en SQL (snake_case)
    private Long id;

    /**
     * Nombre del servicio (ej: "Plomería").
     * Unique Constraint en @Table asegura que no haya dos "Plomería".
     * nullable=false obliga a que siempre tenga valor.
     */
    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * Flag de Borrado Lógico (RN-6).
     * true = Activo, false = Inactivo/Dado de baja.
     * Nunca borramos físicamente la fila para preservar integridad referencial con Órdenes históricas.
     * DefaultValue = true asegura que al crear un nuevo servicio, nazca activo.
     */
    @Column(nullable = false)
    private boolean activo = true;

    // --- CONSTRUCTORES ---

    /** Constructor vacío obligatorio para JPA/Hibernate. Sin esto, Spring lanza error al iniciar. */
    public Servicio() {
    }

    /** Constructor conveniente para el Seeder y pruebas manuales. */
    public Servicio(String nombre, boolean activo) {
        this.nombre = nombre;
        this.activo = activo;
    }

    // --- GETTERS Y SETTERS ---
    // Necesarios para que Jackson (serializador JSON) lea/escriba los campos.
    // Sin estos getters, el endpoint /api/servicios devolvería {} vacío.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}