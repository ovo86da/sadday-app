-- =============================================================================
-- Datos de prueba para el módulo de riesgos por actividad (Fase 6)
-- Depende de: socios-test-data.sql, salidas-test-data.sql
--
-- Salida test principal: aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa
-- Admin (ADMIN):   00000000-0000-4000-b000-000000000001
-- Socio regular:   00000000-0000-4000-b000-000000000003
-- =============================================================================

-- Documento de riesgos activo para la salida principal
INSERT INTO public.activity_risk_documents (
    id, activity_id, version, content, content_hash, active, approved_at, approved_by, created_at, updated_at
) VALUES (
    'cccccccc-cccc-4ccc-bccc-cccccccccccc',
    'aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    1,
    'Contenido del documento de riesgos de la salida test.',
    'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3',
    true,
    NOW(),
    '00000000-0000-4000-b000-000000000001',
    NOW(),
    NOW()
);

-- El admin ya aceptó el documento de riesgos
INSERT INTO public.activity_risk_acceptances (
    id, socio_id, activity_id, activity_risk_document_id, document_version,
    content_hash, accepted_at, ip_address, user_agent, created_at
) VALUES (
    'dddddddd-dddd-4ddd-bddd-dddddddddddd',
    '00000000-0000-4000-b000-000000000001',
    'aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    'cccccccc-cccc-4ccc-bccc-cccccccccccc',
    1,
    'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3',
    NOW(),
    '127.0.0.1',
    'Test',
    NOW()
);
