package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.DificultadHielo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DificultadHieloRepository extends JpaRepository<DificultadHielo, String> {
    List<DificultadHielo> findAllByOrderByRankAsc();
}
