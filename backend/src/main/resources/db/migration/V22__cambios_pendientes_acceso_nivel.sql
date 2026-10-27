-- =============================================================================
-- V22 — Cambios pendientes de aprobación en los umbrales de acceso (FR-026)
--
-- `acceso_ruta_por_nivel` dice que dificultad maxima permite cada nivel de
-- socio. Hasta ahora cualquier directivo la cambiaba actuando solo: quedaba
-- auditado, pero nada lo impedia en el momento. No es un permiso administrativo
-- cualquiera — si alguien relaja un limite, socios que antes no podian
-- inscribirse a cierta ruta de pronto pueden, y la auditoria lo cuenta DESPUES.
--
-- A partir de aqui el cambio se queda esperando en esta tabla y no toca
-- `acceso_ruta_por_nivel` hasta que otro directivo lo aprueba. Mientras tanto el
-- umbral viejo sigue vigente, que es lo seguro: un umbral relajado sin aprobar
-- es justo el riesgo que esto evita.
-- =============================================================================

CREATE TABLE public.acceso_nivel_cambio_pendiente (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nivel_socio_id character varying(10) NOT NULL,

    -- Los siete valores propuestos. Se guardan TODOS aunque solo cambie uno:
    -- la unidad que maneja la API y la que ve quien edita es la fila entera de
    -- un nivel, y guardar solo lo que cambia obligaria a reconstruir el resto
    -- al aprobar, contra un presente que puede haberse movido.
    max_ifas_id            character varying(10) NOT NULL,
    max_roca_id            character varying(15) NOT NULL,
    max_hielo_id           character varying(10) NOT NULL,
    max_compromiso_id      character varying(10) NOT NULL,
    max_yosemite_id        character varying(10) NOT NULL,
    max_sadday_tecnico_id  character varying(10) NOT NULL,
    max_sadday_fisico_id   character varying(10) NOT NULL,

    solicitado_por_id uuid NOT NULL,
    solicitado_en timestamp without time zone DEFAULT now() NOT NULL,

    estado character varying(12) DEFAULT 'PENDIENTE' NOT NULL,
    resuelto_por_id uuid,
    resuelto_en timestamp without time zone,
    motivo_rechazo text,

    CONSTRAINT acceso_nivel_cambio_pendiente_pkey PRIMARY KEY (id),

    CONSTRAINT chk_estado_cambio CHECK (
        estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'CANCELADO')),

    -- Coherencia entre el estado y quien lo resolvio, al estilo del
    -- `chk_aprobacion` que ya usan las rutas: un cambio resuelto sin constancia
    -- de quien y cuando es indistinguible de uno que nadie miro.
    CONSTRAINT chk_resolucion_cambio CHECK (
        (estado = 'PENDIENTE' AND resuelto_por_id IS NULL AND resuelto_en IS NULL)
        OR (estado <> 'PENDIENTE' AND resuelto_por_id IS NOT NULL AND resuelto_en IS NOT NULL)),

    -- Un rechazo sin motivo no le dice nada a quien lo pidio.
    CONSTRAINT chk_motivo_rechazo CHECK (
        estado <> 'RECHAZADO' OR (motivo_rechazo IS NOT NULL AND btrim(motivo_rechazo) <> ''))
);

ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_nivel_socio_fkey FOREIGN KEY (nivel_socio_id)
        REFERENCES public.clasificacion_socio(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_ifas_fkey FOREIGN KEY (max_ifas_id)
        REFERENCES public.escala_alpina_ifas(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_roca_fkey FOREIGN KEY (max_roca_id)
        REFERENCES public.dificultad_roca_uiaa_francesa(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_hielo_fkey FOREIGN KEY (max_hielo_id)
        REFERENCES public.dificultad_hielo_wi(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_compromiso_fkey FOREIGN KEY (max_compromiso_id)
        REFERENCES public.compromiso(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_yosemite_fkey FOREIGN KEY (max_yosemite_id)
        REFERENCES public.sistema_clases_yosemite(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_sadday_tecnico_fkey FOREIGN KEY (max_sadday_tecnico_id)
        REFERENCES public.sadday_riesgo_exigencia(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_sadday_fisico_fkey FOREIGN KEY (max_sadday_fisico_id)
        REFERENCES public.sadday_riesgo_exigencia(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_solicitado_por_fkey FOREIGN KEY (solicitado_por_id)
        REFERENCES public.socios(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_resuelto_por_fkey FOREIGN KEY (resuelto_por_id)
        REFERENCES public.socios(id);

-- Un solo cambio pendiente por nivel, garantizado por la base y no por el
-- servicio. Permitir una cola invitaria a aprobar el de arriba sin ver que
-- viene otro detras; y una comprobacion en Java se la salta cualquier segunda
-- peticion que llegue a la vez.
CREATE UNIQUE INDEX idx_anc_un_pendiente_por_nivel
    ON public.acceso_nivel_cambio_pendiente (nivel_socio_id)
    WHERE estado = 'PENDIENTE';

CREATE INDEX idx_anc_pendientes
    ON public.acceso_nivel_cambio_pendiente (solicitado_en)
    WHERE estado = 'PENDIENTE';

COMMENT ON TABLE public.acceso_nivel_cambio_pendiente IS
    'Cambios de umbral esperando la aprobacion de un segundo directivo (FR-026). '
    'Mientras hay uno pendiente, acceso_ruta_por_nivel conserva el valor vigente.';
