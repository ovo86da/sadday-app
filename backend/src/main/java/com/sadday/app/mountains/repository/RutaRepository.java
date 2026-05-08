package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RutaRepository extends JpaRepository<Ruta, Integer>, JpaSpecificationExecutor<Ruta> {

    long countByMountainId(Integer mountainId);
}
