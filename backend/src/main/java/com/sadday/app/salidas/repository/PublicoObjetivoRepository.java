package com.sadday.app.salidas.repository;

import com.sadday.app.salidas.entity.PublicoObjetivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicoObjetivoRepository extends JpaRepository<PublicoObjetivo, String> {
    List<PublicoObjetivo> findAllByOrderByOrdenAsc();
}
