package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.EscalaAlpinaIfas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscalaAlpinaIfasRepository extends JpaRepository<EscalaAlpinaIfas, String> {
    List<EscalaAlpinaIfas> findAllByOrderByRankAsc();
}
