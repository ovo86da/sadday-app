package com.sadday.app.notificaciones.service;

import com.sadday.app.notificaciones.dto.CumpleanosItem;
import com.sadday.app.notificaciones.dto.CumpleanosResponse;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Servicio de notificaciones del club.
 *
 * <p>Por ahora expone únicamente la alerta de cumpleaños diaria,
 * que el frontend consulta al iniciar sesión o al cargar la pantalla principal.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificacionService {

    private final SocioRepository socioRepository;

    /**
     * Devuelve los socios con cumpleaños hoy (mismo mes y día).
     * Excluye Ex-socios y socios Pendiente Registro.
     * Accesible a cualquier usuario autenticado.
     */
    @PreAuthorize("isAuthenticated()")
    public CumpleanosResponse cumpleanosHoy() {
        LocalDate hoy = LocalDate.now();
        List<Socio> socios = socioRepository.findCumpleanosHoy(hoy.getMonthValue(), hoy.getDayOfMonth());

        List<CumpleanosItem> items = socios.stream()
                .map(s -> new CumpleanosItem(
                        s.getId(),
                        s.getNombre(),
                        s.getApellido(),
                        Period.between(s.getFechaNacimiento(), hoy).getYears()))
                .toList();

        return new CumpleanosResponse(hoy, items.size(), items);
    }
}
