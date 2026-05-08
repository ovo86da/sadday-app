-- =============================================================================
-- Datos de prueba para tests del módulo Admin y Security
-- IDs con prefijo 00000000-0000-4000-d000-* para distinguirlos de otros tests.
-- Los usuarios_auth se insertan programáticamente en el setUp del test.
-- =============================================================================

-- Socio Admin
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-d000-000000000001',
    'Admin', 'Test', '1111111111', 'admin@admin.local',
    '1985-05-10', '2015-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Admin'),
    (SELECT id FROM estado_acceso      WHERE codigo = 'ACTIVE')
);

-- Socio Secretaria
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-d000-000000000002',
    'Secretaria', 'Test', '2222222222', 'secretaria@admin.local',
    '1990-06-20', '2018-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Secretaria'),
    (SELECT id FROM estado_acceso      WHERE codigo = 'ACTIVE')
);

-- Socio regular (para probar denials de 403)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-d000-000000000003',
    'Regular', 'Socio', '3333333333', 'socio@admin.local',
    '1995-01-01', '2022-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso      WHERE codigo = 'ACTIVE')
);

-- Socio target (para probar desbloquear, cambio de estado, etc.)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-d000-000000000010',
    'Target', 'Socio', '9000000099', 'target@admin.local',
    '1992-03-15', '2023-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso      WHERE codigo = 'ACTIVE')
);
