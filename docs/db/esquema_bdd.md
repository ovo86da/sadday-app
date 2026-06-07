# Esquema de Base de Datos — Sadday App

> Generado con Mermaid ERD. Refleja el esquema completo de PostgreSQL definido en `db/migration/`.
> Última actualización: 2026-06-07 (sincronizado con V17).

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
        smallint  estado_habilitacion_id FK
        smallint  tipo_socio_id FK
        varchar   nivel_tecnico_id FK
        smallint  rol_sistema_id FK
        smallint  estado_acceso_id FK
        boolean   es_jefe_montana
        boolean   es_presidenta "solo una activa a la vez — V6"
        timestamp created_at
        timestamp updated_at
    }
    SOCIO_EMERGENCY_CONTACTS {
        uuid      id PK
        uuid      socio_id FK
        smallint  orden "1 o 2 — UNIQUE(socio_id, orden)"
        varchar   nombre_completo
        varchar   relacion
        varchar   celular "nullable"
        text      direccion "nullable"
        timestamp created_at
        timestamp updated_at
    }
    SOCIO_MEDICAL_INFO {
        uuid      id PK
        uuid      socio_id FK UK
        varchar   blood_type "nullable — A+, A-, B+, B-, AB+, AB-, O+, O-"
        boolean   has_relevant_allergies
        text      allergies_detail "nullable"
        boolean   has_relevant_medical_condition
        text      medical_condition_detail "nullable"
        boolean   uses_emergency_medication
        text      emergency_medication_detail "nullable"
        text      additional_notes "nullable"
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at "soft-delete nullable"
    }
    USUARIOS_AUTH {
        uuid      id PK
        uuid      socio_id FK
        varchar   username UK
        varchar   password_hash
        text      totp_secret
        boolean   totp_enabled
        bigint    last_used_totp_counter "anti-replay NIST §5.1.4.2, default -1"
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
        varchar   tipo_actividad "ALPINISMO|ESCALADA|TREKKING|CICLISMO|INTEGRAL — V10"
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
        varchar   estado "PENDIENTE|APROBADA|RECHAZADA — reemplaza boolean aprobada (V7)"
        uuid      revisada_por_id FK "nullable"
        timestamp revisada_en "nullable"
        text      motivo_rechazo "nullable"
        uuid      propuesta_por_id FK
        timestamp created_at
        timestamp updated_at
    }
    RUTAS_INTEGRAL {
        int      ruta_id PK "FK → rutas ON DELETE CASCADE"
        varchar  dificultad_maxima_descripcion "nullable"
        text     descripcion_itinerario "nullable"
        varchar  dificultad_max_tipo "nullable — V9"
    }
    RUTA_CUMBRES {
        int      ruta_id FK "PK compuesto (ruta_id, secuencia)"
        int      mountain_id FK
        smallint secuencia "orden de la cumbre en la integral"
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
    %% DOCUMENTOS LEGALES Y ACEPTACIONES
    %% =========================================================
    LEGAL_DOCUMENTS {
        uuid      id PK
        varchar   code "ej: ESTATUTOS, REGLAMENTO — UNIQUE(code, version)"
        varchar   title
        text      description "nullable"
        varchar   document_type "ej: ESTATUTO, REGLAMENTO, POLITICA, CONSENTIMIENTO"
        varchar   required_stage "REGISTRATION|PROFILE_COMPLETION|ACTIVITY_ENROLLMENT"
        int       version
        text      content "Markdown"
        varchar   content_hash "SHA-256 del contenido"
        boolean   active
        boolean   required
        boolean   requires_reacceptance_on_new_version
        timestamp approved_at "nullable"
        uuid      approved_by FK "nullable → socios"
        timestamp created_at
        timestamp updated_at
    }
    LEGAL_DOCUMENT_ACCEPTANCES {
        uuid      id PK
        uuid      socio_id FK
        uuid      legal_document_id FK
        varchar   document_code
        int       document_version
        varchar   content_hash "hash del contenido aceptado"
        timestamp accepted_at
        varchar   ip_address "nullable"
        text      user_agent "nullable"
        boolean   accepted
        timestamp created_at
    }
    ACTIVITY_RISK_DOCUMENTS {
        uuid      id PK
        uuid      activity_id FK "→ salida ON DELETE CASCADE"
        int       version "UNIQUE(activity_id, version)"
        text      content "Markdown"
        varchar   content_hash "SHA-256"
        boolean   active
        timestamp approved_at "nullable"
        uuid      approved_by FK "nullable → socios"
        timestamp created_at
        timestamp updated_at
    }
    ACTIVITY_RISK_ACCEPTANCES {
        uuid      id PK
        uuid      socio_id FK
        uuid      activity_id FK "→ salida"
        uuid      activity_risk_document_id FK
        int       document_version
        varchar   content_hash
        timestamp accepted_at
        varchar   ip_address "nullable"
        text      user_agent "nullable"
        timestamp created_at
    }
    AUDIT_LOG {
        uuid      id PK
        uuid      actor_user_id FK "nullable → socios"
        varchar   action "LEGAL_DOCUMENT_ACCEPTED|CREATED|MEDICAL_INFO_UPDATED|RETIRE_SOCIO|…"
        varchar   resource_type "nullable"
        uuid      resource_id "nullable"
        varchar   ip_address "nullable"
        timestamp created_at
    }

    %% =========================================================
    %% SISTEMA
    %% =========================================================
    API_KEYS {
        uuid        id PK
        uuid        socio_id FK
        varchar     nombre
        varchar     key_hash UK "SHA-256 del token"
        timestamptz created_at
        timestamptz expires_at "nullable"
        timestamptz last_used_at "nullable"
        timestamptz revoked_at "nullable"
    }
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
    SOCIOS ||--o{ SOCIO_EMERGENCY_CONTACTS  : "socio_id"
    SOCIOS ||--o| SOCIO_MEDICAL_INFO        : "socio_id"

    %% =========================================================
    %% RELACIONES — MONTAÑAS Y RUTAS
    %% =========================================================
    MOUNTAINS |o--o{ RUTAS                          : "mountain_id (nullable)"
    RUTAS }o--o| CLASIFICACION_SOCIO                : "nivel_minimo_socio_id"
    RUTAS ||--o| RUTAS_ALPINISMO                    : "ruta_id"
    RUTAS ||--o| RUTAS_ESCALADA                     : "ruta_id"
    RUTAS ||--o| RUTAS_TREKKING                     : "ruta_id"
    RUTAS ||--o| RUTAS_CICLISMO                     : "ruta_id"
    RUTAS ||--o| RUTAS_INTEGRAL                     : "ruta_id"
    RUTAS ||--o{ RUTA_CUMBRES                       : "ruta_id"
    RUTA_CUMBRES }o--|| MOUNTAINS                   : "mountain_id"
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
    %% RELACIONES — DOCUMENTOS LEGALES Y RIESGO
    %% =========================================================
    SOCIOS ||--o{ LEGAL_DOCUMENT_ACCEPTANCES    : "socio_id"
    LEGAL_DOCUMENT_ACCEPTANCES }o--|| LEGAL_DOCUMENTS : "legal_document_id"
    LEGAL_DOCUMENTS }o--o| SOCIOS               : "approved_by (nullable)"
    SALIDA ||--o{ ACTIVITY_RISK_DOCUMENTS       : "activity_id"
    ACTIVITY_RISK_DOCUMENTS }o--o| SOCIOS       : "approved_by (nullable)"
    SOCIOS ||--o{ ACTIVITY_RISK_ACCEPTANCES     : "socio_id"
    ACTIVITY_RISK_ACCEPTANCES }o--|| SALIDA     : "activity_id"
    ACTIVITY_RISK_ACCEPTANCES }o--|| ACTIVITY_RISK_DOCUMENTS : "activity_risk_document_id"
    SOCIOS ||--o{ AUDIT_LOG                     : "actor_user_id (nullable)"

    %% =========================================================
    %% RELACIONES — SISTEMA
    %% =========================================================
    SOCIOS ||--o{ API_KEYS              : "socio_id"
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
| `tipo_actividad` | `rutas`, `salida` | `ALPINISMO, ESCALADA, TREKKING, CICLISMO, INTEGRAL` |
| `estado` | `rutas` | `PENDIENTE, APROBADA, RECHAZADA` |
| `tipo_contacto` | `contactos_rutas` | `GUIA, TRANSPORTE, REFUGIO, ALMUERZO` |
| `tipo_transporte` | `segmentos_viaje` | `CAMIONETA, FURGONETA, BUS_MEDIANO, BUS_GRANDE` |
| `tipo_escalada` | `rutas_escalada` | `DEPORTIVA, TRADICIONAL, MIXTA, BOULDER` |
| `tipo_bicicleta` | `rutas_ciclismo` | `RIGIDA, DOBLE_SUSPENSION, ENDURO, GRAVEL, RUTA` |
| `dificultad_tecnica` | `rutas_ciclismo` | `S0, S1, S2, S3, S4` |
| `donde_autos` | `informe_salida` | `NO_AUTOS, PARQUEADERO_SEGURO, PARQUEADERO_INSEGURO, BASE_MONTANA, CALLE_SEGURO, CALLE_INSEGURO` |
| `storage_provider` | `documentos` | `S3, LOCAL` |
| `tipo_acta` | `actas_reunion` | `DIRECTIVA, SOCIOS` (también ENUM nativo) |
| `required_stage` | `legal_documents` | `REGISTRATION, PROFILE_COMPLETION, ACTIVITY_ENROLLMENT` |
| `action` | `audit_log` | `LEGAL_DOCUMENT_ACCEPTED, LEGAL_DOCUMENT_CREATED, LEGAL_DOCUMENT_ACTIVATED, MEDICAL_INFO_UPDATED, ACTIVITY_RISK_ACCEPTED, RETIRE_SOCIO, EMERGENCY_CONTACTS_UPDATED` |

## Patrón de Herencia — Tablas de Rutas (Class Table Inheritance)

La tabla `rutas` actúa como padre con campos comunes a todos los tipos de actividad. Cada tipo tiene una tabla hija con PK = FK a `rutas.id`:

```
rutas (padre)
├── rutas_alpinismo  — escalas IFAS, UIAA roca, WI hielo, compromiso, Yosemite, Sadday T/F, equipo
├── rutas_escalada   — UIAA roca, tipo escalada, cintas, altura vía, tipo roca
├── rutas_trekking   — dificultad senderismo, circular, fuentes agua, terreno
├── rutas_ciclismo   — tipo bicicleta, dificultad técnica S0-S4, superficie, ciclabilidad %
└── rutas_integral   — descripción itinerario, dificultad máxima, tipo dificultad (+ ruta_cumbres para las cimas)
```

Solo un registro hijo existirá para cada ruta. `mountain_id` en `rutas` es nullable para tipos que no necesitan cima específica (trekking/ciclismo/integral), usando `lugar_referencia` como alternativa. Las rutas integrales mapean sus cimas mediante la tabla `ruta_cumbres`.

## Full Text Search en Actas

El campo `search_vector TSVECTOR` en `actas_reunion` se mantiene automáticamente por el trigger `trg_actas_search_vector` que llama a `fn_actas_update_search_vector()`. El índice GIN sobre este campo permite búsquedas FTS eficientes.

**Campos incluidos en el índice (desde V37):** `actividades_realizadas_desc`, `actividades_por_realizar`, `acuerdos`, `varios`, `observaciones`.

**Partial unique indexes en `asistentes_reunion` (desde V37):**
- `uq_asistentes_acta_socio` — UNIQUE(acta_id, socio_id) WHERE socio_id IS NOT NULL
- `uq_asistentes_acta_nombre_raw` — UNIQUE(acta_id, nombre_raw) WHERE nombre_raw IS NOT NULL AND socio_id IS NULL

## Notas de Seguridad

- Las contraseñas se almacenan como hash (Argon2id) — nunca en claro.
- El `totp_secret` se cifra a nivel de aplicación (AES-256-GCM) antes de persistir.
- `usuarios_auth.last_used_totp_counter` previene reutilización de códigos TOTP (anti-replay NIST SP 800-63B §5.1.4.2).
- Las `api_keys` se almacenan como hash SHA-256 — nunca el valor en claro.
- Los `refresh_tokens` se almacenan como hash SHA-256 — nunca el token en claro.
- Los tokens de reset/verificación también se almacenan como hash SHA-256.
- La tabla `auditoria` es **append-only**: no se actualiza ni se elimina registros.
- La tabla `audit_log` es igualmente **append-only** y registra eventos de datos sensibles (aceptaciones legales, info médica, retiro de socios).
- Los PDFs viven en S3/Object Storage; en BD solo hay una FK a `documentos` (object_key + checksum_sha256).
- `ip_address` en `refresh_tokens` y `auditoria` es `VARCHAR(45)` (no INET) para compatibilidad con el filtro JWT.
- En `email_verification_tokens`, `socio_id` es nullable para el flujo de pre-registro; en ese caso se usan `cedula`, `correo` y `telefono`.
- `security_events` es **append-only** al igual que `auditoria`. `session_id` es solo de correlación, no FK formal (V54).
- `salida.eliminada` implementa soft-delete: las salidas eliminadas no se borran de la BD, solo se marcan con `eliminada = true`.
- `auditoria.resultado` acepta `SUCCESS | FAILED | BLOCKED | PENDING` — `PENDING` se usa para eventos intermedios del flujo MFA.
- `socio_medical_info` contiene datos sensibles de salud. No aparece en listings generales ni en logs; acceso restringido a Admin/Secretaria (info completa) y Directivo (resumen de emergencia: tipo de sangre, alergias, medicación de emergencia únicamente).
- `legal_document_acceptances` y `activity_risk_acceptances` almacenan `content_hash` para poder demostrar qué versión exacta del documento aceptó cada socio.

## Historial de Migraciones

El esquema actual es el resultado de una evolución incremental que fue **consolidada en V1** (un único archivo SQL de ~3100 líneas). Las migraciones V3–V5 son adiciones posteriores a esa consolidación.

| Versión | Archivo | Descripción |
|---|---|---|
| V1 | `V1__schema.sql` | **Esquema consolidado completo**: ENUMs nativos, 48 tablas, secuencias, índices, FK constraints, trigger + función FTS de actas. Incluye toda la evolución histórica desde el esquema original hasta el estado final. |
| V2 | `V2__seed_data.sql` | Datos de referencia: catálogos de dificultad, 3 estados de habilitación, 5 tipos de socio, roles, dignidades, formatos de salida, públicos objetivo, estados de acceso, 41 montañas ecuatorianas, 69+ rutas, parámetros de configuración del sistema. |
| V3 | `V3__api_keys.sql` | Tabla `api_keys` con índices — autenticación de integraciones externas y servidor MCP (FR-013). El hash SHA-256 de la key se almacena, nunca el valor en claro. |
| V4 | `V4__estados_tipos_socio.sql` | Seed: estados de habilitación `Licencia` (id=4) y `Re-inscripción` (id=5); tipo de socio `Ausente` (id=6); parámetros de configuración `BLOQUEAR_INSCRIPCION_LICENCIA` y `BLOQUEAR_INSCRIPCION_REINSCRIPCION`. |
| V5 | `V5__totp_anti_replay.sql` | `usuarios_auth.last_used_totp_counter BIGINT DEFAULT -1` — previene reutilización de códigos TOTP (NIST SP 800-63B §5.1.4.2 / RFC 6238 §5.2). |
| V6 | `V6__presidenta_flag.sql` | `socios.es_presidenta BOOLEAN DEFAULT false` con índice parcial — solo una presidenta activa a la vez (invariante en servicio). |
| V7 | `V7__ruta_estado.sql` | Reemplaza `rutas.aprobada BOOLEAN` por `estado VARCHAR(20)` con valores `PENDIENTE/APROBADA/RECHAZADA`. Agrega `revisada_por_id`, `revisada_en`, `motivo_rechazo`. Elimina `aprobada_por_id` y `aprobada_en`. |
| V8 | `V8__rutas_integrales.sql` | Tablas `rutas_integral` y `ruta_cumbres` para soporte de rutas de tipo INTEGRAL (multi-cumbre). |
| V9 | `V9__integral_dificultad_tipo.sql` | `rutas_integral.dificultad_max_tipo VARCHAR(20)` — tipo del tramo más difícil de la integral. |
| V10 | `V10__add_integral_tipo_actividad.sql` | Agrega `INTEGRAL` al CHECK constraint de `tipo_actividad` en `rutas` y `salida`. |
| V11 | `V11__legal_documents.sql` | Tablas `legal_documents` y `legal_document_acceptances` — gestión documental versionada con aceptación electrónica. Seed de 4 documentos iniciales. |
| V12 | `V12__emergency_contacts_table.sql` | Nueva tabla `socio_emergency_contacts` (hasta 2 contactos por socio). Migra datos de las columnas inline de `socios`. |
| V13 | `V13__medical_info.sql` | Nueva tabla `socio_medical_info`. Migra `tipo_sangre` de `socios`. |
| V14 | `V14__activity_risk_documents.sql` | Tablas `activity_risk_documents` y `activity_risk_acceptances` — documentos de riesgo específicos por salida con aceptación por participante. Tabla `audit_log` para eventos sensibles. |
| V15 | `V15__audit_log.sql` | *(incluida en V14)* |
| V16 | `V16__drop_emergency_contact_columns.sql` | Elimina las 6 columnas `emergency_contact_*` de `socios` (migradas a `socio_emergency_contacts` en V12). |
| V17 | `V17__drop_tipo_sangre.sql` | Elimina `socios.tipo_sangre` (migrado a `socio_medical_info.blood_type` en V13). |

> **Referencia histórica:** La evolución interna de V1 (equivalente a los V1–V58 originales antes de la consolidación) está documentada en el historial de git. Los hitos más relevantes fueron: rutas multi-actividad (V33), soft-delete de salidas (V42), gestión de sesiones activas (V51), security_events separada de auditoria (V52), y country challenge tokens (V55).
