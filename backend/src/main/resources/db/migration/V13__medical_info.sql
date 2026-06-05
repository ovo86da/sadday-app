-- =============================================================================
-- V13__medical_info.sql
-- Crea tabla separada para información médica mínima de los socios y migra
-- el campo tipo_sangre existente.
-- La columna original (tipo_sangre) NO se elimina aquí;
-- el DROP ocurre en V17 junto con la actualización del código Java.
-- =============================================================================

CREATE TABLE public.socio_medical_info (
    id                              uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id                        uuid NOT NULL,
    blood_type                      character varying(5),
    has_relevant_allergies          boolean NOT NULL DEFAULT false,
    allergies_detail                text,
    has_relevant_medical_condition  boolean NOT NULL DEFAULT false,
    medical_condition_detail        text,
    uses_emergency_medication       boolean NOT NULL DEFAULT false,
    emergency_medication_detail     text,
    additional_notes                text,
    created_at                      timestamp without time zone DEFAULT now() NOT NULL,
    updated_at                      timestamp without time zone DEFAULT now() NOT NULL,
    deleted_at                      timestamp without time zone,
    CONSTRAINT socio_medical_info_pkey PRIMARY KEY (id),
    CONSTRAINT socio_medical_info_socio_id_key UNIQUE (socio_id),
    CONSTRAINT fk_smi_socio FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Migración de tipo_sangre existente.
-- Solo se crean filas para socios que ya tenían tipo de sangre registrado.
-- Los campos booleanos se inicializan en false (respuesta "no") como base;
-- el socio deberá completar su información médica en el nuevo flujo.
-- ---------------------------------------------------------------------------

INSERT INTO public.socio_medical_info (socio_id, blood_type, has_relevant_allergies, has_relevant_medical_condition, uses_emergency_medication)
SELECT
    id,
    tipo_sangre,
    false,
    false,
    false
FROM public.socios
WHERE tipo_sangre IS NOT NULL AND trim(tipo_sangre) <> '';
