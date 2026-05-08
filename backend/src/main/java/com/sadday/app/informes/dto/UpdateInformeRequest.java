package com.sadday.app.informes.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record UpdateInformeRequest(
        Boolean seRealizo,
        Boolean lograronCumbre,
        @Size(max = 2000) String condicionesMeterologicas,
        LocalTime horaSalidaClub,
        LocalTime horaLlegadaMontana,
        LocalTime horaCumbre,
        LocalTime horaInicioDescenso,
        LocalTime horaLlegadaAutos,
        LocalTime horaRegresoClub,
        @Size(max = 5000) String cronica,
        @Size(max = 2000) String observaciones,
        @Size(max = 2000) String comentariosVarios,

        /** Si se proporcionan segmentos, reemplazan todos los existentes. */
        List<SegmentoViajeRequest> segmentos,

        @NotNull Boolean alquiloGuia,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal costoGuia,
        Integer contactoGuiaId,
        UUID guiaSocioId,

        Boolean alquiloRefugio,
        @Size(max = 200) String nombreRefugio,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal costoRefugio,
        Integer contactoRefugioId,

        Boolean acampo,
        @Size(max = 200) String nombreCamping,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal costoCamping,
        Integer contactoCampingId,

        // Autos
        @Pattern(
            regexp = "^(NO_AUTOS|PARQUEADERO_SEGURO|PARQUEADERO_INSEGURO|BASE_MONTANA|CALLE_SEGURO|CALLE_INSEGURO)?$",
            message = "Valor inválido para dondeAutos"
        ) String dondeAutos,
        @Size(max = 300) String autosDescripcion,
        @Pattern(
            regexp = "^(https?://[^\\s]{1,490})?$",
            message = "El link debe comenzar con http:// o https://"
        ) String autosLinkUbicacion,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal costoParqueadero,

        // Costo total del viaje
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal costoTotal,

        // Costo por persona
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal costoPorPersona
) {}
