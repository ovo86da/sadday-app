-- =============================================================================
-- Datos de prueba para tests del módulo Socios
-- IDs con prefijo 00000000-0000-4000-b000-* para distinguirlos de auth tests.
-- Los usuarios_auth se insertan programáticamente en el setUp del test.
-- =============================================================================

-- Socio Admin (para autenticarse como administrador en los tests)
-- nivel_tecnico = Expert (SO006, nivel=5) para superar cualquier nivel mínimo en tests de inscripción
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id, nivel_tecnico_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-b000-000000000001',
    'Admin', 'Test', '1111111111', 'admin@sadday.local',
    '1985-05-10', '2015-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Admin'),
    'SO006',
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- Socio Directivo (para probar habilitar/inhabilitar)
-- nivel_tecnico = Expert (SO006, nivel=5)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id, nivel_tecnico_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-b000-000000000002',
    'Directivo', 'Test', '2222222222', 'directivo@sadday.local',
    '1988-08-20', '2016-03-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Directivo'),
    'SO006',
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- Socio regular (para probar que no puede hacer operaciones de admin)
-- nivel_tecnico = Principiante (SO002, nivel=1) — nivel bajo para probar bloqueos en tests de inscripción
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id, nivel_tecnico_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-b000-000000000003',
    'Regular', 'Socio', '3333333333', 'socio@sadday.local',
    '1995-12-01', '2022-06-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    'SO002',
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- Socio target (para probar CRUD — habilitar/inhabilitar/actualizar)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-b000-000000000010',
    'Target', 'Socio', '9000000001', 'target@sadday.local',
    '1992-03-15', '2023-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Inhabilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);
