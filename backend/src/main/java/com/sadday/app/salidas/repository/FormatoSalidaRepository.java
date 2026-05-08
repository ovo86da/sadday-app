package com.sadday.app.salidas.repository;

import com.sadday.app.salidas.entity.FormatoSalida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormatoSalidaRepository extends JpaRepository<FormatoSalida, String> {
    List<FormatoSalida> findAllByOrderByOrdenAsc();
}
