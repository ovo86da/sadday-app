-- =============================================================================
-- V23 — La matriz cubre los cuatro tipos de ruta (FR-028, 1.ª entrega)
--
-- `acceso_ruta_por_nivel` se escribio para alpinismo: sus siete columnas encajan
-- exactamente con las siete escalas de `rutas_alpinismo`. Escalada comparte solo
-- la de roca, y trekking y ciclismo no comparten NINGUNA.
--
-- Conectar la matriz sin extenderla habria dejado la validacion ausente en dos
-- de los cuatro tipos, sin avisar. Decir «el sistema valida el nivel» cuando
-- solo lo hace en alpinismo es peor que no decirlo, porque la gente confia.
--
-- Esto NO toca `salida_participantes`: la excepcion al inscribirse ya esta
-- modelada desde V1 y mejor de lo que yo la habia planteado —dos aprobadores
-- (Directivo Y Jefe de Salida), un motivo por cada uno, y revocacion—. Ver el
-- registro del FR-028.
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
