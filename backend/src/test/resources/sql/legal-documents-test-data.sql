-- =============================================================================
-- Datos de prueba para tests del módulo Documentos Legales.
-- UUIDs fijos para que los tests sean deterministas.
-- Los socios base se cargan desde socios-test-data.sql.
-- =============================================================================

-- Socio Secretaria (no existe en socios-test-data.sql)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-b000-000000000004',
    'Secretaria', 'Test', '4444444444', 'secretaria@sadday.local',
    '1990-06-15', '2018-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Secretaria'),
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- Documento activo (REGISTRATION)
INSERT INTO legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, approved_by, created_at, updated_at
) VALUES (
    'dddddddd-dddd-4ddd-bddd-dddddddddd01',
    'TEST_POLICY',
    'Política de prueba',
    'Documento de prueba para tests',
    'TEST_POLICY',
    'REGISTRATION',
    1,
    'Contenido de la política de prueba versión 1',
    encode(sha256(convert_to('Contenido de la política de prueba versión 1', 'UTF-8')), 'hex'),
    true, true, true,
    now(), null, now(), now()
);

-- Documento inactivo (versión 2 pendiente de activación)
INSERT INTO legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, approved_by, created_at, updated_at
) VALUES (
    'dddddddd-dddd-4ddd-bddd-dddddddddd02',
    'TEST_POLICY',
    'Política de prueba',
    'Documento de prueba para tests',
    'TEST_POLICY',
    'REGISTRATION',
    2,
    'Contenido de la política de prueba versión 2',
    encode(sha256(convert_to('Contenido de la política de prueba versión 2', 'UTF-8')), 'hex'),
    false, true, true,
    null, null, now(), now()
);

-- Documento activo (PROFILE_COMPLETION)
INSERT INTO legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, approved_by, created_at, updated_at
) VALUES (
    'dddddddd-dddd-4ddd-bddd-dddddddddd03',
    'TEST_WAIVER',
    'Descargo de prueba',
    'Documento de descargo para tests',
    'TEST_WAIVER',
    'PROFILE_COMPLETION',
    1,
    'Contenido del descargo de prueba',
    encode(sha256(convert_to('Contenido del descargo de prueba', 'UTF-8')), 'hex'),
    true, true, true,
    now(), null, now(), now()
);
