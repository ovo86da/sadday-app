package com.sadday.app.salidas.repository;

import com.sadday.app.salidas.entity.Dignidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DignidadRepository extends JpaRepository<Dignidad, Integer> {

    Optional<Dignidad> findByNombre(String nombre);
}
