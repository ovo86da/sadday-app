package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.UpdateAccesoNivelRequest;
import com.sadday.app.mountains.entity.*;
import com.sadday.app.mountains.entity.AccesoNivelCambioPendiente.Estado;
import com.sadday.app.mountains.repository.AccesoNivelCambioPendienteRepository;
import com.sadday.app.mountains.repository.AccesoRutaPorNivelRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Qué se acepta y qué se rechaza al pedir un cambio de umbral.
 *
 * <p>Cubre las guardas de {@code solicitar()}, que son las que impiden que la
 * doble aprobación de FR-026 se vuelva ruido: un cambio que no cambia nada
 * gastaría la aprobación de otra persona para nada, y dos cambios a la vez
 * sobre el mismo nivel dejarían indeterminado cuál queda vigente.
 *
 * <p>El caso del duplicado existe por un fallo propio: capturar la violación
 * del índice único dentro de la misma transacción devolvía un 500 en vez de un
 * 409, porque la transacción ya estaba marcada para deshacerse. El test fija el
 * código de error, que es lo que el cliente ve y lo que se rompió.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CambioUmbralService — qué se acepta al pedir un cambio")
class CambioUmbralSolicitudTest {

    @Mock AccesoNivelCambioPendienteRepository cambioRepository;
    @Mock AccesoRutaPorNivelRepository         accesoRepository;
    @Mock MountainService                      mountainService;
    @Mock CambioUmbralMailSender               mailSender;

    @InjectMocks CambioUmbralService service;

    private static final String NIVEL = "ESCALADOR";
    private static final UUID   PIDE  = UUID.randomUUID();

    private ClasificacionSocio nivel;
    private AccesoRutaPorNivel vigente;

    // valores vigentes
    private final EscalaAlpinaIfas      ifasF    = EscalaAlpinaIfas.builder().id("F").grado("F").rank((short) 2).build();
    private final DificultadRoca        rocaIII  = DificultadRoca.builder().id("III").uiaa("III").rank((short) 3).build();
    private final DificultadHielo       wi2      = DificultadHielo.builder().id("WI2").grado("WI2").rank((short) 2).build();
    private final Compromiso            compI    = Compromiso.builder().id("I").tipo("I").rank((short) 1).build();
    private final SistemaClasesYosemite yds3     = SistemaClasesYosemite.builder().id("C3").tipo("Clase 3").rank((short) 3).build();
    private final SaddayRiesgoExigencia medio    = SaddayRiesgoExigencia.builder().id("MED").escala("Medio").rank((short) 3).build();
    // valores nuevos
    private final DificultadHielo       wi4      = DificultadHielo.builder().id("WI4").grado("WI4").rank((short) 4).build();
    private final DificultadCiclismoStc s1       = DificultadCiclismoStc.builder().id("S1").nombre("S1").rank((short) 2).build();

