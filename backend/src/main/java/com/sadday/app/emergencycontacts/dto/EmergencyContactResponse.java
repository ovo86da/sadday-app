package com.sadday.app.emergencycontacts.dto;

import java.util.UUID;

public record EmergencyContactResponse(
        UUID   id,
        short  orden,
        String nombreCompleto,
        String relacion,
        String celular,
        String direccion
) {}
