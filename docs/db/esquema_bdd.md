# Esquema de Base de Datos — Sadday App

> Generado con Mermaid ERD. Refleja el esquema completo de PostgreSQL definido en `db/migrations/`.
> Última actualización: 2026-05-01 (sincronizado con V58).

---

```mermaid
erDiagram

    %% =========================================================
    %% CATÁLOGOS DE DIFICULTAD
    %% =========================================================
    ESCALA_ALPINA_IFAS {
        varchar  id PK
        varchar  grado
        varchar  nombre
        text     descripcion
        smallint rank
    }
    DIFICULTAD_ROCA_UIAA_FRANCESA {
        varchar  id PK
        varchar  uiaa
        varchar  francesa
        text     descripcion
        smallint rank
    }
    DIFICULTAD_HIELO_WI {
        varchar  id PK
        varchar  grado
        text     descripcion
        smallint rank
    }
    COMPROMISO {
        varchar  id PK
        varchar  tipo
        text     descripcion
        smallint rank
    }
    SISTEMA_CLASES_YOSEMITE {
        varchar  id PK
        varchar  tipo
        text     descripcion
        smallint rank
    }
    SADDAY_RIESGO_EXIGENCIA {
        varchar  id PK
        smallint valor
        varchar  escala
        text     descripcion
        smallint rank
    }
    DIFICULTAD_SENDERISMO {
        varchar  id PK
        varchar  nombre
        text     descripcion
        smallint rank
    }

    %% =========================================================
    %% CATÁLOGOS DE SOCIOS Y ROLES
    %% =========================================================
    CLASIFICACION_SOCIO {
        varchar  id PK
        smallint nivel
        varchar  nombre
        text     descripcion
    }
    TIPO_SOCIO_CLUB {
        smallint id PK
        varchar  nombre
        text     descripcion
    }
    ESTADO_HABILITACION {
        smallint id PK
        varchar  nombre
        text     descripcion
    }
    ROLES_SISTEMA {
        smallint id PK
        varchar  nombre
        text     descripcion
    }
    DIGNIDADES {
        int     id PK
        varchar nombre
        text    descripcion
    }
    PUBLICO_OBJETIVO {
        varchar  id PK
        varchar  nombre
        smallint orden
    }
    FORMATO_SALIDA {
        varchar  id PK
        varchar  nombre
        smallint orden
    }
    EQUIPO_MONTANA {
        int     id PK
        varchar nombre UK
        text    descripcion
    }
    ESTADO_ACCESO {
        smallint id PK
        varchar  codigo UK
        varchar  nombre
        text     descripcion
    }

    %% =========================================================
    %% STORAGE DE DOCUMENTOS (PDFs)
    %% =========================================================
    DOCUMENTOS {
        uuid    id PK
        varchar storage_provider
        varchar object_key
        varchar filename
        varchar content_type
        bigint  size_bytes
        varchar checksum_sha256
        varchar checksum_md5 "nullable — ETag de MinIO/S3"
        timestamp created_at
    }

    %% =========================================================
    %% SOCIOS Y AUTENTICACIÓN
    %% =========================================================
    SOCIOS {
        uuid      id PK
        varchar   nombre
        varchar   apellido
        varchar   cedula UK
        varchar   correo UK
        varchar   telefono
        text      direccion
        date      fecha_nacimiento
        date      fecha_ingreso
        date      fecha_salida
        varchar   tipo_sangre
        varchar   emergency_contact_name
        varchar   emergency_contact_phone
        text      emergency_contact_direccion
        varchar   emergency_contact_name2
        varchar   emergency_contact_phone2
        text      emergency_contact_direccion2
        smallint  estado_habilitacion_id FK
        smallint  tipo_socio_id FK
        varchar   nivel_tecnico_id FK
        smallint  rol_sistema_id FK
        smallint  estado_acceso_id FK
        boolean   es_jefe_montana
        timestamp created_at
        timestamp updated_at
    }
    USUARIOS_AUTH {
        uuid      id PK
        uuid      socio_id FK
        varchar   username UK
        varchar   password_hash
        text      totp_secret
        boolean   totp_enabled
        smallint  failed_attempts
        boolean   login_blocked
        timestamp blocked_until
        boolean   password_must_change
        timestamp last_login
        timestamp created_at
        timestamp updated_at
    }
    REFRESH_TOKENS {
        uuid        id PK
        uuid        socio_id FK
        varchar     token_hash UK
        text        user_agent
        varchar     ip_address
        varchar     platform "WEB|MOBILE|DESKTOP"
        varchar     device_id "SHA-256(userAgent+platform) truncado"
        timestamp   expires_at
        boolean     revoked
        timestamp   revoked_at
        timestamptz last_used_at "nullable — actualizado en cada rotación"
        timestamp   created_at
    }
    PASSWORD_RESET_TOKENS {
        uuid      id PK
        uuid      socio_id FK
        varchar   token_hash UK
        timestamp expires_at
        boolean   used
        timestamp created_at
    }
    EMAIL_VERIFICATION_TOKENS {
        uuid      id PK
        uuid      socio_id FK "nullable — nulo en pre-registro"
        varchar   token_hash UK
        varchar   cedula "solo en pre-registro"
        varchar   correo "solo en pre-registro"
        varchar   telefono "solo en pre-registro"
        varchar   nombre "nullable — prefilled desde CSV"
        varchar   apellido "nullable — prefilled desde CSV"
        varchar   tipo_socio_nombre "nullable — prefilled desde CSV"
        varchar   nivel_tecnico_nombre "nullable — prefilled desde CSV"
        timestamp expires_at
        boolean   used
        timestamp created_at
    }
    ESTADO_CUOTAS {
        bigint    id PK
        uuid      socio_id FK
        numeric   valor
        date      fecha
        varchar   estado
        uuid      registrado_por_id FK
        timestamp created_at
    }
    MFA_CHALLENGE_TOKENS {
        uuid      id PK
        uuid      socio_id FK
        varchar   token_hash UK
        varchar   ip_address
        varchar   user_agent
        timestamp expires_at
        boolean   used
        smallint  attempts
    }
    COUNTRY_CHALLENGE_TOKENS {
        uuid      id PK
        uuid      socio_id "sin FK formal"
        varchar   token_hash UK
        varchar   code_hash
        varchar   ip_address
        text      user_agent
        timestamp expires_at
        boolean   used
        smallint  attempts
        timestamp created_at
    }
    SECURITY_EVENTS {
        uuid        id PK
        uuid        socio_id FK "nullable ON DELETE SET NULL"
        varchar     username
        varchar     event_type
        varchar     ip_address
        varchar     country_code
        varchar     city
        text        user_agent
        varchar     device_id
        uuid        session_id "sin FK — correlación de auditoría"
        jsonb       metadata
        timestamptz created_at
    }

    %% =========================================================
    %% MONTAÑAS Y RUTAS
    %% =========================================================
    MOUNTAINS {
        int       id PK
        varchar   nombre
        varchar   region
        int       altitud
        varchar   pais
        timestamp created_at
        timestamp updated_at
    }
    RUTAS {
        int       id PK
        varchar   nombre
        varchar   tipo_actividad "ALPINISMO|ESCALADA|TREKKING|CICLISMO"
        int       mountain_id FK "nullable"
        varchar   lugar_referencia "para rutas sin cima específica"
        varchar   sector_zona
        numeric   longitud_km
        int       desnivel_m
        smallint  duracion_dias
        smallint  duracion_horas
        text      peligros_notas
        varchar   nivel_minimo_socio_id FK
        boolean   requiere_permisos
        text      documentacion_url
        text      track_url
        boolean   aprobada
        uuid      aprobada_por_id FK
        timestamp aprobada_en
        uuid      propuesta_por_id FK
        timestamp created_at
        timestamp updated_at
    }
    RUTAS_ALPINISMO {
        int      ruta_id PK "FK → rutas"
        varchar  escala_alpina_ifas_id FK
        varchar  dificultad_roca_id FK
        varchar  dificultad_hielo_id FK
        varchar  compromiso_id FK
        varchar  yosemite_id FK
        varchar  sadday_nivel_tecnico_id FK
        varchar  sadday_nivel_fisico_id FK
        int      equipo_montana_id FK "nullable"
    }
    RUTAS_ESCALADA {
        int      ruta_id PK "FK → rutas"
        varchar  dificultad_roca_id FK
        varchar  tipo_escalada "DEPORTIVA|TRADICIONAL|MIXTA|BOULDER"
        smallint num_cintas "nullable"
        int      altura_via_m "nullable"
        varchar  tipo_roca "nullable"
    }
    RUTAS_TREKKING {
        int      ruta_id PK "FK → rutas"
        varchar  dificultad_senderismo_id FK
        boolean  es_circular
        boolean  fuentes_agua
        varchar  tipo_terreno "nullable"
    }
    RUTAS_CICLISMO {
        int      ruta_id PK "FK → rutas"
        varchar  tipo_bicicleta "RIGIDA|DOBLE_SUSPENSION|ENDURO|GRAVEL|RUTA"
        varchar  dificultad_tecnica "S0..S4 nullable"
        varchar  superficie_predominante "nullable"
        numeric  ciclabilidad_pct "nullable 0-100"
    }
    CONTACTOS {
        int       id PK
        varchar   nombre
        varchar   telefono UK
        varchar   correo
        text      notas
        timestamp created_at
        timestamp updated_at
    }
    CONTACTOS_RUTAS {
        int      id PK
        int      contacto_id FK
        int      ruta_id FK
        varchar  tipo_contacto "GUIA|TRANSPORTE|REFUGIO|ALMUERZO"
        boolean  activo
        timestamp created_at
        timestamp updated_at
    }
    ACCESO_RUTA_POR_NIVEL {
        smallint id PK
        varchar  nivel_socio_id FK
        varchar  max_ifas_id FK
        varchar  max_roca_id FK
        varchar  max_hielo_id FK
        varchar  max_compromiso_id FK
        varchar  max_yosemite_id FK
        varchar  max_sadday_tecnico_id FK
        varchar  max_sadday_fisico_id FK
        uuid     updated_by_id FK
        timestamp updated_at
    }
    RUTA_DOCUMENTOS {
        uuid      id PK
        int       ruta_id FK
        uuid      documento_id FK
        uuid      subido_por_id FK "nullable ON DELETE SET NULL"
        timestamp created_at
    }

    %% =========================================================
    %% SALIDAS
    %% =========================================================
    SALIDA {
        uuid     id PK
        varchar  nombre
        date     fecha_inicio
        time     hora_encuentro_club
        date     fecha_fin
        time     hora_estimada_regreso_club
        int      ruta_id FK "nullable — V36"
        varchar  tipo_actividad "ALPINISMO|ESCALADA|TREKKING|CICLISMO nullable — V39"
        varchar  publico_objetivo_id FK "nullable — V40"
        varchar  formato_salida_id FK "nullable — V40"
        varchar  nivel_minimo_requerido_id FK
        smallint capacidad_maxima
        varchar     estado "estado_salida ENUM"
        boolean     inscripciones_cerradas
        varchar     jefe_abandono_nombre "nullable — nombre del jefe si abandonó la salida"
        boolean     eliminada "soft-delete"
        timestamptz eliminada_en "nullable"
        uuid        eliminada_por_id FK "nullable"
        text        motivo_eliminacion "nullable"
        text        motivo_cancelacion "nullable"
        uuid        cancelada_por_id FK "nullable"
        timestamptz cancelada_en "nullable"
        uuid        creado_por_id FK
        timestamp   created_at
        timestamp   updated_at
    }
    SALIDA_PARTICIPANTES {
        bigint    id PK
        uuid      salida_id FK
        uuid      socio_id FK
        varchar   estado_inscripcion "estado_inscripcion ENUM"
        uuid      riesgo_aprobado_por_directivo FK "nullable"
        uuid      riesgo_aprobado_por_jefe FK "nullable"
        timestamp riesgo_aprobado_en "nullable"
        text      motivo_directivo "nullable"
        text      motivo_jefe "nullable"
        timestamp created_at
        timestamp updated_at
    }
    SALIDA_PARTICIPANTE_DIGNIDADES {
        bigint  id PK
        bigint  participante_id FK
        int     dignidad_id FK
    }

    %% =========================================================
    %% INFORMES
    %% =========================================================
    INFORME_SALIDA {
        uuid      id PK
        uuid      salida_id FK
        text      condiciones_meteorologicas
        boolean   se_realizo
        boolean   logro_cumbre
        time      hora_salida_club
        time      hora_llegada_montana
        time      hora_cumbre
        time      hora_inicio_descenso
        time      hora_llegada_autos
        time      hora_regreso_club
        text      cronica
        text      observaciones
        text      comentarios_varios
        boolean   alquilo_guia
        numeric   costo_guia
        int       contacto_guia_id FK
        boolean   alquilo_refugio
        varchar   nombre_refugio
        numeric   costo_refugio
        int       contacto_refugio_id FK
        boolean   acampo
        varchar   nombre_camping
        numeric   costo_camping
        int       contacto_camping_id FK
        varchar   donde_autos
        varchar   autos_descripcion
        varchar   autos_link_ubicacion
        numeric   costo_parqueadero
        numeric   costo_por_persona "nullable"
        numeric   costo_total
        uuid      validado_por_id FK
        timestamp validado_en
        uuid      documento_id FK
        timestamp created_at
        timestamp updated_at
    }
    SEGMENTOS_VIAJE {
        bigint   id PK
        uuid     informe_salida_id FK
        smallint orden
        varchar  origen
        varchar  destino
        boolean  alquilo_transporte
        varchar  tipo_transporte "CAMIONETA|FURGONETA|BUS_MEDIANO|BUS_GRANDE"
        numeric  costo_individual
        int      contacto_id FK
    }
    INFORME_SALIDA_RECONOCIMIENTOS {
        bigint    id PK
        uuid      informe_id FK
        uuid      socio_id FK
        varchar   tipo "AMONESTADO|DESTACADO"
        text      motivo
        uuid      registrado_por_id FK
        timestamp created_at
    }

    %% =========================================================
    %% ACTAS DE REUNIÓN
    %% =========================================================
    ACTAS_REUNION {
        uuid      id PK
        integer   numero_reunion "nullable"
        date      fecha
        time      hora
        time      hora_fin "nullable"
        varchar   lugar "nullable — V37"
        varchar   tipo_acta "DIRECTIVA|SOCIOS"
        text      actividades_realizadas_desc
        text      actividades_por_realizar
        text      acuerdos "nullable — extraído del desarrollo"
        text      varios
        text      observaciones
        uuid      presidente_reunion_id FK "nullable"
        uuid      secretaria_reunion_id FK "nullable"
        uuid      creada_por_id FK
        uuid      documento_id FK
        tsvector  search_vector
        timestamp created_at
        timestamp updated_at
    }
    ASISTENTES_REUNION {
        bigint  id PK
        uuid    acta_id FK
        uuid    socio_id FK "nullable — V37"
        varchar nombre_raw "nullable — nombre tal como aparece en .md"
    }
    ACTA_INFORMES_SALIDA {
        bigint id PK
        uuid   acta_id FK
        uuid   informe_id FK
    }

    %% =========================================================
    %% HISTORIAL HABILITACIÓN
    %% =========================================================
    SOCIO_HABILITACION_LOG {
        bigint    id PK
        uuid      socio_id FK
        smallint  estado_anterior_id FK
        smallint  estado_nuevo_id FK
        uuid      cambiado_por_id FK
        timestamptz cambiado_en
        varchar   fuente "MANUAL | CSV"
        text      csv_key "nullable — clave S3 origen"
        text      notas "nullable"
    }

    %% =========================================================
    %% SISTEMA
    %% =========================================================
    AUDITORIA {
        bigint    id PK
        uuid      socio_id FK
        varchar   accion
        varchar   entidad_afectada
        varchar   entidad_id
        jsonb     datos_anteriores
        jsonb     datos_nuevos
        varchar   ip_address
        text      user_agent
        varchar   resultado
        text      detalle
        timestamp created_at
    }
    CONFIGURACION_SISTEMA {
        smallint  id PK
        varchar   clave UK
        text      valor
        text      descripcion
        uuid      updated_by_id FK
        timestamp updated_at
    }

    %% =========================================================
    %% RELACIONES — SOCIOS Y AUTH
    %% =========================================================
    SOCIOS }o--|| ROLES_SISTEMA             : "rol_sistema_id"
    SOCIOS }o--|| TIPO_SOCIO_CLUB           : "tipo_socio_id"
    SOCIOS }o--|| ESTADO_HABILITACION       : "estado_habilitacion_id"
    SOCIOS }o--|| ESTADO_ACCESO             : "estado_acceso_id"
    SOCIOS }o--o| CLASIFICACION_SOCIO       : "nivel_tecnico_id"
    SOCIOS ||--|| USUARIOS_AUTH             : "socio_id"
    SOCIOS ||--o{ REFRESH_TOKENS            : "socio_id"
    SOCIOS ||--o{ PASSWORD_RESET_TOKENS     : "socio_id"
    SOCIOS ||--o{ EMAIL_VERIFICATION_TOKENS : "socio_id (nullable en pre-registro)"
    SOCIOS ||--o{ ESTADO_CUOTAS             : "socio_id"
    SOCIOS ||--o{ MFA_CHALLENGE_TOKENS      : "socio_id"
    SOCIOS ||--o{ SECURITY_EVENTS           : "socio_id (nullable)"

    %% =========================================================
    %% RELACIONES — MONTAÑAS Y RUTAS
    %% =========================================================
    MOUNTAINS |o--o{ RUTAS                          : "mountain_id (nullable)"
    RUTAS }o--o| CLASIFICACION_SOCIO                : "nivel_minimo_socio_id"
    RUTAS ||--o| RUTAS_ALPINISMO                    : "ruta_id"
    RUTAS ||--o| RUTAS_ESCALADA                     : "ruta_id"
    RUTAS ||--o| RUTAS_TREKKING                     : "ruta_id"
    RUTAS ||--o| RUTAS_CICLISMO                     : "ruta_id"
    RUTAS ||--o{ CONTACTOS_RUTAS                    : "ruta_id"
    RUTAS ||--o{ RUTA_DOCUMENTOS                    : "ruta_id"
    RUTA_DOCUMENTOS }o--|| DOCUMENTOS               : "documento_id"
    RUTA_DOCUMENTOS }o--o| SOCIOS                   : "subido_por_id"
    CONTACTOS_RUTAS }o--|| CONTACTOS                : "contacto_id"
    RUTAS_ALPINISMO }o--|| ESCALA_ALPINA_IFAS        : "escala_alpina_ifas_id"
    RUTAS_ALPINISMO }o--|| DIFICULTAD_ROCA_UIAA_FRANCESA : "dificultad_roca_id"
    RUTAS_ALPINISMO }o--|| DIFICULTAD_HIELO_WI       : "dificultad_hielo_id"
    RUTAS_ALPINISMO }o--|| COMPROMISO                : "compromiso_id"
    RUTAS_ALPINISMO }o--|| SISTEMA_CLASES_YOSEMITE   : "yosemite_id"
    RUTAS_ALPINISMO }o--|| SADDAY_RIESGO_EXIGENCIA   : "sadday_nivel_tecnico_id"
    RUTAS_ALPINISMO }o--|| SADDAY_RIESGO_EXIGENCIA   : "sadday_nivel_fisico_id"
    RUTAS_ALPINISMO }o--o| EQUIPO_MONTANA            : "equipo_montana_id"
    RUTAS_ESCALADA  }o--|| DIFICULTAD_ROCA_UIAA_FRANCESA : "dificultad_roca_id"
    RUTAS_TREKKING  }o--|| DIFICULTAD_SENDERISMO     : "dificultad_senderismo_id"
    CLASIFICACION_SOCIO ||--|| ACCESO_RUTA_POR_NIVEL   : "nivel_socio_id"
    ACCESO_RUTA_POR_NIVEL }o--|| ESCALA_ALPINA_IFAS    : "max_ifas_id"
    ACCESO_RUTA_POR_NIVEL }o--|| DIFICULTAD_ROCA_UIAA_FRANCESA : "max_roca_id"
    ACCESO_RUTA_POR_NIVEL }o--|| DIFICULTAD_HIELO_WI   : "max_hielo_id"
    ACCESO_RUTA_POR_NIVEL }o--|| COMPROMISO             : "max_compromiso_id"
    ACCESO_RUTA_POR_NIVEL }o--|| SISTEMA_CLASES_YOSEMITE : "max_yosemite_id"
    ACCESO_RUTA_POR_NIVEL }o--|| SADDAY_RIESGO_EXIGENCIA : "max_sadday_tecnico_id"
    ACCESO_RUTA_POR_NIVEL }o--|| SADDAY_RIESGO_EXIGENCIA : "max_sadday_fisico_id"

    %% =========================================================
    %% RELACIONES — SALIDAS
    %% =========================================================
    SALIDA }o--o| RUTAS                          : "ruta_id (nullable — V36)"
    SALIDA }o--o| PUBLICO_OBJETIVO               : "publico_objetivo_id (nullable — V40)"
    SALIDA }o--o| FORMATO_SALIDA                 : "formato_salida_id (nullable — V40)"
    SALIDA }o--o| CLASIFICACION_SOCIO            : "nivel_minimo_requerido_id"
    SALIDA }o--|| SOCIOS                         : "creado_por_id"
    SALIDA }o--o| SOCIOS                         : "eliminada_por_id (nullable)"
    SALIDA }o--o| SOCIOS                         : "cancelada_por_id (nullable)"
    SALIDA ||--o{ SALIDA_PARTICIPANTES           : "salida_id"
    SALIDA_PARTICIPANTES }o--|| SOCIOS           : "socio_id"
    SALIDA_PARTICIPANTES ||--o{ SALIDA_PARTICIPANTE_DIGNIDADES : "participante_id"
    SALIDA_PARTICIPANTE_DIGNIDADES }o--|| DIGNIDADES : "dignidad_id"

    %% =========================================================
    %% RELACIONES — INFORMES
    %% =========================================================
    SALIDA ||--o| INFORME_SALIDA                        : "salida_id"
    INFORME_SALIDA }o--o| SOCIOS                        : "validado_por_id"
    INFORME_SALIDA }o--o| CONTACTOS                     : "contacto_guia_id"
    INFORME_SALIDA }o--o| CONTACTOS                     : "contacto_refugio_id"
    INFORME_SALIDA }o--o| CONTACTOS                     : "contacto_camping_id"
    INFORME_SALIDA }o--o| DOCUMENTOS                    : "documento_id"
    INFORME_SALIDA ||--o{ SEGMENTOS_VIAJE               : "informe_salida_id"
    SEGMENTOS_VIAJE }o--o| CONTACTOS                    : "contacto_id"
    INFORME_SALIDA ||--o{ INFORME_SALIDA_RECONOCIMIENTOS : "informe_id"
    INFORME_SALIDA_RECONOCIMIENTOS }o--|| SOCIOS        : "socio_id"
    INFORME_SALIDA_RECONOCIMIENTOS }o--|| SOCIOS        : "registrado_por_id"

    %% =========================================================
    %% RELACIONES — ACTAS
    %% =========================================================
    ACTAS_REUNION }o--|| SOCIOS                  : "creada_por_id"
    ACTAS_REUNION }o--o| SOCIOS                  : "presidente_reunion_id"
    ACTAS_REUNION }o--o| SOCIOS                  : "secretaria_reunion_id"
    ACTAS_REUNION }o--o| DOCUMENTOS              : "documento_id"
    ACTAS_REUNION ||--o{ ASISTENTES_REUNION      : "acta_id"
    ASISTENTES_REUNION }o--o| SOCIOS             : "socio_id (nullable — V37)"
    ACTAS_REUNION ||--o{ ACTA_INFORMES_SALIDA    : "acta_id"
    ACTA_INFORMES_SALIDA }o--|| INFORME_SALIDA   : "informe_id"

    %% =========================================================
    %% RELACIONES — HISTORIAL HABILITACIÓN
    %% =========================================================
    SOCIOS ||--o{ SOCIO_HABILITACION_LOG            : "socio_id"
    SOCIOS ||--o{ SOCIO_HABILITACION_LOG            : "cambiado_por_id"
    SOCIO_HABILITACION_LOG }o--|| ESTADO_HABILITACION : "estado_anterior_id"
    SOCIO_HABILITACION_LOG }o--|| ESTADO_HABILITACION : "estado_nuevo_id"

    %% =========================================================
    %% RELACIONES — SISTEMA
    %% =========================================================
    SOCIOS ||--o{ AUDITORIA              : "socio_id"
    SOCIOS }o--o| CONFIGURACION_SISTEMA  : "updated_by_id"
```

