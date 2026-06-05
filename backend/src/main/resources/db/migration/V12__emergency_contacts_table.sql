-- =============================================================================
-- V12__emergency_contacts_table.sql
-- Crea tabla separada para contactos de emergencia y migra los datos
-- existentes desde las columnas inline de socios.
-- Las columnas originales (emergency_contact_*) NO se eliminan aquí;
-- el DROP ocurre en V16 junto con la actualización del código Java.
-- =============================================================================

CREATE TABLE public.socio_emergency_contacts (
    id              uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id        uuid NOT NULL,
    orden           smallint NOT NULL,
    nombre_completo character varying(200) NOT NULL,
    relacion        character varying(100) NOT NULL,
    celular         character varying(20),
    direccion       text,
    created_at      timestamp without time zone DEFAULT now() NOT NULL,
    updated_at      timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT socio_emergency_contacts_pkey PRIMARY KEY (id),
    CONSTRAINT socio_emergency_contacts_socio_orden_key UNIQUE (socio_id, orden),
    CONSTRAINT chk_sec_orden CHECK (orden IN (1, 2)),
    CONSTRAINT fk_sec_socio FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE
);

CREATE INDEX idx_sec_socio_id ON public.socio_emergency_contacts (socio_id);

-- ---------------------------------------------------------------------------
-- Migración de datos existentes
-- Se usa 'No especificada' como valor por defecto para el campo relacion,
-- que es nuevo y no existía en el modelo anterior.
-- Solo se migran contactos que tengan al menos nombre registrado.
-- ---------------------------------------------------------------------------

INSERT INTO public.socio_emergency_contacts (socio_id, orden, nombre_completo, relacion, celular, direccion)
SELECT
    id,
    1,
    emergency_contact_name,
    'No especificada',
    emergency_contact_phone,
    emergency_contact_direccion
FROM public.socios
WHERE emergency_contact_name IS NOT NULL AND trim(emergency_contact_name) <> '';

INSERT INTO public.socio_emergency_contacts (socio_id, orden, nombre_completo, relacion, celular, direccion)
SELECT
    id,
    2,
    emergency_contact_name2,
    'No especificada',
    emergency_contact_phone2,
    emergency_contact_direccion2
FROM public.socios
WHERE emergency_contact_name2 IS NOT NULL AND trim(emergency_contact_name2) <> '';
