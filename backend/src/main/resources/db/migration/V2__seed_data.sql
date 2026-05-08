-- =============================================================================
-- V2__seed_data.sql — Datos iniciales: tablas de lookup, montañas y rutas
-- Rutas se insertan con aprobada=false para ser aprobadas en producción.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Lookup tables (sin FK a socios)
-- ---------------------------------------------------------------------------

INSERT INTO public.clasificacion_socio (id, nivel, nombre, descripcion) VALUES
('SO001', 0, 'Externo',     'Persona que no es miembro del grupo; no se conocen sus capacidades técnicas ni físicas.'),
('SO002', 1, 'Principiante','Persona que acaba de ingresar al club, con poca o nula experiencia comprobada en montaña.'),
('SO003', 2, 'Semi-senior', 'Persona con varias participaciones en el club y alguna experiencia en media montaña.'),
('SO004', 3, 'Senior',      'Persona con salidas a varias montañas, incluyendo alta montaña y escalada en roca. Puede ser puntero de cordada.'),
('SO005', 4, 'Advance',     'Persona con alta capacidad técnica y física. Puede guiar a otros en salidas de alta dificultad.'),
('SO006', 5, 'Expert',      'Persona con muchos años de experiencia y gran capacidad física. Puede guiar en rutas desconocidas.');

INSERT INTO public.compromiso (id, tipo, descripcion, rank) VALUES
('C001','I',   'Ruta corta, retirada evidente, cerca de civilización.',                          1),
('C002','II',  'Media jornada, retirada sencilla, acceso a rescate rápido.',                     2),
('C003','III', 'Jornada completa. El descenso puede requerir varios rápeles.',                   3),
('C004','IV',  'Jornada larga e intensa. Retirada difícil; mal tiempo = problemas serios.',      4),
('C005','V',   'Varios días. Vivac. Retirada extremadamente compleja una vez adentro.',          5),
('C006','VI',  'Gran pared/expedición remota. Autosuficiencia total.',                           6),
('C007','VII', 'Máximo. Himalaya/Antártida, gran altitud, rescate casi imposible.',              7);

INSERT INTO public.dificultad_hielo_wi (id, grado, descripcion, rank) VALUES
('WI001','NA',  'Sin escala. No hay escalada en hielo.',                                                           0),
('WI002','WI1', '~45°. Se progresa casi caminando con crampones.',                                                1),
('WI003','WI2', '~60°. Hielo sólido, técnica básica (1 piolet posible).',                                        2),
('WI004','WI3', '70°–80°. Largo y consistente. Normalmente dos piolets.',                                        3),
('WI005','WI4', '90° vertical, buena calidad, tramos verticales. Muy físico.',                                   4),
('WI006','WI5', 'Verticalidad sostenida, hielo más frágil (columnas/coliflor).',                                 5),
('WI007','WI6', 'Muy técnico, vertical/desplomado, hielo delgado o inestable.',                                  6),
('WI008','WI7', 'Límite. Hielo extremadamente fino, estructuras colgantes frágiles. Solo expertos top.',         7);

INSERT INTO public.dificultad_roca_uiaa_francesa (id, uiaa, francesa, descripcion, rank) VALUES
('UIAA-F001','NA',   'NA',    'Sin escala. No hay partes de escalada en roca.',                                                                            1),
('UIAA-F002','I',    '1',     'Trepada fácil. Manos para el equilibrio. Normalmente sin cuerda.',                                                         2),
('UIAA-F003','II',   '2',     'Trepada/paso de bloque. Manos necesarias. Exposición baja.',                                                               3),
('UIAA-F004','III',  '3',     'Escalada simple. Los principiantes suelen necesitar cuerda.',                                                              4),
('UIAA-F005','IV',   '4a–4c', 'Técnica clásica. Cuerda/arnés obligatorios.',                                                                             5),
('UIAA-F006','V',    '5a–5c', 'Dificultad media. Vertical con buenas presas.',                                                                           6),
('UIAA-F007','VI',   '6a–6b', 'Dificultad alta. Presas pequeñas, fuerza y técnica.',                                                                     7),
('UIAA-F008','VII+', '6c–7a+','Muy difícil: movimientos técnicos continuos, presas pequeñas y secciones verticales o ligeramente desplomadas.',          8),
('UIAA-F009','VIII', '7b–8a', 'Nivel experto: escalada atlética con movimientos dinámicos y alta exigencia física y mental.',                             9),
('UIAA-F010','IX',   '8b–9a', 'Élite mundial: movimientos extremadamente técnicos, desplomes fuertes. Reservado para escaladores de alto rendimiento.',   10);

INSERT INTO public.dificultad_senderismo (id, nombre, descripcion, rank) VALUES
('DS001','Fácil',        'Camino bien señalizado, terreno llano o con pendientes suaves. Apto para todos.',              1),
('DS002','Moderado',     'Terreno variado con algunas pendientes. Requiere buena condición física básica.',              2),
('DS003','Exigente',     'Pendientes pronunciadas, terreno irregular. Requiere experiencia y buena condición.',          3),
('DS004','Muy exigente', 'Alta exigencia física y técnica. Solo para personas con experiencia consolidada.',             4);

INSERT INTO public.dignidades (id, nombre, descripcion) VALUES
(1,'Jefe de Salida',      'Responsable máximo de la salida. Designado por un Directivo antes de la salida.'),
(2,'Guía',                'Guía de la salida. Lidera el ascenso y orienta al grupo.'),
(3,'Escoba',              'Va al final del grupo. Vela porque nadie se quede atrás.'),
(4,'Cronista',            'Registra todo lo que sucede durante la salida.'),
(5,'Enlace',              'Actúa de enlace entre subgrupos si el grupo se separa. Requiere muy buena condición física.'),
(6,'Fotógrafo',           'Documentación fotográfica de la salida.'),
(7,'Miembro de Cordada',  'Parte de la cordada en salidas de alta montaña.'),
(8,'Puntero de Cordada',  'Puntero de la cordada en alta montaña.'),
(9,'Conductor',           'Socio del club que proporcionó su vehículo propio para la salida.');

