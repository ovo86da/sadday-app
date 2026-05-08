package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.Compromiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompromisoRepository extends JpaRepository<Compromiso, String> {
    List<Compromiso> findAllByOrderByRankAsc();
}