---

## Convenciones de Nomenclatura

| Tipo | Convención | Ejemplo |
|---|---|---|
| Tablas | `snake_case` plural | `salida_participantes` |
| PKs | `id` | UUID v4 o SERIAL/BIGSERIAL |
| FKs | `{tabla_referenciada}_id` | `ruta_id`, `socio_id` |
| ENUMs | `UPPER_CASE` | `PLANIFICADA`, `AMONESTADO` |
| Índices | `idx_{tabla}_{campo}` | `idx_salida_estado` |
| Triggers | `trg_{tabla}_{accion}` | `trg_actas_search_vector` |
| Funciones | `fn_{tabla}_{accion}` | `fn_actas_update_search_vector` |

## ENUMs Nativos de PostgreSQL

| ENUM | Valores | Tabla que lo usa |
|---|---|---|
| `estado_salida` | `PLANIFICADA, EN_CURSO, REALIZADA, CANCELADA` | `salida.estado` |
| `estado_inscripcion` | `INSCRITO, CONFIRMADO, NO_FUE, CANCELADO, PENDIENTE_APROBACION, NEGADO` | `salida_participantes.estado_inscripcion` |
| `tipo_reconocimiento` | `AMONESTADO, DESTACADO` | `informe_salida_reconocimientos.tipo` |
| `tipo_acta` | `DIRECTIVA, SOCIOS` | `actas_reunion.tipo_acta` |