    @BeforeEach
    void setUp() {
        nivel = ClasificacionSocio.builder().id(NIVEL).nivel((short) 3).nombre("Escalador").build();

        // La matriz vigente: alpinismo puesto, senderismo y ciclismo sin fijar.
        vigente = AccesoRutaPorNivel.builder()
                .id((short) 1).nivelSocio(nivel)
                .maxIfas(ifasF).maxRoca(rocaIII).maxHielo(wi2)
                .maxCompromiso(compI).maxYosemite(yds3)
                .maxSaddayTecnico(medio).maxSaddayFisico(medio)
                .build();

        when(accesoRepository.findByNivelSocioId(NIVEL)).thenReturn(Optional.of(vigente));
        when(mountainService.findSocio(PIDE))
                .thenReturn(Socio.builder().id(PIDE).nombre("Ana").apellido("Pérez").build());

        // El servicio resuelve cada id por su escala; aquí se devuelve lo vigente
        // salvo que un test pida otra cosa.
        when(mountainService.findEscala("F")).thenReturn(ifasF);
        when(mountainService.findRoca("III")).thenReturn(rocaIII);
        when(mountainService.findHielo("WI2")).thenReturn(wi2);
        when(mountainService.findHielo("WI4")).thenReturn(wi4);
        when(mountainService.findCompromiso("I")).thenReturn(compI);
        when(mountainService.findYosemite("C3")).thenReturn(yds3);
        when(mountainService.findSadday("MED")).thenReturn(medio);
        when(mountainService.findSenderismoOpcional(null)).thenReturn(null);
        when(mountainService.findCiclismoOpcional(null)).thenReturn(null);
        when(mountainService.findCiclismoOpcional("S1")).thenReturn(s1);

        when(cambioRepository.save(any())).thenAnswer(i -> {
            AccesoNivelCambioPendiente c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
    }

    /** Lo vigente, tal cual, como petición. Los tests cambian solo lo que quieren probar. */
    private UpdateAccesoNivelRequest igualALoVigente() {
        return new UpdateAccesoNivelRequest("F", "III", "WI2", "I", "C3", "MED", "MED", null, null);
    }

    private BusinessException alSolicitar(UpdateAccesoNivelRequest req) {
        return assertThrows(BusinessException.class, () -> service.solicitar(NIVEL, req, PIDE));
    }

    // ── guardas ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un cambio que no cambia nada se rechaza, nulos incluidos")
    void sinCambiosSeRechaza() {
        BusinessException ex = alSolicitar(igualALoVigente());

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode(),
                "pedir los valores que ya están vigentes no es un cambio");
        verify(cambioRepository, never()).save(any());
        verify(mailSender, never()).avisarSolicitud(any());
    }

    @Test
    @DisplayName("senderismo y ciclismo sin fijar en los dos lados cuentan como iguales")
    void nulosEnAmbosLadosSonIguales() {
        // Si `mismoId` tratara dos nulos como distintos, esto pasaría la guarda
        // y se abriría un cambio vacío que alguien tendría que ir a aprobar.
        assertEquals(ErrorCode.VALIDATION_ERROR, alSolicitar(igualALoVigente()).getErrorCode());
    }

    @Test
    @DisplayName("fijar ciclismo, que estaba sin fijar, sí es un cambio")
    void fijarUnaEscalaSinFijarEsCambio() {
        UpdateAccesoNivelRequest req = new UpdateAccesoNivelRequest(
                "F", "III", "WI2", "I", "C3", "MED", "MED", null, "S1");

        service.solicitar(NIVEL, req, PIDE);

        ArgumentCaptor<AccesoNivelCambioPendiente> cap =
                ArgumentCaptor.forClass(AccesoNivelCambioPendiente.class);
        verify(cambioRepository).save(cap.capture());

        AccesoNivelCambioPendiente guardado = cap.getValue();
        assertEquals("S1", guardado.getMaxCiclismo().getId());
        assertNull(guardado.getMaxSenderismo(), "senderismo sigue sin fijar");
        assertEquals(Estado.PENDIENTE, guardado.getEstado(),
                "nace pendiente: lo tiene que aprobar otra persona");
        assertEquals(PIDE, guardado.getSolicitadoPor().getId());
        verify(mailSender).avisarSolicitud(guardado);
    }

    @Test
    @DisplayName("cambiar una escala de alpinismo también pasa la guarda")
    void cambiarAlpinismoEsCambio() {
        UpdateAccesoNivelRequest req = new UpdateAccesoNivelRequest(
                "F", "III", "WI4", "I", "C3", "MED", "MED", null, null);

        service.solicitar(NIVEL, req, PIDE);

        verify(cambioRepository).save(any());
        verify(mailSender).avisarSolicitud(any());
    }

    @Test
    @DisplayName("si ya hay uno pendiente para ese nivel → 409, no 500")
    void duplicadoDaConflicto() {
        AccesoNivelCambioPendiente otro = AccesoNivelCambioPendiente.builder()
                .id(UUID.randomUUID()).nivelSocio(nivel).estado(Estado.PENDIENTE)
                .solicitadoPor(Socio.builder().id(UUID.randomUUID()).nombre("Luis").apellido("Gómez").build())
                .build();
        when(cambioRepository.findByNivelSocioIdAndEstado(NIVEL, Estado.PENDIENTE))
                .thenReturn(Optional.of(otro));

        UpdateAccesoNivelRequest req = new UpdateAccesoNivelRequest(
                "F", "III", "WI4", "I", "C3", "MED", "MED", null, null);
        BusinessException ex = alSolicitar(req);

        assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode(),
                "se comprueba antes de insertar; dejarlo al índice daba un 500");
        assertTrue(ex.getMessage().contains("Luis"),
                "quien consulta necesita saber quién lo pidió: " + ex.getMessage());
        verify(cambioRepository, never()).save(any());
    }

    @Test
    @DisplayName("un nivel sin matriz vigente no se puede cambiar")
    void nivelInexistente() {
        when(accesoRepository.findByNivelSocioId("NO_EXISTE")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.solicitar("NO_EXISTE", igualALoVigente(), PIDE));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        verify(cambioRepository, never()).save(any());
    }
}