INSERT INTO public.equipo_montana (id, nombre, descripcion) VALUES
(1,'Equipo Alta Montaña',    'Equipo completo de alta montaña'),
(2,'Equipo Media Montaña',   'Equipo completo de media montaña'),
(3,'Equipo Escalada Roca',   'Equipo completo para escalada en Roca'),
(4,'Equipo Escalada Hielo',  'Equipo completo para escalada en Hielo'),
(5,'Equipo Selva Tropical',  'Equipo pensado para lugares selváticos, cascadas, etc.'),
(6,'Sin equipo obligatorio', 'No se necesita equipo especial aparte de calzado');

INSERT INTO public.escala_alpina_ifas (id, grado, nombre, descripcion, rank) VALUES
('IFAS001','F',  'Facile (Fácil)',                     'Caminata técnica. Terreno fácil, glaciar poco complejo. Manos para equilibrio ocasional.',              1),
('IFAS002','PD', 'Peu Difficile (Poco difícil)',        'Pendientes de nieve/hielo hasta ~45°. Manejo básico de cuerda y crampones.',                           2),
('IFAS003','AD', 'Assez Difficile (Algo difícil)',      'Pasos de roca aprox. III, pendientes 45°–65°. Requiere experiencia.',                                  3),
('IFAS004','D',  'Difficile (Difícil)',                 'Escalada sostenida (IV/V) y hielo hasta ~70°. Nivel avanzado.',                                        4),
('IFAS005','TD', 'Très Difficile (Muy difícil)',        'Rutas largas, técnicas y con peligros objetivos (avalanchas, desprendimientos).',                      5),
('IFAS006','ED', 'Extrêmement Difficile',               'Élite. Terreno muy vertical, mucha exposición, gran exigencia.',                                       6);

INSERT INTO public.estado_acceso (id, codigo, nombre, descripcion) VALUES
(1,'ACTIVE',           'Activo',               'Socio con acceso completo al sistema.'),
(2,'BLOCKED',          'Bloqueado',            'Acceso bloqueado por motivo de negocio (sanción, etc.). Puede volver a Activo.'),
(3,'EX_MEMBER',        'Ex-miembro',           'Persona que dejó el club oficialmente. Puede reactivarse.'),
(4,'PENDING_REGISTER', 'Pendiente de registro','Invitado por la secretaria; aún no ha completado su registro.'),
(5,'DISABLED',         'Deshabilitado',        'Cuenta deshabilitada por razón técnica. Solo el Admin puede reactivar.');

INSERT INTO public.estado_habilitacion (id, nombre, descripcion) VALUES
(1,'Habilitado',     'Socio habilitado, con cuotas al día o declarado vitalicio.'),
(2,'Inhabilitado',   'Socio inhabilitado por deudas u otras razones marcadas por un Directivo.'),
(3,'Socio Vitalicio','Socio vitalicio por antigüedad; exento del pago de cuotas.');

INSERT INTO public.formato_salida (id, nombre, orden) VALUES
('FS001','Salida de campo',  1),
('FS002','Reunión',          2),
('FS003','Evento social',    3),
('FS004','Entrenamiento',    4),
('FS005','Paseo / Relax',    5);

INSERT INTO public.publico_objetivo (id, nombre, orden) VALUES
('PO001','Socios',       1),
('PO002','Juvenil',      2),
('PO003','Adulto Mayor', 3),
('PO004','Con externos', 4),
('PO005','Verano',       5);

INSERT INTO public.roles_sistema (id, nombre, descripcion) VALUES
(1,'Admin',     'Acceso total al sistema sin restricciones.'),
(2,'Secretaria','Gestión de socios, actas, salidas y portal de administración.'),
(3,'Directivo', 'Gestión de salidas, aprobación de rutas y montañas, habilitación de socios.'),
(4,'Socio',     'Usuario base: puede ver el calendario, inscribirse en salidas y proponer rutas.');

INSERT INTO public.sadday_riesgo_exigencia (id, valor, escala, descripcion, rank) VALUES
('SA001',1,'Mínimo',  'El riesgo que se asume es mínimo y no supondrá un imprevisto de gran importancia si se desencadena.',           1),
('SA002',2,'Bajo',    'El riesgo es bajo. Si el peligro se desencadena puede suponer un retraso importante en el horario.',            2),
('SA003',3,'Moderado','El riesgo es moderado. Si el peligro se materializa probablemente la actividad no se finalizará según lo previsto.',3),
('SA004',4,'Alto',    'El riesgo es alto. Si el peligro se materializa puede haber daños personales importantes.',                     4),
('SA005',5,'Extremo', 'El riesgo es extremo. Si el peligro se desencadena la integridad física corre grave peligro.',                  5);

INSERT INTO public.sistema_clases_yosemite (id, tipo, descripcion, rank) VALUES
('Y001','NA',      'No aplica.',                                          0),
('Y002','Clase 1', 'Senderismo por camino.',                             1),
('Y003','Clase 2', 'Terreno irregular, manos para equilibrio.',          2),
('Y004','Clase 3', 'Trepada (scrambling). Riesgo de caída; algunos usan cuerda.',3),
('Y005','Clase 4', 'Muy expuesto. Cuerda casi siempre.',                 4),
('Y006','Clase 5', 'Escalada técnica (5.5, 5.10…).',                    5);

