-- =============================================================================
-- V23 — La matriz cubre los cuatro tipos, y la excepcion al inscribirse (FR-028)
--
-- `acceso_ruta_por_nivel` se escribio para alpinismo: sus siete columnas encajan
-- exactamente con las siete escalas de `rutas_alpinismo`. Escalada comparte solo
-- la de roca, y trekking y ciclismo no comparten NINGUNA.
--
-- Conectar la matriz sin extenderla habria dejado la validacion ausente en dos
-- de los cuatro tipos, sin avisar. Decir «el sistema valida el nivel» cuando
-- solo lo hace en alpinismo es peor que no decirlo, porque la gente confia.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Catalogo de dificultad tecnica de ciclismo
--
-- Hoy `rutas_ciclismo.dificultad_tecnica` es un varchar(5) con un CHECK de
-- S0..S4 y sin orden explicito. Las otras seis escalas son catalogos con `rank`,
-- y comparar necesita ese orden: deducirlo del texto («S3 va despues de S2»)
-- funciona hasta que alguien añade un grado que no encaja con el alfabeto.
--
-- Los ids son los mismos valores que ya guarda la columna, asi que
-- `rutas_ciclismo` sigue funcionando sin tocarla. Convertirla en clave foranea
-- es un cambio pequeño y aparte, no hace falta para esto.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE public.dificultad_ciclismo_stc (
    id character varying(5) NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion text,
    rank smallint NOT NULL,
    CONSTRAINT dificultad_ciclismo_stc_pkey PRIMARY KEY (id)
);

INSERT INTO public.dificultad_ciclismo_stc (id, nombre, descripcion, rank) VALUES
    ('S0', 'S0 — sin dificultad', 'Camino firme, sin obstaculos. Rodable por cualquiera.', 1),
    ('S1', 'S1 — facil',          'Raices y piedras pequeñas, pendiente suave.',             2),
    ('S2', 'S2 — media',          'Raices y piedras grandes, escalones, curvas cerradas.',   3),
    ('S3', 'S3 — dificil',        'Bloques, escalones altos, pendiente fuerte y expuesta.',  4),
    ('S4', 'S4 — muy dificil',    'Terreno extremo; a menudo hay que bajarse de la bici.',   5);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Las dos columnas nuevas en la matriz, y en los cambios pendientes
--
-- Nulas a proposito: hoy nadie ha fijado esos limites y poner un valor por
-- defecto seria inventarselo. Un default permisivo abre la puerta que esto
-- cierra; uno restrictivo bloquea salidas de trekking desde el primer dia.
--
-- Mientras esten nulas, el sistema NO puede decir si una ruta de ese tipo
-- excede el nivel, y eso se le dice a quien aprueba en vez de dejarlo pasar en
-- silencio: lo que no se puede determinar cuenta como aviso, nunca como
-- visto bueno.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.acceso_ruta_por_nivel
    ADD COLUMN max_senderismo_id character varying(10),
    ADD COLUMN max_ciclismo_id   character varying(5);

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_max_senderismo_fkey FOREIGN KEY (max_senderismo_id)
        REFERENCES public.dificultad_senderismo(id);
ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_max_ciclismo_fkey FOREIGN KEY (max_ciclismo_id)
        REFERENCES public.dificultad_ciclismo_stc(id);

-- Los cambios pendientes de FR-026 llevan los mismos campos: cambiar el maximo
-- de senderismo de un nivel es cambiar un umbral de acceso como cualquier otro
-- y pasa por la misma doble aprobacion.
ALTER TABLE public.acceso_nivel_cambio_pendiente
    ADD COLUMN max_senderismo_id character varying(10),
    ADD COLUMN max_ciclismo_id   character varying(5);

ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_senderismo_fkey FOREIGN KEY (max_senderismo_id)
        REFERENCES public.dificultad_senderismo(id);
ALTER TABLE ONLY public.acceso_nivel_cambio_pendiente
    ADD CONSTRAINT anc_max_ciclismo_fkey FOREIGN KEY (max_ciclismo_id)
        REFERENCES public.dificultad_ciclismo_stc(id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. La excepcion: un directivo aprueba aunque el nivel no de
--
-- No se bloquea al socio en la puerta —puede apuntarse— y lo que se detiene es
-- la aprobacion. Si el directivo aprueba igualmente, queda constancia.
--
-- El motivo es obligatorio: una excepcion sin motivo es indistinguible de un
-- descuido, y es justo lo que luego hay que poder revisar.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.salida_participantes
    ADD COLUMN excepcion_nivel_por_id uuid,
    ADD COLUMN excepcion_nivel_en timestamp without time zone,
    ADD COLUMN excepcion_nivel_motivo text;

ALTER TABLE ONLY public.salida_participantes
    ADD CONSTRAINT sp_excepcion_nivel_por_fkey FOREIGN KEY (excepcion_nivel_por_id)
        REFERENCES public.socios(id);

ALTER TABLE public.salida_participantes
    ADD CONSTRAINT chk_excepcion_nivel CHECK (
        (excepcion_nivel_por_id IS NULL
            AND excepcion_nivel_en IS NULL
            AND excepcion_nivel_motivo IS NULL)
        OR (excepcion_nivel_por_id IS NOT NULL
            AND excepcion_nivel_en IS NOT NULL
            AND excepcion_nivel_motivo IS NOT NULL
            AND btrim(excepcion_nivel_motivo) <> ''));

CREATE INDEX idx_sp_excepcion_nivel
    ON public.salida_participantes (excepcion_nivel_por_id)
    WHERE excepcion_nivel_por_id IS NOT NULL;

COMMENT ON COLUMN public.salida_participantes.excepcion_nivel_motivo IS
    'Por que se aprobo la inscripcion pese a que el nivel del socio no alcanza '
    'la dificultad de la ruta (FR-028). Obligatorio si hay excepcion.';
