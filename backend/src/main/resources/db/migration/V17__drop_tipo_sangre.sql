-- =============================================================================
-- V17__drop_tipo_sangre.sql
-- Elimina la columna tipo_sangre de socios.
-- Los datos ya fueron migrados a socio_medical_info.blood_type en V13.
-- =============================================================================

ALTER TABLE public.socios
    DROP COLUMN IF EXISTS tipo_sangre;
