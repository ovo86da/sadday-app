package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.DificultadCiclismoStc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DificultadCiclismoStcRepository extends JpaRepository<DificultadCiclismoStc, String> {
    List<DificultadCiclismoStc> findAllByOrderByRankAsc();
}
