package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.RutaEnlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RutaEnlaceRepository extends JpaRepository<RutaEnlace, Long> {

    List<RutaEnlace> findByRutaIdOrderByOrdenAscIdAsc(Integer rutaId);

    Optional<RutaEnlace> findByIdAndRutaId(Long id, Integer rutaId);
}