> **Importante:** Los ENUMs nativos de PostgreSQL requieren `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` + `@Enumerated(EnumType.STRING)` en la entidad JPA correspondiente.

## Valores Enum de Negocio (VARCHAR, no ENUM nativo)

| Campo | Tabla | Valores posibles |
|---|---|---|
| `tipo_actividad` | `rutas`, `salida` | `ALPINISMO, ESCALADA, TREKKING, CICLISMO` |
| `tipo_contacto` | `contactos_rutas` | `GUIA, TRANSPORTE, REFUGIO, ALMUERZO` |
| `tipo_transporte` | `segmentos_viaje` | `CAMIONETA, FURGONETA, BUS_MEDIANO, BUS_GRANDE` |
| `tipo_escalada` | `rutas_escalada` | `DEPORTIVA, TRADICIONAL, MIXTA, BOULDER` |
| `tipo_bicicleta` | `rutas_ciclismo` | `RIGIDA, DOBLE_SUSPENSION, ENDURO, GRAVEL, RUTA` |
| `dificultad_tecnica` | `rutas_ciclismo` | `S0, S1, S2, S3, S4` |
| `donde_autos` | `informe_salida` | `NO_AUTOS, PARQUEADERO_SEGURO, PARQUEADERO_INSEGURO, BASE_MONTANA, CALLE_SEGURO, CALLE_INSEGURO` |
| `storage_provider` | `documentos` | `S3, LOCAL` |
| `tipo_acta` | `actas_reunion` | `DIRECTIVA, SOCIOS` (también ENUM nativo) |