INSERT INTO public.tipo_socio_club (id, nombre, descripcion) VALUES
(1,'Socio Activo','Socio activo del club.'),
(2,'Aspirante',   'Socio aspirante, pendiente de tomar el curso de montaña.'),
(3,'Ex-socio',    'Ex-socio del club que presentó la renuncia o fue expulsado.'),
(4,'Sancionado',  'Socio sancionado por la directiva.'),
(5,'Juvenil',     'Socio menor de edad, calculado automáticamente desde la fecha de nacimiento.');

-- ---------------------------------------------------------------------------
-- acceso_ruta_por_nivel (FK a clasificacion_socio y escalas — sin socios)
-- ---------------------------------------------------------------------------

INSERT INTO public.acceso_ruta_por_nivel
    (id, nivel_socio_id, max_ifas_id, max_roca_id, max_hielo_id, max_compromiso_id, max_yosemite_id, updated_by_id, max_sadday_tecnico_id, max_sadday_fisico_id)
VALUES
(1,'SO001','IFAS001','UIAA-F001','WI001','C001','Y003',NULL,'SA001','SA001'),
(2,'SO002','IFAS002','UIAA-F001','WI001','C001','Y003',NULL,'SA003','SA003'),
(3,'SO003','IFAS002','UIAA-F002','WI001','C002','Y005',NULL,'SA003','SA003'),
(4,'SO004','IFAS003','UIAA-F006','WI005','C002','Y006',NULL,'SA005','SA005'),
(5,'SO005','IFAS005','UIAA-F008','WI005','C003','Y006',NULL,'SA005','SA005'),
(6,'SO006','IFAS006','UIAA-F008','WI006','C005','Y006',NULL,'SA005','SA005');

-- ---------------------------------------------------------------------------
-- configuracion_sistema (updated_by_id = NULL para prod)
-- ---------------------------------------------------------------------------

INSERT INTO public.configuracion_sistema (id, clave, valor, descripcion, updated_by_id) VALUES
(1,'BLOQUEAR_INSCRIPCION_INHABILITADOS','false','Si es true, todos los socios inhabilitados quedan bloqueados para inscribirse en salidas (excepto la Secretaria).',NULL),
(2,'MAX_INTENTOS_LOGIN','3','Número máximo de intentos de login fallidos antes de bloquear la cuenta.',NULL),
(3,'HORAS_BLOQUEO_LOGIN','24','Horas de bloqueo de cuenta tras superar el máximo de intentos fallidos.',NULL);

-- ---------------------------------------------------------------------------
-- mountains
-- ---------------------------------------------------------------------------

INSERT INTO public.mountains (id, nombre, region, altitud, pais) VALUES
( 4,'Rumiñahui Central',    'CORDILLERA_ORIENTAL', 4722,'Ecuador'),
( 5,'Rumiñahui Sur',        'CORDILLERA_ORIENTAL', 4722,'Ecuador'),
(13,'Chimborazo',           'CORDILLERA_OCCIDENTAL',6263,'Ecuador'),
(14,'Cotopaxi',             'CORDILLERA_ORIENTAL', 5897,'Ecuador'),
(15,'Cayambe',              'CORDILLERA_ORIENTAL', 5790,'Ecuador'),
(16,'Antisana',             'CORDILLERA_ORIENTAL', 5753,'Ecuador'),
(17,'El Altar (Capac-Urcu)','CORDILLERA_ORIENTAL', 5319,'Ecuador'),
(18,'Iliniza Sur',          'CORDILLERA_OCCIDENTAL',5248,'Ecuador'),
(19,'Sangay',               'CORDILLERA_ORIENTAL', 5230,'Ecuador'),
(20,'Iliniza Norte',        'CORDILLERA_OCCIDENTAL',5126,'Ecuador'),
(21,'Tungurahua',           'CORDILLERA_ORIENTAL', 5023,'Ecuador'),
(22,'Carihuairazo',         'CORDILLERA_OCCIDENTAL',5018,'Ecuador'),
(23,'Morurco',              'CORDILLERA_ORIENTAL', 4881,'Ecuador'),
(24,'Predicador',           'CORDILLERA_ORIENTAL', 4878,'Ecuador'),
(25,'Quilindaña',           'CORDILLERA_ORIENTAL', 4877,'Ecuador'),
(26,'Sincholagua',          'CORDILLERA_ORIENTAL', 4919,'Ecuador'),
(27,'Cotacachi',            'CORDILLERA_OCCIDENTAL',4944,'Ecuador'),
(28,'Guagua Pichincha',     'CORDILLERA_OCCIDENTAL',4794,'Ecuador'),
(29,'Corazón',              'CORDILLERA_OCCIDENTAL',4790,'Ecuador'),
(30,'Chiles',               'CORDILLERA_OCCIDENTAL',4748,'Ecuador'),
(31,'Ayapungo',             'CORDILLERA_ORIENTAL', 4730,'Ecuador'),
(32,'Rumiñahui Norte',      'CORDILLERA_ORIENTAL', 4712,'Ecuador'),
(33,'Cubillín',             'CORDILLERA_ORIENTAL', 4711,'Ecuador'),
(34,'Rucu Pichincha',       'CORDILLERA_OCCIDENTAL',4698,'Ecuador'),
(35,'Quilimas',             'CORDILLERA_ORIENTAL', 4686,'Ecuador'),
(36,'Saraurcu',             'CORDILLERA_ORIENTAL', 4677,'Ecuador'),
(37,'Imbabura',             'ZONA_INTERANDINA',    4640,'Ecuador'),
(38,'Cerro Hermoso',        'CORDILLERA_ORIENTAL', 4639,'Ecuador'),
(39,'Casahuala',            'CORDILLERA_ORIENTAL', 4562,'Ecuador'),
(40,'Yanahurco de Piñán',   'CORDILLERA_OCCIDENTAL',4538,'Ecuador'),
(41,'Atacazo',              'CORDILLERA_OCCIDENTAL',4463,'Ecuador'),
(42,'Cerro Puntas',         'CORDILLERA_ORIENTAL', 4463,'Ecuador'),
(43,'Fuya Fuya',            'ZONA_INTERANDINA',    4290,'Ecuador'),
(44,'Yanahurco de Mojanda', 'ZONA_INTERANDINA',    4289,'Ecuador'),
(45,'Pasochoa',             'CORDILLERA_ORIENTAL', 4200,'Ecuador'),
(46,'Sumaco',               'CORDILLERA_ORIENTAL', 3990,'Ecuador'),
(47,'Quilotoa',             'CORDILLERA_OCCIDENTAL',3914,'Ecuador'),
(48,'Cerro Collana',        'CORDILLERA_ORIENTAL', 3814,'Ecuador'),
(49,'Reventador',           'CORDILLERA_ORIENTAL', 3562,'Ecuador'),
(50,'Casitagua',            'CORDILLERA_OCCIDENTAL',3514,'Ecuador'),
(51,'Pululahua',            'CORDILLERA_OCCIDENTAL',3356,'Ecuador');

