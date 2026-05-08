package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.DificultadRoca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DificultadRocaRepository extends JpaRepository<DificultadRoca, String> {
    List<DificultadRoca> findAllByOrderByRankAsc();
}
