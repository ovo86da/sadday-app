package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.SistemaClasesYosemite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SistemaClasesYosemiteRepository extends JpaRepository<SistemaClasesYosemite, String> {
    List<SistemaClasesYosemite> findAllByOrderByRankAsc();
}
