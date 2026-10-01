package ue.edu.co.tasksnap.api.dominio;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * ENTIDAD JPA: Tecnico (catalogo maestro).
 * Espejo 1:1 de la entidad Room Tecnico del Android, con una diferencia
 * deliberada de seguridad: passwordHash y salt nunca salen en el JSON.
 */
@Entity
@Table(name = "tecnicos", uniqueConstraints = @UniqueConstraint(columnNames = "usuario"))
public class Tecnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tecnico_id")
    private Long tecnicoId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String usuario;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @JsonIgnore
    @Column(nullable = false)
    private String salt;

    @Column(nullable = false)
    private boolean activo = true;

    public Tecnico() {
    }

    public Tecnico(String nombre, String usuario, String passwordHash, String salt, boolean activo) {
        this.nombre = nombre;
        this.usuario = usuario;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.activo = activo;
    }

    public Long getTecnicoId() { return tecnicoId; }
    public void setTecnicoId(Long tecnicoId) { this.tecnicoId = tecnicoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}