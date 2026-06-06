-- Test data para perfil completo.
-- Requiere socios-test-data.sql ejecutado antes.
-- Admin (00000000-0000-4000-b000-000000000001) → perfil completo.
-- Socio (00000000-0000-4000-b000-000000000003) → perfil incompleto (sin contactos, sin medical info, sin docs).

-- Teléfono para admin (necesario para datos básicos completos)
UPDATE socios SET telefono = '0991111111' WHERE id = '00000000-0000-4000-b000-000000000001';

-- Contactos de emergencia para admin
INSERT INTO public.socio_emergency_contacts (id, socio_id, orden, nombre_completo, relacion, celular, created_at, updated_at)
VALUES
    ('aaaabbbb-0001-4000-b000-000000000001', '00000000-0000-4000-b000-000000000001', 1, 'Contacto Uno', 'Familiar', '0991111111', now(), now()),
    ('aaaabbbb-0002-4000-b000-000000000001', '00000000-0000-4000-b000-000000000001', 2, 'Contacto Dos', 'Amigo',    '0992222222', now(), now());

-- Información médica para admin
INSERT INTO public.socio_medical_info (id, socio_id, blood_type, has_relevant_allergies, has_relevant_medical_condition, uses_emergency_medication, created_at, updated_at)
VALUES ('bbbbcccc-0001-4000-b000-000000000001', '00000000-0000-4000-b000-000000000001', 'O+', false, false, false, now(), now());

-- Aceptaciones de todos los documentos obligatorios para admin
-- Primero obtenemos los documentos activos requeridos que ya existen (insertados por Flyway V11)
-- DATA_PROCESSING_POLICY (version 1, REGISTRATION)
INSERT INTO public.legal_document_acceptances (id, socio_id, legal_document_id, document_code, document_version, content_hash, accepted_at, ip_address, user_agent, accepted, created_at)
SELECT
    'ccccdddd-0001-4000-b000-000000000001',
    '00000000-0000-4000-b000-000000000001',
    d.id,
    d.code,
    d.version,
    d.content_hash,
    now(), '127.0.0.1', 'TestAgent', true, now()
FROM public.legal_documents d
WHERE d.code = 'DATA_PROCESSING_POLICY' AND d.active = true
LIMIT 1;

-- MEDICAL_DATA_CONSENT (version 1, REGISTRATION)
INSERT INTO public.legal_document_acceptances (id, socio_id, legal_document_id, document_code, document_version, content_hash, accepted_at, ip_address, user_agent, accepted, created_at)
SELECT
    'ccccdddd-0002-4000-b000-000000000001',
    '00000000-0000-4000-b000-000000000001',
    d.id,
    d.code,
    d.version,
    d.content_hash,
    now(), '127.0.0.1', 'TestAgent', true, now()
FROM public.legal_documents d
WHERE d.code = 'MEDICAL_DATA_CONSENT' AND d.active = true
LIMIT 1;

-- DATA_RETENTION_POLICY (version 1, PROFILE_COMPLETION)
INSERT INTO public.legal_document_acceptances (id, socio_id, legal_document_id, document_code, document_version, content_hash, accepted_at, ip_address, user_agent, accepted, created_at)
SELECT
    'ccccdddd-0003-4000-b000-000000000001',
    '00000000-0000-4000-b000-000000000001',
    d.id,
    d.code,
    d.version,
    d.content_hash,
    now(), '127.0.0.1', 'TestAgent', true, now()
FROM public.legal_documents d
WHERE d.code = 'DATA_RETENTION_POLICY' AND d.active = true
LIMIT 1;

-- LIABILITY_WAIVER (version 1, PROFILE_COMPLETION)
INSERT INTO public.legal_document_acceptances (id, socio_id, legal_document_id, document_code, document_version, content_hash, accepted_at, ip_address, user_agent, accepted, created_at)
SELECT
    'ccccdddd-0004-4000-b000-000000000001',
    '00000000-0000-4000-b000-000000000001',
    d.id,
    d.code,
    d.version,
    d.content_hash,
    now(), '127.0.0.1', 'TestAgent', true, now()
FROM public.legal_documents d
WHERE d.code = 'LIABILITY_WAIVER' AND d.active = true
LIMIT 1;
