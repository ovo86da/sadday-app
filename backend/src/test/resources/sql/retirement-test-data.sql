-- =============================================================================
-- Datos de prueba para el módulo de retiro de socio (Fase 7)
-- Depende de: socios-test-data.sql
--
-- Socios:
--   Admin    (ADMIN):     00000000-0000-4000-b000-000000000001
--   Socio    (SOCIO):     00000000-0000-4000-b000-000000000003  → sin deuda
--   Directivo(DIRECTIVO): 00000000-0000-4000-b000-000000000002  → con deuda pendiente
-- =============================================================================

-- Contactos de emergencia para el socio (003)
INSERT INTO public.socio_emergency_contacts (id, socio_id, orden, nombre_completo, relacion, celular, created_at, updated_at)
VALUES
    ('eeee0001-0000-4000-b000-000000000003', '00000000-0000-4000-b000-000000000003', 1, 'Contacto A', 'Familiar', '0993333333', now(), now()),
    ('eeee0002-0000-4000-b000-000000000003', '00000000-0000-4000-b000-000000000003', 2, 'Contacto B', 'Amigo',    '0994444444', now(), now());

-- Información médica para el socio (003)
INSERT INTO public.socio_medical_info (id, socio_id, blood_type, has_relevant_allergies, has_relevant_medical_condition, uses_emergency_medication, created_at, updated_at)
VALUES ('ffffb001-0000-4000-b000-000000000003', '00000000-0000-4000-b000-000000000003', 'O+', false, false, false, now(), now());

-- Cuota PENDIENTE para el directivo (002) → caso "con deuda"
INSERT INTO public.estado_cuotas (socio_id, valor, fecha, estado, created_at)
VALUES (
    '00000000-0000-4000-b000-000000000002',
    50.00,
    CURRENT_DATE,
    'PENDIENTE',
    now()
);
