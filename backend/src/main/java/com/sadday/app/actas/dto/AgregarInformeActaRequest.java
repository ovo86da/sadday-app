package com.sadday.app.actas.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AgregarInformeActaRequest(@NotNull UUID informeId) {}
