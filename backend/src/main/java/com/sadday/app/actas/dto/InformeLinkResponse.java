package com.sadday.app.actas.dto;

import java.util.UUID;

public record InformeLinkResponse(Long id, UUID informeId, UUID salidaId, String salidaNombre) {}
