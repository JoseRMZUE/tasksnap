package ue.edu.co.tasksnap.api.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ue.edu.co.tasksnap.api.dominio.Tecnico;

@Repository
public interface TecnicoRepository extends JpaRepository<Tecnico, Long> {
    List<Tecnico> findByActivoTrueOrderByNombreAsc();
}