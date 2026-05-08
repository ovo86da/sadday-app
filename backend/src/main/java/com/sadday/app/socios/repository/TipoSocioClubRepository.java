package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.TipoSocioClub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoSocioClubRepository extends JpaRepository<TipoSocioClub, Short> {
    Optional<TipoSocioClub> findByNombre(String nombre);
}
