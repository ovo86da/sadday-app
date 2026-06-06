-- =============================================================================
-- V16__drop_emergency_contact_columns.sql
-- Elimina las 6 columnas inline de contactos de emergencia de la tabla socios.
-- Seguro porque el código Java ya no las referencia (reemplazadas por
-- socio_emergency_contacts en V12, módulo EmergencyContactService en Fase 3).
-- =============================================================================

ALTER TABLE public.socios
    DROP COLUMN IF EXISTS emergency_contact_name,
    DROP COLUMN IF EXISTS emergency_contact_phone,
    DROP COLUMN IF EXISTS emergency_contact_direccion,
    DROP COLUMN IF EXISTS emergency_contact_name2,
    DROP COLUMN IF EXISTS emergency_contact_phone2,
    DROP COLUMN IF EXISTS emergency_contact_direccion2;
