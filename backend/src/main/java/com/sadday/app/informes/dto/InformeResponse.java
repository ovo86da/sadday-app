package com.sadday.app.informes.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record InformeResponse(
        UUID id,
        UUID salidaId,
        String salidaNombre,
        Boolean seRealizo,
        Boolean lograronCumbre,
        String condicionesMeterologicas,
        LocalTime horaSalidaClub,
        LocalTime horaLlegadaMontana,
        LocalTime horaCumbre,
        LocalTime horaInicioDescenso,
        LocalTime horaLlegadaAutos,
        LocalTime horaRegresoClub,
        String cronica,
        String observaciones,
        String comentariosVarios,

        List<SegmentoViajeResponse> segmentos,

        Boolean alquiloGuia,
        BigDecimal costoGuia,
        Integer contactoGuiaId,
        String contactoGuiaNombre,
        String contactoGuiaTelefono,
        String contactoGuiaCorreo,

        Boolean alquiloRefugio,
        String nombreRefugio,
        BigDecimal costoRefugio,
        Integer contactoRefugioId,
        String contactoRefugioNombre,

        Boolean acampo,
        String nombreCamping,
        BigDecimal costoCamping,
        Integer contactoCampingId,
        String contactoCampingNombre,

        String dondeAutos,
        String autosDescripcion,
        String autosLinkUbicacion,
        BigDecimal costoParqueadero,

        BigDecimal costoTotal,
        BigDecimal costoPorPersona,
        UUID validadoPorId,
        String validadoPorNombre,
        LocalDateTime validadoEn,
        UUID documentoId,
        String documentoFilename,
        List<ReconocimientoResponse> reconocimientos,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
