package com.sadday.app.shared.repository;

import com.sadday.app.shared.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
}
