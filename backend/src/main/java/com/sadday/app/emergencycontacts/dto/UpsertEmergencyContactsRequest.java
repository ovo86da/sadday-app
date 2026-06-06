package com.sadday.app.emergencycontacts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpsertEmergencyContactsRequest(

        @NotEmpty(message = "Debe incluir al menos un contacto de emergencia")
        @Size(max = 2, message = "Se permiten máximo 2 contactos de emergencia")
        @Valid
        List<EmergencyContactRequest> contactos
) {}
