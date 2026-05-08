package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.entity.Contacto;
import com.sadday.app.mountains.entity.ContactoRuta;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.repository.ContactoRepository;
import com.sadday.app.mountains.repository.ContactoRutaRepository;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContactoService {

    private final ContactoRepository contactoRepository;
    private final ContactoRutaRepository contactoRutaRepository;
    private final RutaRepository rutaRepository;

    // ── CRUD global contactos ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<GlobalContactoResponse> listar(String q, Pageable pageable) {
        return contactoRepository.buscar(q, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<GlobalContactoResponse> buscarSugerencias(String q) {
        return contactoRepository.buscarSugerencias(q, PageRequest.of(0, 10))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public GlobalContactoResponse obtener(Integer id) {
        return toResponse(findById(id));
    }

    @PreAuthorize("isAuthenticated()")
    public GlobalContactoResponse crear(CreateGlobalContactoRequest request) {
        if (request.telefono() != null && !request.telefono().isBlank()) {
            contactoRepository.findByTelefono(request.telefono()).ifPresent(c -> {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                        "Ya existe un contacto con teléfono " + request.telefono());
            });
        }
        Contacto c = Contacto.builder()
                .nombre(request.nombre())
                .telefono(request.telefono() != null && !request.telefono().isBlank() ? request.telefono() : null)
                .correo(request.correo())
                .notas(request.notas())
                .build();
        return toResponse(contactoRepository.save(c));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public GlobalContactoResponse actualizar(Integer id, UpdateGlobalContactoRequest request) {
        Contacto c = findById(id);
        if (request.telefono() != null && !request.telefono().isBlank()
                && !request.telefono().equals(c.getTelefono())) {
            contactoRepository.findByTelefono(request.telefono()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                            "Ya existe un contacto con teléfono " + request.telefono());
                }
            });
        }
        c.setNombre(request.nombre());
        c.setTelefono(request.telefono() != null && !request.telefono().isBlank() ? request.telefono() : null);
        c.setCorreo(request.correo());
        c.setNotas(request.notas());
        return toResponse(contactoRepository.save(c));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public void eliminar(Integer id) {
        findById(id);
        contactoRepository.deleteById(id);
    }

    // ── Upsert por teléfono (usado por InformeService) ───────────────────

    /**
     * Busca o crea un contacto por teléfono (deduplicación).
     * Si teléfono es null, crea un nuevo contacto.
     */
    public Contacto upsertByPhone(String nombre, String telefono, String correo) {
        if (telefono != null && !telefono.isBlank()) {
            return contactoRepository.findByTelefono(telefono)
                    .orElseGet(() -> contactoRepository.save(
                            Contacto.builder()
                                    .nombre(nombre)
                                    .telefono(telefono)
                                    .correo(correo)
                                    .build()
                    ));
        }
        return contactoRepository.save(
                Contacto.builder().nombre(nombre).correo(correo).build()
        );
    }

    /**
     * Vincula un contacto a una ruta en la tabla contactos_rutas (idempotente).
     */
    public void vincularARuta(Integer contactoId, Integer rutaId, String tipoContacto) {
        Contacto contacto = findById(contactoId);
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_NOT_FOUND));

        boolean yaVinculado = contactoRutaRepository.findActivosByRutaId(rutaId)
                .stream()
                .anyMatch(cr -> cr.getContacto().getId().equals(contactoId)
                        && tipoContacto.equals(cr.getTipoContacto()));

        if (!yaVinculado) {
            ContactoRuta cr = ContactoRuta.builder()
                    .contacto(contacto)
                    .ruta(ruta)
                    .tipoContacto(tipoContacto)
                    .activo(true)
                    .build();
            contactoRutaRepository.save(cr);
        }
    }

    // ── Contactos de una ruta ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ContactoRutaResponse> listarContactosRuta(Integer rutaId) {
        return contactoRutaRepository.findActivosByRutaId(rutaId)
                .stream().map(this::toContactoRutaResponse).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public ContactoRutaResponse vincular(Integer rutaId, VincularContactoRutaRequest request) {
        Contacto contacto = findById(request.contactoId());
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_NOT_FOUND));

        ContactoRuta cr = ContactoRuta.builder()
                .contacto(contacto)
                .ruta(ruta)
                .tipoContacto(request.tipoContacto())
                .activo(true)
                .build();
        return toContactoRutaResponse(contactoRutaRepository.save(cr));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void desvincular(Integer rutaId, Integer contactoRutaId) {
        ContactoRuta cr = contactoRutaRepository.findById(contactoRutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Vínculo no encontrado"));
        if (!cr.getRuta().getId().equals(rutaId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "El vínculo no pertenece a esta ruta");
        }
        cr.setActivo(false);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    public Contacto findById(Integer id) {
        return contactoRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Contacto no encontrado: " + id));
    }

    private GlobalContactoResponse toResponse(Contacto c) {
        List<String> tipos = contactoRutaRepository.findTiposActivosByContactoId(c.getId());
        return new GlobalContactoResponse(c.getId(), c.getNombre(), c.getTelefono(),
                c.getCorreo(), c.getNotas(), tipos, c.getCreatedAt(), c.getUpdatedAt());
    }

    private ContactoRutaResponse toContactoRutaResponse(ContactoRuta cr) {
        return new ContactoRutaResponse(
                cr.getId(),
                cr.getContacto().getId(),
                cr.getContacto().getNombre(),
                cr.getContacto().getTelefono(),
                cr.getContacto().getCorreo(),
                cr.getTipoContacto(),
                cr.getActivo()
        );
    }
}
