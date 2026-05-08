package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.Mountain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MountainRepository extends JpaRepository<Mountain, Integer>, JpaSpecificationExecutor<Mountain> {
    boolean existsByNombreAndRegion(String nombre, String region);
    boolean existsByNombreAndRegionAndIdNot(String nombre, String region, Integer id);
}
