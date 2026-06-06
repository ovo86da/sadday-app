-- Test data: información médica para el socio admin (00000000-0000-4000-b000-000000000001)
-- Se espera que socios-test-data.sql se ejecute antes.

INSERT INTO public.socio_medical_info (
    id, socio_id, blood_type,
    has_relevant_allergies, allergies_detail,
    has_relevant_medical_condition, medical_condition_detail,
    uses_emergency_medication, emergency_medication_detail,
    additional_notes, created_at, updated_at
) VALUES (
    'eeeeeeee-eeee-4eee-beee-eeeeeeeeee01',
    '00000000-0000-4000-b000-000000000001',
    'O+',
    true, 'Alergia a la penicilina',
    false, null,
    false, null,
    null,
    now(), now()
);

-- Aceptación de MEDICAL_DATA_CONSENT para el admin
INSERT INTO public.legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, created_at, updated_at
) VALUES (
    'aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    'MEDICAL_DATA_CONSENT',
    'Consentimiento Datos de Salud (Test)',
    'Test',
    'MEDICAL_DATA_CONSENT',
    'REGISTRATION',
    99,
    'Contenido de prueba',
    'aabbccdd',
    true, true, true,
    now(), now(), now()
);

INSERT INTO public.legal_document_acceptances (
    id, socio_id, legal_document_id, document_code, document_version,
    content_hash, accepted_at, ip_address, user_agent, accepted, created_at
) VALUES (
    'cccccccc-cccc-4ccc-bccc-cccccccccccc',
    '00000000-0000-4000-b000-000000000001',
    'aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    'MEDICAL_DATA_CONSENT',
    99,
    'aabbccdd',
    now(), '127.0.0.1', 'TestAgent', true, now()
);
