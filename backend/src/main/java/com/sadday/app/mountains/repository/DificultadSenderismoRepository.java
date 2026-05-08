package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.DificultadSenderismo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DificultadSenderismoRepository extends JpaRepository<DificultadSenderismo, String> {
    List<DificultadSenderismo> findAllByOrderByRankAsc();
}