-- ---------------------------------------------------------------------------
-- rutas (aprobada=false, sin FK a socios)
-- ---------------------------------------------------------------------------

INSERT INTO public.rutas
    (id, nombre, mountain_id, sector_zona, longitud_km, desnivel_m, duracion_dias, peligros_notas,
     requiere_permisos, aprobada, aprobada_por_id, aprobada_en, propuesta_por_id,
     tipo_actividad, lugar_referencia, nivel_minimo_socio_id, duracion_horas)
VALUES
-- ALPINISMO
(  1,'Ruta Normal',                         13,'Cara Sur',                                    NULL, NULL,NULL,NULL,                                                                                                                       true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,NULL,NULL),
( 57,'Chimborazo - Ruta Normal (Whymper)',  13,'Cara noroeste, Refugio Whymper',              14.00, 1400,   2,'Grietas en glaciar norte. Viento fuerte en cumbre. Partida nocturna desde refugio.',                                     true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO004',NULL),
( 58,'Cotopaxi - Ruta Normal',              14,'Glaciar norte, Refugio José Rivas',           12.00, 1000,   2,'Grietas y seracs en glaciar. Partida obligatoria entre 23h-01h. Verificar cobertura volcánica.',                          true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO003',NULL),
( 59,'Cayambe - Ruta Normal Sureste',       15,'Glaciar sureste, Refugio Berge',              12.00, 1100,   2,'Glaciar muy activo con grietas profundas. Alta exposición al viento. Ruta larga sobre nieve.',                             true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO004',NULL),
( 60,'Antisana - Ruta Norte (Burbano)',     16,'Glaciar norte, Refugio Antisana',             15.00, 1200,   2,'El Denali ecuatoriano. Glaciar muy resquebrajado. Permiso Reserva Ecológica obligatorio.',                                true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO005',NULL),
( 61,'El Altar - Pico El Obispo',           17,'Collanes, cara oeste',                        20.00, 1600,   3,'Ruta muy expuesta con tramos de hielo y roca. Neblina frecuente. Permisos PN Sangay requeridos.',                          true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO006',NULL),
( 62,'Iliniza Sur - Ruta Normal',           18,'Refugio Nuevos Horizontes, cara norte',       10.00,  700,   2,'Pendientes de hielo hasta 70°. Grietas en glaciar. Crampones y piolet obligatorios.',                                    true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO004',NULL),
( 63,'Iliniza Norte - Ruta Normal',         20,'Refugio Nuevos Horizontes, cresta norte',     10.00,  600,   1,'Tramos de roca expuesta en la cresta. Sin glaciar permanente. Buena aclimatación para nevados.',                          false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO002',NULL),
( 64,'Carihuairazo - Ruta Normal',          22,'Cara oeste, carretera Ambato-Guaranda',        8.00,  700,   1,'Pendientes de nieve suave. Páramo húmedo. Niebla frecuente en temporada lluviosa.',                                      false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO002',NULL),
( 65,'Sincholagua - Ruta Normal',           26,'Cara sur, Parque Nacional Cotopaxi',          12.00,  900,   1,'Tramos de nieve y barro en páramo. Buenas vistas al Cotopaxi y Rumiñahui.',                                              true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO002',NULL),
( 66,'Cotacachi - Ruta Norte',              27,'Cara norte, Laguna Cuicocha',                  8.00,  900,   1,'Sin glaciar. Terreno rocoso y páramo. Acceso desde mirador de Cuicocha.',                                               false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO002',NULL),
( 67,'Guagua Pichincha - Ruta Normal',      28,'Caldera norte, acceso teleférico Quito',       6.00,  400,   1,'Volcán activo. Monitorear IGEPN. Gases volcánicos en caldera.',                                                          false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO002',NULL),
( 68,'Corazón - Ruta Sureste',              29,'Aloa, Mejía',                                 10.00,  800,   1,'Páramo abierto. Orientación difícil en niebla. Terreno mixto hierba y roca.',                                            false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO002',NULL),
( 69,'Tungurahua - Ruta Normal',            21,'Pondoa, Refugio Nicolás Martínez',            12.00, 1000,   2,'Volcán activo. Requiere autorización IGEPN. Peligro de piroclastos y gases. Verificar antes.',                           true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO004',NULL),
( 70,'Rumiñahui Central - Ruta Normal',      4,'Parque Nacional Cotopaxi, cara norte',        10.00,  800,   1,'Roca volcánica suelta en tramos superiores. Sin glaciar. Buena aclimatación.',                                          true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO002',NULL),
( 71,'Quilindaña - Ruta Normal',            25,'Hacienda San José, cara sur',                 15.00, 1200,   2,'Cima aislada con glaciar pequeño. Páramo amplio. Poco frecuentada.',                                                    false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO003',NULL),
( 72,'Morurco - Ruta por Glaciar',          23,'Cara oeste, Reserva Antisana',                 8.00,  700,   1,'Glaciar pequeño pero técnico. Acceso restringido a Reserva Antisana. Buena aclimatación.',                               true,  false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO003',NULL),
( 73,'Pasochoa - Cumbre Volcánica',         45,'Hacienda San Francisco, cara norte',           9.00,  546,NULL,'Sin glaciar ni nieve. Bosque nativo en interior del cráter. Excelente para principiantes.',                              false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO001',   4),
( 74,'Atacazo - Cumbre',                    41,'Lloa, cara norte',                             8.00,  600,NULL,'Sin glaciar. Páramo abierto. Vistas a Quito y valles interandinos.',                                                    false, false,NULL,NULL,NULL,'ALPINISMO',NULL,'SO001',   5),
-- ESCALADA
(  2,'escalada1',                         NULL,'Quito',                                         1.00, 2800,NULL,'Sin peligros',                                                                                                           false, false,NULL,NULL,NULL,'ESCALADA','canteras','SO002',    1),
( 75,'Cojitambo - Sector Los Diedros',    NULL,'Sector central diedros',                       NULL, NULL,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'ESCALADA','Cojitambo, Cañar','SO002',  4),
( 76,'Cojitambo - Sector El Desplome',   NULL,'Sector norte desplomes',                        NULL, NULL,NULL,'Voladizo aéreo. Requiere seguro al pasador. Solo escaladores con experiencia en extraplomado.',                          false, false,NULL,NULL,NULL,'ESCALADA','Cojitambo, Cañar','SO004',  4),
( 77,'Cojitambo - Vía Clásica Norte',    NULL,'Pared norte clásica',                           NULL,   60,NULL,NULL,                                                                                                                      false, false,NULL,NULL,NULL,'ESCALADA','Cojitambo, Cañar','SO002',  3),
( 78,'Cojitambo - Inicio Novatos',       NULL,'Sector escuela',                                NULL, NULL,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'ESCALADA','Cojitambo, Cañar','SO001',  3),
( 79,'Rucu Pichincha - Sector Politécnica', 34,'Cruz Loma km 3, pared oeste',                 NULL, NULL,NULL,'Altitud 4200m. Roca riolítica sólida. Acceso por teleférico o senderismo.',                                              false, false,NULL,NULL,NULL,'ESCALADA',NULL,'SO003',  4),
( 80,'Rucu Pichincha - Pared Este',        34,'Cruz Loma, pared este',                        NULL,   40,NULL,'Exposición al viento este. Solo en días despejados.',                                                                    false, false,NULL,NULL,NULL,'ESCALADA',NULL,'SO004',  3),
( 81,'Guagua Pichincha - Cumbre Norte (multi-largo)', 28,'Caldera norte, acceso teleférico',  NULL,  200,NULL,'Multi-largo en 4600m. Alta exposición al viento. Gases volcánicos ocasionales. Solo escaladores expertos.',              false, false,NULL,NULL,NULL,'ESCALADA',NULL,'SO005',  6),
( 82,'Peñas Blancas - Pifo',             NULL,'Sector Peñas Blancas',                         NULL, NULL,NULL,'Acceso vía Pifo-Papallacta. Roca basáltica compacta.',                                                                  false, false,NULL,NULL,NULL,'ESCALADA','Pifo, Quito','SO002',  4),
( 83,'Sayausi - Roca Caliza',            NULL,'Sector caliza Parque El Cajas',                 NULL, NULL,NULL,'Temperatura baja. Roca caliza con buena fricción.',                                                                     false, false,NULL,NULL,NULL,'ESCALADA','Sayausi, Cuenca','SO002',  4),
( 84,'El Altar - Vía La Monja',           17,'Pared sureste del Altar',                       NULL,  500,NULL,'Multi-largo mixto roca-hielo. Solo escaladores de élite. Permisos PN Sangay obligatorios. Condiciones impredecibles.',  true,  false,NULL,NULL,NULL,'ESCALADA',NULL,'SO006',  8),
( 85,'Zumbahua - Sector Basáltico',      NULL,'Cerros volcánicos basálticos',                  NULL, NULL,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'ESCALADA','Zumbahua, Cotopaxi','SO001',  3),
( 86,'Nariz del Diablo - Sector Alausí', NULL,'Escarpe Nariz del Diablo',                     NULL,   80,NULL,'Roca sedimentaria con zonas descompuestas. Verificar solidez antes de escalar.',                                         false, false,NULL,NULL,NULL,'ESCALADA','Alausí, Chimborazo','SO003',  4),
-- TREKKING
(  3,'Cueva de los buhos',               NULL,'Ruminaui',                                      20.00,  500,NULL,'Sin peligros',                                                                                                           true,  false,NULL,NULL,NULL,'TREKKING','Parque nacional cotopaxi','SO002',  6),
( 87,'Quilotoa Loop - Circuito Completo',NULL,'Latacunga - Isinliví - Chugchilán - Quilotoa', 40.00, 2500,   4,NULL,                                                                                                                      false, false,NULL,NULL,NULL,'TREKKING','Quilotoa, Cotopaxi','SO003',NULL),
( 88,'Quilotoa - Vuelta a la Laguna',     47,'Borde del cráter',                              10.00,  300,NULL,'Terreno arcilloso resbaloso en época lluviosa.',                                                                         false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  5),
( 89,'Cotopaxi - Lagunas de Limpiopungo',14,'Parque Nacional Cotopaxi',                       12.00,  200,NULL,NULL,                                                                                                                      true,  false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  4),
( 90,'Cotopaxi - Ruta al Refugio José Rivas', 14,'Parque Nacional Cotopaxi',                   4.00,  300,NULL,'Altitud 4864m. Aclimatarse previamente. Alta probabilidad de mal de montaña.',                                          true,  false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  2),
( 91,'Pasochoa - Circuito Bosque Protector', 45,'Bosque Protector Pasochoa',                   9.00,  546,NULL,NULL,                                                                                                                      false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  4),
( 92,'Pululahua - Descenso al Cráter',    51,'Reserva Geobotánica Pululahua',                  8.00,  600,NULL,NULL,                                                                                                                      false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  4),
( 93,'Fuya Fuya - Lagunas de Mojanda',    43,'Mojanda, Otavalo',                               8.00,  500,NULL,'Páramo pantanoso. Niebla frecuente. Llevar ropa impermeable.',                                                           false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  5),
( 94,'Rumiñahui Norte - Ascenso al Cráter',32,'Parque Nacional Cotopaxi',                     14.00, 1000,NULL,NULL,                                                                                                                      true,  false,NULL,NULL,NULL,'TREKKING',NULL,'SO002',  7),
( 95,'Imbabura - Ruta desde La Esperanza', 37,'Comunidad La Esperanza, Ibarra',               10.00, 1250,NULL,'Ruta larga y expuesta. Sin agua en la cima.',                                                                           false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO002',  6),
( 96,'El Altar - Laguna Amarilla',         17,'Collanes, Parque Nacional Sangay',              16.00,  800,   2,'Niebla frecuente. Cruce de ríos. Permiso PN Sangay requerido.',                                                         true,  false,NULL,NULL,NULL,'TREKKING',NULL,'SO002',NULL),
( 97,'Antisana - Páramo del Antisana',     16,'Reserva Ecológica Antisana',                   15.00,  600,NULL,'Permiso Reserva Antisana obligatorio. Zona de cóndores.',                                                               true,  false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  6),
( 98,'Atacazo - Laguna Verde',             41,'Lloa, Quito',                                    8.00,  600,NULL,NULL,                                                                                                                      false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  5),
( 99,'Cerro Hermoso - Valle Encantado',    38,'Parque Nacional Llanganates',                   20.00,  900,   2,'Niebla permanente y pantanos. Orientación difícil. GPS y guía local obligatorios.',                                      true,  false,NULL,NULL,NULL,'TREKKING',NULL,'SO003',NULL),
(100,'Sumaco - Base del Volcán (Ruta Cero)',46,'Wawa Sumaco, Napo',                            18.00,  800,   2,'Selva tropical densa. Alta humedad. Riesgo de serpientes.',                                                              false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO002',NULL),
(101,'Casitagua - Sendero Norte',           50,'Calderón, norte de Quito',                     6.00,  400,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  3),
(102,'Reventador - Cascada San Rafael',    49,'Vía Quito-Lago Agrio km 134',                   8.00,  300,NULL,'Volcán activo. Verificar IGEPN. Ruta resbaladiza por humedad.',                                                         false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  3),
(103,'Quilindaña - Laguna Verde',          25,'Reserva Ecológica Los Ilinizas',                15.00,  700,   2,NULL,                                                                                                                      false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO002',NULL),
(104,'Corazón - Sendero desde Aloa',       29,'Aloa, Mejía',                                  12.00, 1200,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO002',  7),
(105,'Cayambe - Páramo y Laguna San Marcos',15,'Reserva Cayambe-Coca',                         8.00,  400,NULL,'Permiso Reserva Cayambe-Coca obligatorio.',                                                                              true,  false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  4),
(106,'Rucu Pichincha - Sendero del Teleférico', 34,'Teleférico Quito, Pichincha',             10.00,  900,NULL,'Altitud máxima 4696m. Posible mal de montaña. Clima cambiante.',                                                         false, false,NULL,NULL,NULL,'TREKKING',NULL,'SO001',  5),
-- CICLISMO
(  4,'Tambopaxi',                        NULL,NULL,                                            NULL, NULL,NULL,NULL,                                                                                                                        true,  false,NULL,NULL,NULL,'CICLISMO','parque del coto','SO002',NULL),
(107,'Baños - Ruta de las Cascadas',     NULL,'Ruta Baños - Puyo (km 0-17)',                  17.00,  800,NULL,'Tráfico vehicular intenso. Usar luz y chaleco reflectivo.',                                                              false, false,NULL,NULL,NULL,'CICLISMO','Baños de Agua Santa, Tungurahua','SO001',  2),
(108,'Cotopaxi - Circuito Parque Nacional', 14,'Parque Nacional Cotopaxi',                    30.00,  500,NULL,NULL,                                                                                                                       true,  false,NULL,NULL,NULL,'CICLISMO',NULL,'SO001',  4),
(109,'Nono - Alaspungo',                 NULL,'Noroccidente de Quito',                         13.00,  800,NULL,'Singletrack técnico con raíces y barro. Sección de cascada resbaladiza.',                                                false, false,NULL,NULL,NULL,'CICLISMO','Nono, Pichincha','SO002',  3),
(110,'Cruz Loma - Rucu Pichincha Descenso', 34,'Teleférico de Quito, cara este',              17.00, 1115,NULL,'Singletrack muy técnico. Alta velocidad en descensos. Casco y protecciones completas obligatorias.',                     false, false,NULL,NULL,NULL,'CICLISMO',NULL,'SO003',  3),
(111,'Yanacocha Integral',               NULL,'Noroccidente de Quito',                         42.00,  709,NULL,'Ruta larga. Llevar kit de reparación y suficiente alimentación.',                                                         false, false,NULL,NULL,NULL,'CICLISMO','Bosque Protector Yanacocha','SO002',  5),
(112,'Lloa - Palmira Vuelta',            NULL,'Suroeste de Quito',                             36.00,  745,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'CICLISMO','Lloa, Pichincha','SO001',  4),
(113,'Papallacta - Baeza (Descenso)',    NULL,'Vía Quito-Tena km 60-95',                      35.00, 1800,NULL,'Tráfico de camiones. Temperatura muy baja en Papallacta (3300m).',                                                       false, false,NULL,NULL,NULL,'CICLISMO','Papallacta, Napo','SO001',  3),
(114,'Chimborazo - Circuito Norte',       13,'Refugios Whymper, Riobamba',                    35.00, 1000,NULL,'Altitud máxima 4800m. Viento muy fuerte en páramo. Ropa térmica obligatoria.',                                           false, false,NULL,NULL,NULL,'CICLISMO',NULL,'SO002',  5),
(115,'Atucucho - Ruta Técnica',          NULL,'Norte de Quito',                                10.00, 1051,NULL,'Descensos muy abruptos. Solo ciclistas experimentados.',                                                                 false, false,NULL,NULL,NULL,'CICLISMO','Atucucho, Quito','SO003',  2),
(116,'Quilotoa - Circuito del Cráter',    47,'Zumbahua, Cotopaxi',                            10.00,  400,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'CICLISMO',NULL,'SO001',  2),
(117,'Ilinizas - Circuito Base',          20,'Reserva Ecológica Los Ilinizas',                20.00,  800,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'CICLISMO',NULL,'SO002',  3),
(118,'Machachi - Páramo Andino',         NULL,'Valle de Machachi',                             25.00,  600,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'CICLISMO','Machachi, Mejía','SO001',  3),
(119,'Antisana - Ruta Reserva Sur',       16,'Reserva Ecológica Antisana',                    18.00,  600,NULL,'Permiso Reserva Antisana obligatorio. Zona de cóndores y osos de anteojos.',                                             true,  false,NULL,NULL,NULL,'CICLISMO',NULL,'SO002',  3),
(120,'Guagua Pichincha - Descenso Cruz Loma', 28,'Teleférico de Quito',                       15.00,  900,NULL,'Volcán activo. Gases ocasionales. Singletrack técnico con exposición.',                                                  false, false,NULL,NULL,NULL,'CICLISMO',NULL,'SO003',  2),
(121,'Imbabura - Circuito Laguna San Pablo', 37,'Laguna San Pablo, Otavalo',                  22.00,  400,NULL,NULL,                                                                                                                       false, false,NULL,NULL,NULL,'CICLISMO',NULL,'SO001',  3);

-- ---------------------------------------------------------------------------
-- rutas_alpinismo
-- ---------------------------------------------------------------------------

INSERT INTO public.rutas_alpinismo
    (ruta_id, escala_alpina_ifas_id, dificultad_roca_id, dificultad_hielo_id, compromiso_id, yosemite_id, sadday_nivel_tecnico_id, sadday_nivel_fisico_id, equipo_montana_id)
VALUES
( 1,'IFAS003','UIAA-F001','WI003','C003','Y002','SA003','SA003',NULL),
(57,'IFAS002','UIAA-F002','WI003','C003','Y001','SA004','SA004',   1),
(58,'IFAS002','UIAA-F001','WI003','C002','Y001','SA003','SA003',   1),
(59,'IFAS002','UIAA-F001','WI003','C002','Y001','SA003','SA004',   1),
(60,'IFAS004','UIAA-F002','WI004','C004','Y001','SA004','SA004',   1),
(61,'IFAS004','UIAA-F004','WI004','C004','Y004','SA005','SA004',   1),
(62,'IFAS003','UIAA-F003','WI003','C003','Y001','SA004','SA003',   1),
(63,'IFAS002','UIAA-F003','WI001','C002','Y003','SA002','SA003',   2),
(64,'IFAS001','UIAA-F001','WI002','C001','Y001','SA002','SA002',   2),
(65,'IFAS002','UIAA-F002','WI002','C002','Y002','SA002','SA002',   2),
(66,'IFAS001','UIAA-F001','WI001','C001','Y001','SA002','SA002',   6),
(67,'IFAS001','UIAA-F001','WI001','C001','Y001','SA001','SA002',   6),
(68,'IFAS001','UIAA-F001','WI001','C001','Y001','SA002','SA002',   6),
(69,'IFAS003','UIAA-F002','WI003','C003','Y001','SA003','SA004',   1),
(70,'IFAS001','UIAA-F002','WI001','C002','Y002','SA002','SA002',   2),
(71,'IFAS002','UIAA-F002','WI002','C002','Y001','SA003','SA003',   2),
(72,'IFAS002','UIAA-F001','WI002','C002','Y001','SA002','SA003',   2),
(73,'IFAS001','UIAA-F001','WI001','C001','Y001','SA001','SA001',   6),
(74,'IFAS001','UIAA-F001','WI001','C001','Y001','SA001','SA002',   6);

-- ---------------------------------------------------------------------------
-- rutas_ciclismo
-- ---------------------------------------------------------------------------

INSERT INTO public.rutas_ciclismo
    (ruta_id, tipo_bicicleta, dificultad_tecnica, superficie_predominante, ciclabilidad_pct)
VALUES
(  4,'DOBLE_SUSPENSION','S1','Lastrado',             100.00),
(107,'RIGIDA',          'S1','Asfalto y empedrado',   95.00),
(108,'RIGIDA',          'S1','Carretera de tierra',   90.00),
(109,'DOBLE_SUSPENSION','S2','Singletrack y sendero', 75.00),
(110,'DOBLE_SUSPENSION','S3','Singletrack técnico',   70.00),
(111,'DOBLE_SUSPENSION','S2','Singletrack y carretera',80.00),
(112,'RIGIDA',          'S1','Camino de tierra',      88.00),
(113,'ENDURO',          'S2','Asfalto',              100.00),
(114,'DOBLE_SUSPENSION','S2','Camino de tierra y páramo',85.00),
(115,'ENDURO',          'S3','Singletrack técnico',   65.00),
(116,'RIGIDA',          'S1','Camino de tierra',      90.00),
(117,'DOBLE_SUSPENSION','S2','Singletrack y camino',  80.00),
(118,'RIGIDA',          'S1','Camino de tierra',      92.00),
(119,'DOBLE_SUSPENSION','S2','Carretera de tierra',   85.00),
(120,'ENDURO',          'S3','Singletrack técnico',   68.00),
(121,'RUTA',            'S0','Asfalto',              100.00);

-- ---------------------------------------------------------------------------
-- rutas_escalada
-- ---------------------------------------------------------------------------

INSERT INTO public.rutas_escalada
    (ruta_id, dificultad_roca_id, tipo_escalada, num_cintas, altura_via_m, tipo_roca)
VALUES
( 2,'UIAA-F007','DEPORTIVA',   5,  10,'Granito'),
(75,'UIAA-F006','DEPORTIVA',   8,  30,'Andesita'),
(76,'UIAA-F007','DEPORTIVA',  10,  40,'Andesita'),
(77,'UIAA-F005','DEPORTIVA',  12,  60,'Andesita'),
(78,'UIAA-F004','DEPORTIVA',   6,  25,'Andesita'),
(79,'UIAA-F006','DEPORTIVA',   8,  35,'Riolita'),
(80,'UIAA-F007','DEPORTIVA',   6,  40,'Riolita'),
(81,'UIAA-F007','TRADICIONAL',NULL,200,'Riolita'),
(82,'UIAA-F006','DEPORTIVA',  10,  25,'Basalto'),
(83,'UIAA-F006','DEPORTIVA',   8,  30,'Caliza'),
(84,'UIAA-F007','MIXTA',     NULL, 500,'Roca volcánica'),
(85,'UIAA-F005','DEPORTIVA',   6,  20,'Basalto'),
(86,'UIAA-F005','TRADICIONAL',NULL, 80,'Roca sedimentaria');

-- ---------------------------------------------------------------------------
-- rutas_trekking
-- ---------------------------------------------------------------------------

INSERT INTO public.rutas_trekking
    (ruta_id, dificultad_id, es_circular, fuentes_agua, tipo_terreno)
VALUES
(  3,'DS001',true, false,'Bosque pajonal'),
( 87,'DS003',true, true, 'Páramo, camino de tierra, pueblos indígenas'),
( 88,'DS002',true, false,'Arcilla y tierra sobre borde de cráter'),
( 89,'DS001',true, true, 'Páramo plano, camino vehicular de tierra'),
( 90,'DS002',false,false,'Arena volcánica, roca'),
( 91,'DS002',true, true, 'Bosque montano húmedo, sendero de tierra'),
( 92,'DS002',false,true, 'Bosque nuboso, sendero de tierra'),
( 93,'DS002',false,true, 'Páramo húmedo, pastizal'),
( 94,'DS003',false,true, 'Páramo, roca volcánica, tierra'),
( 95,'DS003',false,true, 'Páramo pedregoso, sendero de tierra'),
( 96,'DS003',false,true, 'Páramo, cruce de ríos, tierra'),
( 97,'DS002',false,true, 'Páramo abierto, camino de tierra'),
( 98,'DS002',false,true, 'Páramo, sendero de tierra'),
( 99,'DS004',false,true, 'Páramo, pantano, vegetación densa'),
(100,'DS003',false,true, 'Selva tropical, barro, raíces'),
(101,'DS002',false,false,'Páramo seco, tierra y roca'),
(102,'DS001',false,true, 'Selva baja, sendero húmedo'),
(103,'DS003',false,true, 'Páramo, roca, barro'),
(104,'DS003',false,true, 'Páramo abierto, tierra y hierba'),
(105,'DS002',false,true, 'Páramo andino, camino de tierra'),
(106,'DS002',false,false,'Páramo rocoso, arena volcánica');

-- ---------------------------------------------------------------------------
-- Secuencias
-- ---------------------------------------------------------------------------

SELECT pg_catalog.setval('public.acceso_ruta_por_nivel_id_seq',  6, true);
SELECT pg_catalog.setval('public.configuracion_sistema_id_seq',  3, true);
SELECT pg_catalog.setval('public.dignidades_id_seq',             9, true);
SELECT pg_catalog.setval('public.equipo_montana_id_seq',         6, true);
SELECT pg_catalog.setval('public.estado_acceso_id_seq',          5, true);
SELECT pg_catalog.setval('public.estado_habilitacion_id_seq',    3, true);
SELECT pg_catalog.setval('public.mountains_id_seq',             51, true);
SELECT pg_catalog.setval('public.roles_sistema_id_seq',          4, true);
SELECT pg_catalog.setval('public.rutas_id_seq',                121, true);
SELECT pg_catalog.setval('public.tipo_socio_club_id_seq',        6, true);