## Patrón de Herencia — Tablas de Rutas (Class Table Inheritance)

La tabla `rutas` actúa como padre con campos comunes a todos los tipos de actividad. Cada tipo tiene una tabla hija con PK = FK a `rutas.id`:

```
rutas (padre)
├── rutas_alpinismo  — escalas IFAS, UIAA roca, WI hielo, compromiso, Yosemite, Sadday T/F, equipo
├── rutas_escalada   — UIAA roca, tipo escalada, cintas, altura vía, tipo roca
├── rutas_trekking   — dificultad senderismo, circular, fuentes agua, terreno
└── rutas_ciclismo   — tipo bicicleta, dificultad técnica S0-S4, superficie, ciclabilidad %
```

Solo uno de los cuatro registros hijos existirá para cada ruta. `mountain_id` es nullable para tipos que no necesitan cima específica (trekking/ciclismo), usando `lugar_referencia` como alternativa.

## Full Text Search en Actas

El campo `search_vector TSVECTOR` en `actas_reunion` se mantiene automáticamente por el trigger `trg_actas_search_vector` que llama a `fn_actas_update_search_vector()`. El índice GIN sobre este campo permite búsquedas FTS eficientes.

**Campos incluidos en el índice (desde V37):** `actividades_realizadas_desc`, `actividades_por_realizar`, `acuerdos`, `varios`, `observaciones`.

