-- =============================================================================
-- Datos de prueba para tests de autenticación
-- Cargado via @Sql en AbstractIntegrationTest o AuthIntegrationTest.
--
-- IDs fijos con prefijo 00000000-0000-4000-a000-* para identificarlos fácilmente.
-- La contraseña del usuario de prueba es: TestPassword123!
-- (el hash se calcula en el setUp del test via PasswordEncoder para consistencia)
-- =============================================================================

-- Socio habilitado (rol Socio)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-a000-000000000001',
    'Test', 'Usuario', '9999999999', 'test@sadday.local',
    '1990-01-15', '2020-03-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- Socio inhabilitado (para probar bloqueo de login)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    '00000000-0000-4000-a000-000000000003',
    'Socio', 'Inhabilitado', '8888888888', 'inhabilitado@sadday.local',
    '1985-06-20', '2019-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Inhabilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- NOTA: usuarios_auth se insertan programáticamente en el setUp del test
-- porque el hash de contraseña debe generarse con el PasswordEncoder real.
