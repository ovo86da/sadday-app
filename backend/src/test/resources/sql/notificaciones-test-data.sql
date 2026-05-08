-- =============================================================================
-- Datos de prueba para tests del módulo Notificaciones
-- Inserta un socio cuyo cumpleaños es HOY (fecha dinámica) para verificar
-- que el endpoint /notificaciones/cumpleanos lo devuelve.
-- =============================================================================

-- Socio con cumpleaños HOY (edad 20 años)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    'aaaa1111-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    'Cumple', 'Hoy', '8888888881', 'cumple@sadday.local',
    CURRENT_DATE - INTERVAL '20 years',
    '2020-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- Socio con cumpleaños MAÑANA (NO debe aparecer en el endpoint)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    'aaaa2222-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    'Cumple', 'Manana', '8888888882', 'cumple.manana@sadday.local',
    CURRENT_DATE - INTERVAL '20 years' + INTERVAL '1 day',
    '2020-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Socio Activo'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);

-- Socio Ex-socio con cumpleaños hoy (NO debe aparecer — excluido por tipo)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    'aaaa3333-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    'Exsocio', 'Cumple', '8888888883', 'exsocio.cumple@sadday.local',
    CURRENT_DATE - INTERVAL '30 years',
    '2010-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Inhabilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Ex-socio'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso WHERE codigo = 'EX_MEMBER')
);

-- Socio Juvenil que ya tiene 18 años (para verificar el scheduler de promoción)
INSERT INTO socios (
    id, nombre, apellido, cedula, correo,
    fecha_nacimiento, fecha_ingreso,
    estado_habilitacion_id, tipo_socio_id, rol_sistema_id,
    estado_acceso_id
) VALUES (
    'aaaa4444-aaaa-4aaa-baaa-aaaaaaaaaaaa',
    'Juvenil', 'Mayor', '8888888884', 'juvenil.mayor@sadday.local',
    CURRENT_DATE - INTERVAL '19 years',
    '2023-01-01',
    (SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'),
    (SELECT id FROM tipo_socio_club    WHERE nombre = 'Juvenil'),
    (SELECT id FROM roles_sistema      WHERE nombre = 'Socio'),
    (SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')
);
