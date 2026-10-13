package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.RutaEnlaceRequest;
import com.sadday.app.mountains.dto.RutaEnlaceResponse;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.entity.RutaEnlace;
import com.sadday.app.mountains.repository.RutaEnlaceRepository;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Enlaces externos de una ruta: Wikiloc, GaiaGPS, Komoot, fichas de parque…
 *
 * <p>Sustituye a {@code rutas.track_url} y {@code rutas.documentacion_url}, que
 * admitían un enlace cada uno y no servían para guardar varias apps a la vez.
 *
 * <h2>Ventana de transición</h2>
 *
 * Las dos columnas antiguas siguen en la base —se eliminan en una migración
 * posterior, una vez verificada la copia— y, lo que importa más, <strong>los
 * clientes siguen escribiéndolas</strong>: el formulario de rutas de mobile
 * manda {@code trackUrl} y {@code documentacionUrl}. Si el backend leyera solo
 * de la tabla nueva, lo que mobile escribiese no aparecería en ningún sitio.
 *
 * <p>Por eso {@link #sincronizarLegacy} mantiene las dos representaciones
 * alineadas: se lee de {@code ruta_enlace}, y lo que llegue por los campos
 * antiguos se refleja ahí. Cuando mobile y el frontend usen los enlaces
 * directamente, esto y las columnas se van juntos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RutaEnlaceService {

    /** Etiquetas con las que la migración V20 copió los dos campos antiguos. */
    private static final String ETIQUETA_TRACK         = "Track";
    private static final String ETIQUETA_DOCUMENTACION = "Documentación";

    private final RutaEnlaceRepository rutaEnlaceRepository;
    private final RutaRepository       rutaRepository;

    @Transactional(readOnly = true)
    public List<RutaEnlaceResponse> listarPorRuta(Integer rutaId) {
        return rutaEnlaceRepository.findByRutaIdOrderByOrdenAscIdAsc(rutaId)
                .stream()
                .map(RutaEnlaceService::toResponse)
                .toList();
    }

    @PreAuthorize("isAuthenticated()")
    public RutaEnlaceResponse crear(Integer rutaId, RutaEnlaceRequest req) {
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_NOT_FOUND));

        RutaEnlace enlace = rutaEnlaceRepository.save(RutaEnlace.builder()
                .ruta(ruta)
                .etiqueta(req.etiqueta().trim())
                .url(req.url().trim())
                .orden(req.orden() != null ? req.orden() : (short) 0)
                .build());

        log.info("Enlace añadido a ruta: rutaId={}, enlaceId={}, etiqueta={}",
                rutaId, enlace.getId(), enlace.getEtiqueta());
        return toResponse(enlace);
    }

    @PreAuthorize("isAuthenticated()")
    public RutaEnlaceResponse actualizar(Integer rutaId, Long enlaceId, RutaEnlaceRequest req) {
        RutaEnlace enlace = rutaEnlaceRepository.findByIdAndRutaId(enlaceId, rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Enlace no encontrado en esta ruta"));

        enlace.setEtiqueta(req.etiqueta().trim());
        enlace.setUrl(req.url().trim());
        if (req.orden() != null) enlace.setOrden(req.orden());

        log.info("Enlace actualizado: rutaId={}, enlaceId={}", rutaId, enlaceId);
        return toResponse(rutaEnlaceRepository.save(enlace));
    }

    @PreAuthorize("isAuthenticated()")
    public void eliminar(Integer rutaId, Long enlaceId) {
        RutaEnlace enlace = rutaEnlaceRepository.findByIdAndRutaId(enlaceId, rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Enlace no encontrado en esta ruta"));
        rutaEnlaceRepository.delete(enlace);
        log.info("Enlace eliminado: rutaId={}, enlaceId={}", rutaId, enlaceId);
    }

    /**
     * Refleja en {@code ruta_enlace} lo que llegue por los campos antiguos.
     *
     * <p>Se llama al crear y al actualizar una ruta. Un valor vacío o nulo
     * borra el enlace correspondiente, de modo que vaciar el campo en mobile
     * también lo quita de la lista.
     *
     * <p>Solo toca los enlaces con las etiquetas que usó la migración; los que
     * el socio haya añadido a mano no se rozan.
     */
    public void sincronizarLegacy(Ruta ruta, String trackUrl, String documentacionUrl) {
        upsertLegacy(ruta, ETIQUETA_TRACK, trackUrl, (short) 1);
        upsertLegacy(ruta, ETIQUETA_DOCUMENTACION, documentacionUrl, (short) 2);
    }

    private void upsertLegacy(Ruta ruta, String etiqueta, String url, short orden) {
        List<RutaEnlace> existentes = rutaEnlaceRepository
                .findByRutaIdOrderByOrdenAscIdAsc(ruta.getId())
                .stream()
                .filter(e -> etiqueta.equals(e.getEtiqueta()))
                .toList();

        boolean vacio = url == null || url.isBlank();

        if (vacio) {
            existentes.forEach(rutaEnlaceRepository::delete);
            return;
        }

        if (existentes.isEmpty()) {
            rutaEnlaceRepository.save(RutaEnlace.builder()
                    .ruta(ruta).etiqueta(etiqueta).url(url.trim()).orden(orden).build());
        } else {
            RutaEnlace primero = existentes.get(0);
            primero.setUrl(url.trim());
            rutaEnlaceRepository.save(primero);
            existentes.stream().skip(1).forEach(rutaEnlaceRepository::delete);
        }
    }

    private static RutaEnlaceResponse toResponse(RutaEnlace e) {
        return new RutaEnlaceResponse(e.getId(), e.getEtiqueta(), e.getUrl(), e.getOrden());
    }
}