**Partial unique indexes en `asistentes_reunion` (desde V37):**
- `uq_asistentes_acta_socio` — UNIQUE(acta_id, socio_id) WHERE socio_id IS NOT NULL
- `uq_asistentes_acta_nombre_raw` — UNIQUE(acta_id, nombre_raw) WHERE nombre_raw IS NOT NULL AND socio_id IS NULL

## Notas de Seguridad

- Las contraseñas se almacenan como hash (bcrypt/argon2) — nunca en claro.
- El `totp_secret` se cifra a nivel de aplicación (AES-256-GCM) antes de persistir.
- Los `refresh_tokens` se almacenan como hash SHA-256 — nunca el token en claro.
- Los tokens de reset/verificación también se almacenan como hash SHA-256.
- La tabla `auditoria` es **append-only**: no se actualiza ni se elimina registros.
- Los PDFs viven en S3/Object Storage; en BD solo hay una FK a `documentos` (object_key + checksum_sha256).
- `ip_address` en `refresh_tokens` y `auditoria` es `VARCHAR(45)` (no INET) para compatibilidad con el filtro JWT.
- En `email_verification_tokens`, `socio_id` es nullable para el flujo de pre-registro; en ese caso se usan `cedula`, `correo` y `telefono`.
- `security_events` es **append-only** al igual que `auditoria`. `session_id` es solo de correlación, no FK formal (V54).
- `salida.eliminada` implementa soft-delete: las salidas eliminadas no se borran de la BD, solo se marcan con `eliminada = true`.
- `auditoria.resultado` acepta `SUCCESS | FAILED | BLOCKED | PENDING` — `PENDING` se usa para eventos intermedios del flujo MFA.

## Historial de Migraciones

| Versión | Descripción |
|---|---|
| V1 | Tablas catálogo: escalas de dificultad, clasificación socio, roles, dignidades |
| V2 | Tablas core: socios, usuarios_auth, refresh_tokens, tokens de reset/verificación, estado_cuotas, auditoria |
| V3 | Montañas, rutas, contactos_rutas (original), acceso_ruta_por_nivel |
| V4 | Salidas, salida_participantes, salida_participante_dignidades |
| V5 | Informe de salida, informe_salida_reconocimientos |
| V6 | Actas de reunión, asistentes_reunion, acta_informes_salida |
| V7 | Tablas sistema: configuracion_sistema; seed lookup data |
| V8 | Seed data general |
| V9 | `rutas.track_url TEXT` |
| V10 | Tabla `documentos` (storage-agnostic para PDFs) |
| V11 | `informe_salida` y `actas_reunion`: `pdf_url`/`pdf_hash` → `documento_id FK→documentos` |
| V12 | Elimina `es_jefe_salida` de `salida_participantes`; migra a `salida_participante_dignidades` |
| V13 | `informe_salida`: añade `alquilo_transporte`, `costo_transporte`, `alquilo_guia`, `costo_guia`, `costo_total` |
| V14 | `informe_salida`: NOT NULL en alquilo_*; añade `contacto_transporte_id`, `contacto_guia_id`; dignidad "Conductor" |
| V15 | Tabla `equipo_montana` (6 seed entries); `rutas.equipo_montana_id FK` |
| V16 | `informe_salida.logro_cumbre BOOLEAN NOT NULL DEFAULT FALSE` |
| V17 | `actas_reunion.tipo_acta ENUM('DIRECTIVA','SOCIOS')` |
| V18 | Tabla `contactos` global (teléfono UNIQUE para deduplicación) |
| V19 | Refactoriza `contactos_rutas`: elimina columnas directas, añade `contacto_id FK→contactos`, `tipo_contacto VARCHAR`, `activo BOOLEAN`; elimina tabla `tipo_contacto` |
| V20 | Tabla `segmentos_viaje` (reemplaza `alquilo_transporte` del informe) |
| V21 | `informe_salida`: elimina `alquilo_transporte`/`costo_transporte`/`contacto_transporte_id`; `contacto_guia_id` → FK→contactos; añade alojamiento (refugio + camping) |
| V22 | Seed contactos mock (4 contactos + ruta Chimborazo) |
| V23 | `informe_salida`: añade `donde_autos`, `autos_descripcion`, `autos_link_ubicacion`; `costo_total` pasa a manual |
| V24 | `informe_salida.costo_parqueadero NUMERIC(8,2)` |
| V25 | `rutas`: `sadday_riesgo_id` → `sadday_nivel_tecnico_id` + `sadday_nivel_fisico_id` |
| V26 | `mountains.pais VARCHAR(100) DEFAULT 'Ecuador'`; seed 40 montañas ecuatorianas |
| V27 | `acceso_ruta_por_nivel`: `max_sadday_id` → `max_sadday_tecnico_id` + `max_sadday_fisico_id` |
| V28 | `estado_inscripcion`: añade valor `PENDIENTE_APROBACION` |
| V29 | `estado_inscripcion`: añade `NEGADO`; `salida_participantes`: añade `motivo_directivo`, `motivo_jefe` |
| V30 | `salida.inscripciones_cerradas BOOLEAN DEFAULT FALSE` (control de cierre por Jefe de Salida) |
| V31 | `email_verification_tokens`: `socio_id` nullable; añade `cedula`, `correo`, `telefono` (pre-registro) |
| V32 | Elimina montañas duplicadas con `region = 'Andes'`; reasigna rutas a montañas canónicas |
| V33 | Rutas multi-actividad: `tipo_actividad`, `lugar_referencia`, `nivel_minimo_socio_id`, `duracion_horas` en `rutas`; `mountain_id` nullable; elimina FKs de dificultad de `rutas`; crea `dificultad_senderismo`, `rutas_alpinismo`, `rutas_escalada`, `rutas_trekking`, `rutas_ciclismo`; migra datos históricos a `rutas_alpinismo` |
| V34 | Seed rutas Ecuador: rutas de alpinismo en montañas del seed (Chimborazo, Cotopaxi, Cayambe, Illinizas, etc.) |
| V35 | Tabla `socio_habilitacion_log`: auditoría de cambios de estado de habilitación (fuente: MANUAL o CSV); índices por socio y por fecha |
| V36 | `salida.ruta_id` pasa a nullable — permite salidas sin ruta de montaña (eventos, reuniones, ciclismo urbano) |
| V37 | `actas_reunion`: añade `numero_reunion`, `hora_fin`, `presidente_reunion_id`, `secretaria_reunion_id`, `acuerdos`; `lugar` pasa a nullable; actualiza trigger FTS para incluir `acuerdos`. `asistentes_reunion`: añade `nombre_raw`; `socio_id` pasa a nullable; reemplaza UNIQUE(acta_id, socio_id) por dos índices únicos parciales |
| V38 | `usuarios_auth.password_must_change BOOLEAN DEFAULT FALSE` — fuerza cambio de contraseña en primer login |
| V39 | `salida.tipo_actividad VARCHAR(20)` con CHECK constraint — registra categoría deportiva directamente en la salida (independiente de la ruta); backfill desde `ruta.tipo_actividad` |
| V40 | Reemplaza `clasificacion_sadday` (T1-T10) por dos tablas lookup: `publico_objetivo` (Socios/Juvenil/Adulto Mayor/Con externos/Verano) y `formato_salida` (Salida de campo/Reunión/Evento social/Entrenamiento/Paseo Relax); elimina `salida.tipo_salida_id` y tabla `clasificacion_sadday` |
| V41 | `documentos.checksum_md5 VARCHAR(32)` nullable — ETag que retorna MinIO/S3 al subir |
| V42 | `salida`: soft-delete (`eliminada`, `eliminada_en`, `eliminada_por_id`, `motivo_eliminacion`) + cancellation reason (`motivo_cancelacion`, `cancelada_por_id`, `cancelada_en`) |
| V43 | `refresh_tokens.platform VARCHAR(10) DEFAULT 'WEB'` — identifica la plataforma del cliente |
| V44 | Índices de rendimiento en `salida_participantes` para queries filtradas por `estado_inscripcion` |
| V45 | Tabla `mfa_challenge_tokens`: tokens temporales (5 min) para el segundo paso del login 2FA |
| V46 | `usuarios_auth.login_bloqueado_manualmente` (reemplazado y eliminado en V47) |
| V47 | Tabla `estado_acceso` (ACTIVE/BLOCKED/EX_MEMBER/PENDING_REGISTER/DISABLED); `socios.estado_acceso_id FK`; elimina `login_bloqueado_manualmente` de `usuarios_auth`; elimina tipo_socio "Pendiente Registro" |
| V48 | `email_verification_tokens`: añade `nombre`, `apellido`, `tipo_socio_nombre`, `nivel_tecnico_nombre` — datos pre-cargados para el flujo de importación CSV |
| V49 | `socios.es_jefe_montana BOOLEAN DEFAULT FALSE` — flag para directivos autorizados a aprobar inscripciones con nivel insuficiente |
| V50 | Tabla `ruta_documentos` (ruta_id, documento_id, subido_por_id) — documentos de permiso (PDF/Word/Excel) asociados a rutas |
| V51 | `refresh_tokens`: añade `last_used_at TIMESTAMPTZ` y `device_id VARCHAR(32)` — gestión de sesiones activas |
| V52 | Tabla `security_events`: registro de eventos de autenticación y detección de anomalías (separada de `auditoria`) |
| V53 | `security_events.country_code`: `CHAR(2)` → `VARCHAR(2)` para compatibilidad con Hibernate |
| V54 | `security_events`: elimina FK de `session_id` — es campo de correlación, no de integridad referencial |
| V55 | Tabla `country_challenge_tokens`: desafíos de verificación por país inusual en el login |
| V56 | `auditoria.resultado`: amplía CHECK constraint para incluir `PENDING` (para eventos intermedios como MFA challenge) |
| V57 | `informe_salida.costo_por_persona DECIMAL(10,2)` nullable |
| V58 | `salida.jefe_abandono_nombre VARCHAR(200)` nullable — nombre del jefe si abandonó la salida |
