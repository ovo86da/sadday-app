-- Estado consolidado del esquema - generado el 2026-05-05 - reemplaza V1 a V58

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: estado_inscripcion; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.estado_inscripcion AS ENUM (
    'INSCRITO',
    'CONFIRMADO',
    'NO_FUE',
    'CANCELADO',
    'PENDIENTE_APROBACION',
    'NEGADO'
);


--
-- Name: estado_salida; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.estado_salida AS ENUM (
    'PLANIFICADA',
    'EN_CURSO',
    'REALIZADA',
    'CANCELADA'
);


--
-- Name: tipo_acta; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tipo_acta AS ENUM (
    'DIRECTIVA',
    'SOCIOS'
);


--
-- Name: tipo_reconocimiento; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tipo_reconocimiento AS ENUM (
    'AMONESTADO',
    'DESTACADO'
);


--
-- Name: fn_actas_update_search_vector(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_actas_update_search_vector() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector('spanish',
        COALESCE(NEW.actividades_realizadas_desc, '') || ' ' ||
        COALESCE(NEW.actividades_por_realizar,    '') || ' ' ||
        COALESCE(NEW.acuerdos,                    '') || ' ' ||
        COALESCE(NEW.varios,                      '') || ' ' ||
        COALESCE(NEW.observaciones,               '')
    );
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: acceso_ruta_por_nivel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acceso_ruta_por_nivel (
    id smallint NOT NULL,
    nivel_socio_id character varying(10) NOT NULL,
    max_ifas_id character varying(10) NOT NULL,
    max_roca_id character varying(15) NOT NULL,
    max_hielo_id character varying(10) NOT NULL,
    max_compromiso_id character varying(10) NOT NULL,
    max_yosemite_id character varying(10) NOT NULL,
    updated_by_id uuid,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    max_sadday_tecnico_id character varying(10) NOT NULL,
    max_sadday_fisico_id character varying(10) NOT NULL
);


--
-- Name: acceso_ruta_por_nivel_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acceso_ruta_por_nivel_id_seq
    AS smallint
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acceso_ruta_por_nivel_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acceso_ruta_por_nivel_id_seq OWNED BY public.acceso_ruta_por_nivel.id;


--
-- Name: acta_informes_salida; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acta_informes_salida (
    id bigint NOT NULL,
    acta_id uuid NOT NULL,
    informe_id uuid NOT NULL
);


--
-- Name: acta_informes_salida_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acta_informes_salida_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acta_informes_salida_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acta_informes_salida_id_seq OWNED BY public.acta_informes_salida.id;


--
-- Name: actas_reunion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.actas_reunion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    fecha date NOT NULL,
    hora time without time zone NOT NULL,
    lugar character varying(200),
    actividades_realizadas_desc text,
    actividades_por_realizar text,
    varios text,
    observaciones text,
    creada_por_id uuid NOT NULL,
    search_vector tsvector,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    documento_id uuid,
    tipo_acta public.tipo_acta DEFAULT 'DIRECTIVA'::public.tipo_acta NOT NULL,
    numero_reunion integer,
    hora_fin time without time zone,
    presidente_reunion_id uuid,
    secretaria_reunion_id uuid,
    acuerdos text
);


--
-- Name: asistentes_reunion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asistentes_reunion (
    id bigint NOT NULL,
    acta_id uuid NOT NULL,
    socio_id uuid,
    nombre_raw character varying(200)
);


--
-- Name: asistentes_reunion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asistentes_reunion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asistentes_reunion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asistentes_reunion_id_seq OWNED BY public.asistentes_reunion.id;


--
-- Name: auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria (
    id bigint NOT NULL,
    actor_username character varying(100),
    accion character varying(100) NOT NULL,
    entidad_afectada character varying(100),
    entidad_id character varying(100),
    datos_anteriores jsonb,
    datos_nuevos jsonb,
    ip_address character varying(45),
    user_agent text,
    resultado character varying(20) NOT NULL,
    detalle text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT auditoria_resultado_check CHECK (((resultado)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying, 'BLOCKED'::character varying, 'PENDING'::character varying])::text[])))
);


--
-- Name: auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auditoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auditoria_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auditoria_id_seq OWNED BY public.auditoria.id;


--
-- Name: clasificacion_socio; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clasificacion_socio (
    id character varying(10) NOT NULL,
    nivel smallint NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion text NOT NULL,
    CONSTRAINT clasificacion_socio_nivel_check CHECK ((nivel >= 0))
);


--
-- Name: compromiso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compromiso (
    id character varying(10) NOT NULL,
    tipo character varying(10) NOT NULL,
    descripcion text NOT NULL,
    rank smallint NOT NULL,
    CONSTRAINT compromiso_rank_check CHECK ((rank >= 1))
);


--
-- Name: configuracion_sistema; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.configuracion_sistema (
    id smallint NOT NULL,
    clave character varying(100) NOT NULL,
    valor text NOT NULL,
    descripcion text,
    updated_by_id uuid,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: configuracion_sistema_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.configuracion_sistema_id_seq
    AS smallint
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: configuracion_sistema_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.configuracion_sistema_id_seq OWNED BY public.configuracion_sistema.id;


--
-- Name: contactos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contactos (
    id integer NOT NULL,
    nombre character varying(200) NOT NULL,
    telefono character varying(20),
    correo character varying(255),
    notas text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: contactos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contactos_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contactos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contactos_id_seq OWNED BY public.contactos.id;


--
-- Name: contactos_rutas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contactos_rutas (
    id integer NOT NULL,
    ruta_id integer NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    contacto_id integer NOT NULL,
    tipo_contacto character varying(20),
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: contactos_rutas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contactos_rutas_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contactos_rutas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contactos_rutas_id_seq OWNED BY public.contactos_rutas.id;


--
-- Name: country_challenge_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.country_challenge_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    code_hash character varying(64) NOT NULL,
    ip_address character varying(45),
    user_agent text,
    expires_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    attempts smallint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: dificultad_hielo_wi; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dificultad_hielo_wi (
    id character varying(10) NOT NULL,
    grado character varying(10) NOT NULL,
    descripcion text NOT NULL,
    rank smallint NOT NULL,
    CONSTRAINT dificultad_hielo_wi_rank_check CHECK ((rank >= 0))
);


--
-- Name: dificultad_roca_uiaa_francesa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dificultad_roca_uiaa_francesa (
    id character varying(15) NOT NULL,
    uiaa character varying(10),
    francesa character varying(10),
    descripcion text NOT NULL,
    rank smallint NOT NULL,
    CONSTRAINT dificultad_roca_uiaa_francesa_rank_check CHECK ((rank >= 1))
);


--
-- Name: dificultad_senderismo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dificultad_senderismo (
    id character varying(10) NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion text,
    rank smallint NOT NULL
);


--
-- Name: dignidades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dignidades (
    id integer NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion text NOT NULL
);


--
-- Name: dignidades_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dignidades_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dignidades_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dignidades_id_seq OWNED BY public.dignidades.id;


--
-- Name: documentos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documentos (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    storage_provider character varying(20) NOT NULL,
    object_key character varying(500) NOT NULL,
    filename character varying(255) NOT NULL,
    content_type character varying(100) DEFAULT 'application/pdf'::character varying NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    checksum_md5 character varying(32)
);


--
-- Name: email_verification_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_verification_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id uuid,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    cedula character varying(20),
    correo character varying(255),
    telefono character varying(20),
    nombre character varying(100),
    apellido character varying(100),
    tipo_socio_nombre character varying(50),
    nivel_tecnico_nombre character varying(50)
);


--
-- Name: equipo_montana; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.equipo_montana (
    id integer NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion text NOT NULL
);


--
-- Name: equipo_montana_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.equipo_montana_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: equipo_montana_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.equipo_montana_id_seq OWNED BY public.equipo_montana.id;


--
-- Name: escala_alpina_ifas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.escala_alpina_ifas (
    id character varying(10) NOT NULL,
    grado character varying(10) NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion text NOT NULL,
    rank smallint NOT NULL,
    CONSTRAINT escala_alpina_ifas_rank_check CHECK ((rank >= 1))
);


--
-- Name: estado_acceso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.estado_acceso (
    id smallint NOT NULL,
    codigo character varying(30) NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion text NOT NULL
);


--
-- Name: estado_acceso_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.estado_acceso_id_seq
    AS smallint
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: estado_acceso_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.estado_acceso_id_seq OWNED BY public.estado_acceso.id;


--
-- Name: estado_cuotas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.estado_cuotas (
    id bigint NOT NULL,
    socio_id uuid NOT NULL,
    valor numeric(10,2) NOT NULL,
    fecha date NOT NULL,
    estado character varying(20) NOT NULL,
    registrado_por_id uuid,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT estado_cuotas_estado_check CHECK (((estado)::text = ANY ((ARRAY['PAGADO'::character varying, 'PENDIENTE'::character varying])::text[]))),
    CONSTRAINT estado_cuotas_valor_check CHECK ((valor > (0)::numeric))
);


--
-- Name: estado_cuotas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.estado_cuotas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: estado_cuotas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.estado_cuotas_id_seq OWNED BY public.estado_cuotas.id;


--
-- Name: estado_habilitacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.estado_habilitacion (
    id smallint NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion text NOT NULL
);


--
-- Name: estado_habilitacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.estado_habilitacion_id_seq
    AS smallint
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: estado_habilitacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.estado_habilitacion_id_seq OWNED BY public.estado_habilitacion.id;


--
-- Name: formato_salida; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.formato_salida (
    id character varying(10) NOT NULL,
    nombre character varying(60) NOT NULL,
    orden smallint NOT NULL
);


--
-- Name: informe_salida; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.informe_salida (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    salida_id uuid NOT NULL,
    condiciones_meteorologicas text,
    se_realizo boolean NOT NULL,
    hora_salida_club time without time zone,
    hora_llegada_montana time without time zone,
    hora_cumbre time without time zone,
    hora_inicio_descenso time without time zone,
    hora_llegada_autos time without time zone,
    hora_regreso_club time without time zone,
    cronica text,
    observaciones text,
    comentarios_varios text,
    validado_por_id uuid,
    validado_en timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    documento_id uuid,
    alquilo_guia boolean NOT NULL,
    costo_guia numeric(10,2),
    costo_total numeric(10,2),
    logro_cumbre boolean DEFAULT false NOT NULL,
    contacto_guia_id integer,
    alquilo_refugio boolean DEFAULT false NOT NULL,
    nombre_refugio character varying(200),
    costo_refugio numeric(8,2),
    contacto_refugio_id integer,
    acampo boolean DEFAULT false NOT NULL,
    nombre_camping character varying(200),
    costo_camping numeric(8,2),
    contacto_camping_id integer,
    donde_autos character varying(30),
    autos_descripcion character varying(300),
    autos_link_ubicacion character varying(500),
    costo_parqueadero numeric(8,2),
    costo_por_persona numeric(10,2),
    CONSTRAINT chk_validacion CHECK ((((validado_por_id IS NULL) AND (validado_en IS NULL)) OR ((validado_por_id IS NOT NULL) AND (validado_en IS NOT NULL))))
);


--
-- Name: informe_salida_reconocimientos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.informe_salida_reconocimientos (
    id bigint NOT NULL,
    informe_id uuid NOT NULL,
    socio_id uuid NOT NULL,
    tipo public.tipo_reconocimiento NOT NULL,
    motivo text NOT NULL,
    registrado_por_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: informe_salida_reconocimientos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.informe_salida_reconocimientos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: informe_salida_reconocimientos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.informe_salida_reconocimientos_id_seq OWNED BY public.informe_salida_reconocimientos.id;


--
-- Name: mfa_challenge_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mfa_challenge_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    ip_address character varying(45),
    user_agent character varying(500),
    expires_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    attempts smallint DEFAULT 0 NOT NULL
);


--
-- Name: mountains; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mountains (
    id integer NOT NULL,
    nombre character varying(100) NOT NULL,
    region character varying(100) NOT NULL,
    altitud integer NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    pais character varying(100) DEFAULT 'Ecuador'::character varying NOT NULL,
    CONSTRAINT mountains_altitud_check CHECK ((altitud > 0))
);


--
-- Name: mountains_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mountains_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mountains_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mountains_id_seq OWNED BY public.mountains.id;


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id uuid NOT NULL,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: publico_objetivo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.publico_objetivo (
    id character varying(10) NOT NULL,
    nombre character varying(50) NOT NULL,
    orden smallint NOT NULL
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id uuid NOT NULL,
    token_hash character varying(255) NOT NULL,
    user_agent text,
    ip_address character varying(45),
    expires_at timestamp without time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL,
    revoked_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    platform character varying(10) DEFAULT 'WEB'::character varying NOT NULL,
    last_used_at timestamp with time zone,
    device_id character varying(32)
);


--
-- Name: roles_sistema; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles_sistema (
    id smallint NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion text NOT NULL
);


--
-- Name: roles_sistema_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_sistema_id_seq
    AS smallint
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_sistema_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_sistema_id_seq OWNED BY public.roles_sistema.id;


--
-- Name: ruta_documentos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ruta_documentos (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    ruta_id integer NOT NULL,
    documento_id uuid NOT NULL,
    subido_por_id uuid,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: rutas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rutas (
    id integer NOT NULL,
    nombre character varying(200) NOT NULL,
    mountain_id integer,
    sector_zona character varying(200),
    longitud_km numeric(6,2),
    desnivel_m integer,
    duracion_dias smallint,
    peligros_notas text,
    requiere_permisos boolean DEFAULT false NOT NULL,
    documentacion_url text,
    aprobada boolean DEFAULT false NOT NULL,
    aprobada_por_id uuid,
    aprobada_en timestamp without time zone,
    propuesta_por_id uuid,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    track_url text,
    tipo_actividad character varying(20) NOT NULL,
    lugar_referencia character varying(200),
    nivel_minimo_socio_id character varying(10),
    duracion_horas smallint,
    CONSTRAINT chk_aprobacion CHECK (((aprobada = false) OR ((aprobada = true) AND (aprobada_por_id IS NOT NULL) AND (aprobada_en IS NOT NULL)))),
    CONSTRAINT chk_tipo_actividad CHECK (((tipo_actividad)::text = ANY ((ARRAY['ALPINISMO'::character varying, 'ESCALADA'::character varying, 'TREKKING'::character varying, 'CICLISMO'::character varying])::text[]))),
    CONSTRAINT rutas_desnivel_m_check CHECK ((desnivel_m > 0)),
    CONSTRAINT rutas_duracion_dias_check CHECK ((duracion_dias > 0)),
    CONSTRAINT rutas_duracion_horas_check CHECK ((duracion_horas > 0)),
    CONSTRAINT rutas_longitud_km_check CHECK ((longitud_km > (0)::numeric))
);


--
-- Name: rutas_alpinismo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rutas_alpinismo (
    ruta_id integer NOT NULL,
    escala_alpina_ifas_id character varying(10) NOT NULL,
    dificultad_roca_id character varying(15) NOT NULL,
    dificultad_hielo_id character varying(10) NOT NULL,
    compromiso_id character varying(10) NOT NULL,
    yosemite_id character varying(10) NOT NULL,
    sadday_nivel_tecnico_id character varying(10) NOT NULL,
    sadday_nivel_fisico_id character varying(10) NOT NULL,
    equipo_montana_id integer
);


--
-- Name: rutas_ciclismo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rutas_ciclismo (
    ruta_id integer NOT NULL,
    tipo_bicicleta character varying(25) NOT NULL,
    dificultad_tecnica character varying(5),
    superficie_predominante character varying(200),
    ciclabilidad_pct numeric(5,2),
    CONSTRAINT rutas_ciclismo_ciclabilidad_pct_check CHECK (((ciclabilidad_pct >= (0)::numeric) AND (ciclabilidad_pct <= (100)::numeric))),
    CONSTRAINT rutas_ciclismo_dificultad_tecnica_check CHECK (((dificultad_tecnica)::text = ANY ((ARRAY['S0'::character varying, 'S1'::character varying, 'S2'::character varying, 'S3'::character varying, 'S4'::character varying])::text[]))),
    CONSTRAINT rutas_ciclismo_tipo_bicicleta_check CHECK (((tipo_bicicleta)::text = ANY ((ARRAY['RIGIDA'::character varying, 'DOBLE_SUSPENSION'::character varying, 'ENDURO'::character varying, 'GRAVEL'::character varying, 'RUTA'::character varying])::text[])))
);


--
-- Name: rutas_escalada; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rutas_escalada (
    ruta_id integer NOT NULL,
    dificultad_roca_id character varying(15) NOT NULL,
    tipo_escalada character varying(20) NOT NULL,
    num_cintas smallint,
    altura_via_m integer,
    tipo_roca character varying(100),
    CONSTRAINT rutas_escalada_altura_via_m_check CHECK ((altura_via_m > 0)),
    CONSTRAINT rutas_escalada_tipo_escalada_check CHECK (((tipo_escalada)::text = ANY ((ARRAY['DEPORTIVA'::character varying, 'TRADICIONAL'::character varying, 'MIXTA'::character varying, 'BOULDER'::character varying])::text[])))
);


--
-- Name: rutas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rutas_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rutas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rutas_id_seq OWNED BY public.rutas.id;


--
-- Name: rutas_trekking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rutas_trekking (
    ruta_id integer NOT NULL,
    dificultad_id character varying(10) NOT NULL,
    es_circular boolean DEFAULT false NOT NULL,
    fuentes_agua boolean DEFAULT false NOT NULL,
    tipo_terreno character varying(200)
);


--
-- Name: sadday_riesgo_exigencia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sadday_riesgo_exigencia (
    id character varying(10) NOT NULL,
    valor smallint NOT NULL,
    escala character varying(20) NOT NULL,
    descripcion text NOT NULL,
    rank smallint NOT NULL,
    CONSTRAINT sadday_riesgo_exigencia_rank_check CHECK ((rank >= 1)),
    CONSTRAINT sadday_riesgo_exigencia_valor_check CHECK (((valor >= 1) AND (valor <= 5)))
);


--
-- Name: salida; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.salida (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(200) NOT NULL,
    fecha_inicio date NOT NULL,
    hora_encuentro_club time without time zone NOT NULL,
    fecha_fin date NOT NULL,
    hora_estimada_regreso_club time without time zone,
    ruta_id integer,
    nivel_minimo_requerido_id character varying(10),
    capacidad_maxima smallint,
    estado public.estado_salida DEFAULT 'PLANIFICADA'::public.estado_salida NOT NULL,
    creado_por_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    inscripciones_cerradas boolean DEFAULT false NOT NULL,
    tipo_actividad character varying(20),
    publico_objetivo_id character varying(10),
    formato_salida_id character varying(10),
    eliminada boolean DEFAULT false NOT NULL,
    eliminada_en timestamp with time zone,
    eliminada_por_id uuid,
    motivo_eliminacion text,
    motivo_cancelacion text,
    cancelada_por_id uuid,
    cancelada_en timestamp with time zone,
    jefe_abandono_nombre character varying(200),
    CONSTRAINT chk_fechas_salida CHECK ((fecha_fin >= fecha_inicio)),
    CONSTRAINT salida_capacidad_maxima_check CHECK ((capacidad_maxima > 0)),
    CONSTRAINT salida_tipo_actividad_check CHECK (((tipo_actividad)::text = ANY ((ARRAY['ALPINISMO'::character varying, 'ESCALADA'::character varying, 'TREKKING'::character varying, 'CICLISMO'::character varying])::text[])))
);


--
-- Name: salida_participante_dignidades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.salida_participante_dignidades (
    id bigint NOT NULL,
    participante_id bigint NOT NULL,
    dignidad_id integer NOT NULL
);


--
-- Name: salida_participante_dignidades_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.salida_participante_dignidades_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salida_participante_dignidades_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.salida_participante_dignidades_id_seq OWNED BY public.salida_participante_dignidades.id;


--
-- Name: salida_participantes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.salida_participantes (
    id bigint NOT NULL,
    salida_id uuid NOT NULL,
    socio_id uuid NOT NULL,
    estado_inscripcion public.estado_inscripcion DEFAULT 'INSCRITO'::public.estado_inscripcion NOT NULL,
    riesgo_aprobado_por_directivo uuid,
    riesgo_aprobado_por_jefe uuid,
    riesgo_aprobado_en timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    motivo_directivo text,
    motivo_jefe text
);


--
-- Name: salida_participantes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.salida_participantes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salida_participantes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.salida_participantes_id_seq OWNED BY public.salida_participantes.id;


--
-- Name: security_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id uuid,
    username character varying(100),
    event_type character varying(50) NOT NULL,
    ip_address character varying(45),
    country_code character varying(2),
    city character varying(100),
    user_agent text,
    device_id character varying(32),
    session_id uuid,
    metadata jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: segmentos_viaje; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.segmentos_viaje (
    id bigint NOT NULL,
    informe_salida_id uuid NOT NULL,
    orden smallint NOT NULL,
    origen character varying(200) DEFAULT 'Club Sadday'::character varying NOT NULL,
    destino character varying(200) NOT NULL,
    alquilo_transporte boolean DEFAULT false NOT NULL,
    tipo_transporte character varying(20),
    costo_individual numeric(8,2),
    contacto_id integer,
    CONSTRAINT chk_tipo_transporte CHECK (((tipo_transporte IS NULL) OR ((tipo_transporte)::text = ANY ((ARRAY['CAMIONETA'::character varying, 'FURGONETA'::character varying, 'BUS_MEDIANO'::character varying, 'BUS_GRANDE'::character varying])::text[]))))
);


--
-- Name: segmentos_viaje_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.segmentos_viaje_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: segmentos_viaje_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.segmentos_viaje_id_seq OWNED BY public.segmentos_viaje.id;


--
-- Name: sistema_clases_yosemite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sistema_clases_yosemite (
    id character varying(10) NOT NULL,
    tipo character varying(20) NOT NULL,
    descripcion text NOT NULL,
    rank smallint NOT NULL,
    CONSTRAINT sistema_clases_yosemite_rank_check CHECK ((rank >= 0))
);


--
-- Name: socio_habilitacion_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.socio_habilitacion_log (
    id bigint NOT NULL,
    socio_id uuid NOT NULL,
    estado_anterior_id smallint NOT NULL,
    estado_nuevo_id smallint NOT NULL,
    cambiado_por_id uuid NOT NULL,
    cambiado_en timestamp with time zone DEFAULT now() NOT NULL,
    fuente character varying(10) DEFAULT 'MANUAL'::character varying NOT NULL,
    csv_key text,
    notas text
);


--
-- Name: socio_habilitacion_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.socio_habilitacion_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: socio_habilitacion_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.socio_habilitacion_log_id_seq OWNED BY public.socio_habilitacion_log.id;


--
-- Name: socios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.socios (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(100) NOT NULL,
    apellido character varying(100) NOT NULL,
    cedula character varying(20) NOT NULL,
    correo character varying(255) NOT NULL,
    telefono character varying(20),
    direccion text,
    fecha_nacimiento date NOT NULL,
    fecha_ingreso date DEFAULT CURRENT_DATE NOT NULL,
    fecha_salida date,
    tipo_sangre character varying(5),
    emergency_contact_name character varying(200),
    emergency_contact_phone character varying(20),
    emergency_contact_direccion text,
    emergency_contact_name2 character varying(200),
    emergency_contact_phone2 character varying(20),
    emergency_contact_direccion2 text,
    estado_habilitacion_id smallint NOT NULL,
    tipo_socio_id smallint NOT NULL,
    nivel_tecnico_id character varying(10),
    rol_sistema_id smallint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    estado_acceso_id smallint NOT NULL,
    es_jefe_montana boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_fecha_salida CHECK (((fecha_salida IS NULL) OR (fecha_salida >= fecha_ingreso)))
);


--
-- Name: tipo_socio_club; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipo_socio_club (
    id smallint NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion text NOT NULL
);


--
-- Name: tipo_socio_club_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tipo_socio_club_id_seq
    AS smallint
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_socio_club_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tipo_socio_club_id_seq OWNED BY public.tipo_socio_club.id;


--
-- Name: usuarios_auth; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuarios_auth (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id uuid NOT NULL,
    username character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    totp_secret text,
    totp_enabled boolean DEFAULT false NOT NULL,
    failed_attempts smallint DEFAULT 0 NOT NULL,
    login_blocked boolean DEFAULT false NOT NULL,
    blocked_until timestamp without time zone,
    last_login timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    password_must_change boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_totp CHECK (((totp_enabled = false) OR ((totp_enabled = true) AND (totp_secret IS NOT NULL)))),
    CONSTRAINT usuarios_auth_failed_attempts_check CHECK ((failed_attempts >= 0))
);


--
-- Name: acceso_ruta_por_nivel id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel ALTER COLUMN id SET DEFAULT nextval('public.acceso_ruta_por_nivel_id_seq'::regclass);


--
-- Name: acta_informes_salida id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_informes_salida ALTER COLUMN id SET DEFAULT nextval('public.acta_informes_salida_id_seq'::regclass);


--
-- Name: asistentes_reunion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistentes_reunion ALTER COLUMN id SET DEFAULT nextval('public.asistentes_reunion_id_seq'::regclass);


--
-- Name: auditoria id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ALTER COLUMN id SET DEFAULT nextval('public.auditoria_id_seq'::regclass);


--
-- Name: configuracion_sistema id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_sistema ALTER COLUMN id SET DEFAULT nextval('public.configuracion_sistema_id_seq'::regclass);


--
-- Name: contactos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos ALTER COLUMN id SET DEFAULT nextval('public.contactos_id_seq'::regclass);


--
-- Name: contactos_rutas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos_rutas ALTER COLUMN id SET DEFAULT nextval('public.contactos_rutas_id_seq'::regclass);


--
-- Name: dignidades id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dignidades ALTER COLUMN id SET DEFAULT nextval('public.dignidades_id_seq'::regclass);


--
-- Name: equipo_montana id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipo_montana ALTER COLUMN id SET DEFAULT nextval('public.equipo_montana_id_seq'::regclass);


--
-- Name: estado_acceso id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_acceso ALTER COLUMN id SET DEFAULT nextval('public.estado_acceso_id_seq'::regclass);


--
-- Name: estado_cuotas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_cuotas ALTER COLUMN id SET DEFAULT nextval('public.estado_cuotas_id_seq'::regclass);


--
-- Name: estado_habilitacion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_habilitacion ALTER COLUMN id SET DEFAULT nextval('public.estado_habilitacion_id_seq'::regclass);


--
-- Name: informe_salida_reconocimientos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida_reconocimientos ALTER COLUMN id SET DEFAULT nextval('public.informe_salida_reconocimientos_id_seq'::regclass);


--
-- Name: mountains id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mountains ALTER COLUMN id SET DEFAULT nextval('public.mountains_id_seq'::regclass);


--
-- Name: roles_sistema id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles_sistema ALTER COLUMN id SET DEFAULT nextval('public.roles_sistema_id_seq'::regclass);


--
-- Name: rutas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas ALTER COLUMN id SET DEFAULT nextval('public.rutas_id_seq'::regclass);


--
-- Name: salida_participante_dignidades id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participante_dignidades ALTER COLUMN id SET DEFAULT nextval('public.salida_participante_dignidades_id_seq'::regclass);


--
-- Name: salida_participantes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participantes ALTER COLUMN id SET DEFAULT nextval('public.salida_participantes_id_seq'::regclass);


--
-- Name: segmentos_viaje id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.segmentos_viaje ALTER COLUMN id SET DEFAULT nextval('public.segmentos_viaje_id_seq'::regclass);


--
-- Name: socio_habilitacion_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socio_habilitacion_log ALTER COLUMN id SET DEFAULT nextval('public.socio_habilitacion_log_id_seq'::regclass);


--
-- Name: tipo_socio_club id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_socio_club ALTER COLUMN id SET DEFAULT nextval('public.tipo_socio_club_id_seq'::regclass);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_nivel_socio_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_nivel_socio_id_key UNIQUE (nivel_socio_id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_pkey PRIMARY KEY (id);


--
-- Name: acta_informes_salida acta_informes_salida_acta_id_informe_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_informes_salida
    ADD CONSTRAINT acta_informes_salida_acta_id_informe_id_key UNIQUE (acta_id, informe_id);


--
-- Name: acta_informes_salida acta_informes_salida_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_informes_salida
    ADD CONSTRAINT acta_informes_salida_pkey PRIMARY KEY (id);


--
-- Name: actas_reunion actas_reunion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas_reunion
    ADD CONSTRAINT actas_reunion_pkey PRIMARY KEY (id);


--
-- Name: asistentes_reunion asistentes_reunion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistentes_reunion
    ADD CONSTRAINT asistentes_reunion_pkey PRIMARY KEY (id);


--
-- Name: auditoria auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_pkey PRIMARY KEY (id);


--
-- Name: clasificacion_socio clasificacion_socio_nivel_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clasificacion_socio
    ADD CONSTRAINT clasificacion_socio_nivel_key UNIQUE (nivel);


--
-- Name: clasificacion_socio clasificacion_socio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clasificacion_socio
    ADD CONSTRAINT clasificacion_socio_pkey PRIMARY KEY (id);


--
-- Name: compromiso compromiso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compromiso
    ADD CONSTRAINT compromiso_pkey PRIMARY KEY (id);


--
-- Name: configuracion_sistema configuracion_sistema_clave_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_sistema
    ADD CONSTRAINT configuracion_sistema_clave_key UNIQUE (clave);


--
-- Name: configuracion_sistema configuracion_sistema_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_sistema
    ADD CONSTRAINT configuracion_sistema_pkey PRIMARY KEY (id);


--
-- Name: contactos contactos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos
    ADD CONSTRAINT contactos_pkey PRIMARY KEY (id);


--
-- Name: contactos_rutas contactos_rutas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos_rutas
    ADD CONSTRAINT contactos_rutas_pkey PRIMARY KEY (id);


--
-- Name: contactos contactos_telefono_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos
    ADD CONSTRAINT contactos_telefono_key UNIQUE (telefono);


--
-- Name: country_challenge_tokens country_challenge_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.country_challenge_tokens
    ADD CONSTRAINT country_challenge_tokens_pkey PRIMARY KEY (id);


--
-- Name: country_challenge_tokens country_challenge_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.country_challenge_tokens
    ADD CONSTRAINT country_challenge_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: dificultad_hielo_wi dificultad_hielo_wi_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dificultad_hielo_wi
    ADD CONSTRAINT dificultad_hielo_wi_pkey PRIMARY KEY (id);


--
-- Name: dificultad_roca_uiaa_francesa dificultad_roca_uiaa_francesa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dificultad_roca_uiaa_francesa
    ADD CONSTRAINT dificultad_roca_uiaa_francesa_pkey PRIMARY KEY (id);


--
-- Name: dificultad_senderismo dificultad_senderismo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dificultad_senderismo
    ADD CONSTRAINT dificultad_senderismo_pkey PRIMARY KEY (id);


--
-- Name: dignidades dignidades_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dignidades
    ADD CONSTRAINT dignidades_nombre_key UNIQUE (nombre);


--
-- Name: dignidades dignidades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dignidades
    ADD CONSTRAINT dignidades_pkey PRIMARY KEY (id);


--
-- Name: documentos documentos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documentos
    ADD CONSTRAINT documentos_pkey PRIMARY KEY (id);


--
-- Name: email_verification_tokens email_verification_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_pkey PRIMARY KEY (id);


--
-- Name: email_verification_tokens email_verification_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: equipo_montana equipo_montana_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipo_montana
    ADD CONSTRAINT equipo_montana_nombre_key UNIQUE (nombre);


--
-- Name: equipo_montana equipo_montana_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.equipo_montana
    ADD CONSTRAINT equipo_montana_pkey PRIMARY KEY (id);


--
-- Name: escala_alpina_ifas escala_alpina_ifas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.escala_alpina_ifas
    ADD CONSTRAINT escala_alpina_ifas_pkey PRIMARY KEY (id);


--
-- Name: estado_acceso estado_acceso_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_acceso
    ADD CONSTRAINT estado_acceso_codigo_key UNIQUE (codigo);


--
-- Name: estado_acceso estado_acceso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_acceso
    ADD CONSTRAINT estado_acceso_pkey PRIMARY KEY (id);


--
-- Name: estado_cuotas estado_cuotas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_cuotas
    ADD CONSTRAINT estado_cuotas_pkey PRIMARY KEY (id);


--
-- Name: estado_habilitacion estado_habilitacion_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_habilitacion
    ADD CONSTRAINT estado_habilitacion_nombre_key UNIQUE (nombre);


--
-- Name: estado_habilitacion estado_habilitacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_habilitacion
    ADD CONSTRAINT estado_habilitacion_pkey PRIMARY KEY (id);


--
-- Name: formato_salida formato_salida_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formato_salida
    ADD CONSTRAINT formato_salida_pkey PRIMARY KEY (id);


--
-- Name: informe_salida informe_salida_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_pkey PRIMARY KEY (id);


--
-- Name: informe_salida_reconocimientos informe_salida_reconocimientos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida_reconocimientos
    ADD CONSTRAINT informe_salida_reconocimientos_pkey PRIMARY KEY (id);


--
-- Name: informe_salida informe_salida_salida_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_salida_id_key UNIQUE (salida_id);


--
-- Name: mfa_challenge_tokens mfa_challenge_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mfa_challenge_tokens
    ADD CONSTRAINT mfa_challenge_tokens_pkey PRIMARY KEY (id);


--
-- Name: mfa_challenge_tokens mfa_challenge_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mfa_challenge_tokens
    ADD CONSTRAINT mfa_challenge_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: mountains mountains_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mountains
    ADD CONSTRAINT mountains_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: publico_objetivo publico_objetivo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.publico_objetivo
    ADD CONSTRAINT publico_objetivo_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: roles_sistema roles_sistema_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles_sistema
    ADD CONSTRAINT roles_sistema_nombre_key UNIQUE (nombre);


--
-- Name: roles_sistema roles_sistema_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles_sistema
    ADD CONSTRAINT roles_sistema_pkey PRIMARY KEY (id);


--
-- Name: ruta_documentos ruta_documentos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_documentos
    ADD CONSTRAINT ruta_documentos_pkey PRIMARY KEY (id);


--
-- Name: rutas_alpinismo rutas_alpinismo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_pkey PRIMARY KEY (ruta_id);


--
-- Name: rutas_ciclismo rutas_ciclismo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_ciclismo
    ADD CONSTRAINT rutas_ciclismo_pkey PRIMARY KEY (ruta_id);


--
-- Name: rutas_escalada rutas_escalada_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_escalada
    ADD CONSTRAINT rutas_escalada_pkey PRIMARY KEY (ruta_id);


--
-- Name: rutas rutas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas
    ADD CONSTRAINT rutas_pkey PRIMARY KEY (id);


--
-- Name: rutas_trekking rutas_trekking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_trekking
    ADD CONSTRAINT rutas_trekking_pkey PRIMARY KEY (ruta_id);


--
-- Name: sadday_riesgo_exigencia sadday_riesgo_exigencia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sadday_riesgo_exigencia
    ADD CONSTRAINT sadday_riesgo_exigencia_pkey PRIMARY KEY (id);


--
-- Name: salida_participante_dignidades salida_participante_dignidades_participante_id_dignidad_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participante_dignidades
    ADD CONSTRAINT salida_participante_dignidades_participante_id_dignidad_id_key UNIQUE (participante_id, dignidad_id);


--
-- Name: salida_participante_dignidades salida_participante_dignidades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participante_dignidades
    ADD CONSTRAINT salida_participante_dignidades_pkey PRIMARY KEY (id);


--
-- Name: salida_participantes salida_participantes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participantes
    ADD CONSTRAINT salida_participantes_pkey PRIMARY KEY (id);


--
-- Name: salida_participantes salida_participantes_salida_id_socio_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participantes
    ADD CONSTRAINT salida_participantes_salida_id_socio_id_key UNIQUE (salida_id, socio_id);


--
-- Name: salida salida_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_pkey PRIMARY KEY (id);


--
-- Name: security_events security_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT security_events_pkey PRIMARY KEY (id);


--
-- Name: segmentos_viaje segmentos_viaje_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.segmentos_viaje
    ADD CONSTRAINT segmentos_viaje_pkey PRIMARY KEY (id);


--
-- Name: sistema_clases_yosemite sistema_clases_yosemite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sistema_clases_yosemite
    ADD CONSTRAINT sistema_clases_yosemite_pkey PRIMARY KEY (id);


--
-- Name: socio_habilitacion_log socio_habilitacion_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socio_habilitacion_log
    ADD CONSTRAINT socio_habilitacion_log_pkey PRIMARY KEY (id);


--
-- Name: socios socios_cedula_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_cedula_key UNIQUE (cedula);


--
-- Name: socios socios_correo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_correo_key UNIQUE (correo);


--
-- Name: socios socios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_pkey PRIMARY KEY (id);


--
-- Name: tipo_socio_club tipo_socio_club_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_socio_club
    ADD CONSTRAINT tipo_socio_club_nombre_key UNIQUE (nombre);


--
-- Name: tipo_socio_club tipo_socio_club_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_socio_club
    ADD CONSTRAINT tipo_socio_club_pkey PRIMARY KEY (id);


--
-- Name: contactos_rutas uq_contacto_ruta_tipo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos_rutas
    ADD CONSTRAINT uq_contacto_ruta_tipo UNIQUE (contacto_id, ruta_id, tipo_contacto);


--
-- Name: ruta_documentos uq_ruta_documento; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_documentos
    ADD CONSTRAINT uq_ruta_documento UNIQUE (ruta_id, documento_id);


--
-- Name: segmentos_viaje uq_segmento_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.segmentos_viaje
    ADD CONSTRAINT uq_segmento_orden UNIQUE (informe_salida_id, orden);


--
-- Name: usuarios_auth usuarios_auth_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_auth
    ADD CONSTRAINT usuarios_auth_pkey PRIMARY KEY (id);


--
-- Name: usuarios_auth usuarios_auth_socio_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_auth
    ADD CONSTRAINT usuarios_auth_socio_id_key UNIQUE (socio_id);


--
-- Name: usuarios_auth usuarios_auth_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_auth
    ADD CONSTRAINT usuarios_auth_username_key UNIQUE (username);


--
-- Name: idx_acta_informes_acta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_acta_informes_acta ON public.acta_informes_salida USING btree (acta_id);


--
-- Name: idx_acta_informes_informe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_acta_informes_informe ON public.acta_informes_salida USING btree (informe_id);


--
-- Name: idx_actas_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_actas_fecha ON public.actas_reunion USING btree (fecha);


--
-- Name: idx_actas_presidente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_actas_presidente ON public.actas_reunion USING btree (presidente_reunion_id);


--
-- Name: idx_actas_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_actas_search ON public.actas_reunion USING gin (search_vector);


--
-- Name: idx_actas_secretaria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_actas_secretaria ON public.actas_reunion USING btree (secretaria_reunion_id);


--
-- Name: idx_asistentes_acta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asistentes_acta ON public.asistentes_reunion USING btree (acta_id);


--
-- Name: idx_asistentes_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asistentes_socio ON public.asistentes_reunion USING btree (socio_id);


--
-- Name: idx_auditoria_accion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_accion ON public.auditoria USING btree (accion);


--
-- Name: idx_auditoria_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_actor ON public.auditoria USING btree (actor_username);


--
-- Name: idx_auditoria_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_entidad ON public.auditoria USING btree (entidad_afectada, entidad_id);


--
-- Name: idx_auditoria_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_fecha ON public.auditoria USING btree (created_at);


--
-- Name: idx_contactos_nombre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contactos_nombre ON public.contactos USING btree (lower((nombre)::text));


--
-- Name: idx_contactos_ruta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contactos_ruta ON public.contactos_rutas USING btree (ruta_id);


--
-- Name: idx_contactos_telefono; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contactos_telefono ON public.contactos USING btree (telefono);


--
-- Name: idx_country_challenge_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_country_challenge_expires ON public.country_challenge_tokens USING btree (expires_at);


--
-- Name: idx_country_challenge_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_country_challenge_token_hash ON public.country_challenge_tokens USING btree (token_hash);


--
-- Name: idx_cuotas_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cuotas_estado ON public.estado_cuotas USING btree (estado);


--
-- Name: idx_cuotas_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cuotas_socio ON public.estado_cuotas USING btree (socio_id);


--
-- Name: idx_dignidades_dignidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dignidades_dignidad ON public.salida_participante_dignidades USING btree (dignidad_id);


--
-- Name: idx_dignidades_participante; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dignidades_participante ON public.salida_participante_dignidades USING btree (participante_id);


--
-- Name: idx_email_verify_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_verify_socio ON public.email_verification_tokens USING btree (socio_id);


--
-- Name: idx_hab_log_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hab_log_fecha ON public.socio_habilitacion_log USING btree (cambiado_en DESC);


--
-- Name: idx_hab_log_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hab_log_socio ON public.socio_habilitacion_log USING btree (socio_id);


--
-- Name: idx_informe_salida_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_informe_salida_id ON public.informe_salida USING btree (salida_id);


--
-- Name: idx_mfa_challenge_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mfa_challenge_expires ON public.mfa_challenge_tokens USING btree (expires_at) WHERE (used = false);


--
-- Name: idx_participantes_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_participantes_estado ON public.salida_participantes USING btree (estado_inscripcion);


--
-- Name: idx_participantes_salida; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_participantes_salida ON public.salida_participantes USING btree (salida_id);


--
-- Name: idx_participantes_salida_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_participantes_salida_estado ON public.salida_participantes USING btree (salida_id, estado_inscripcion);


--
-- Name: idx_participantes_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_participantes_socio ON public.salida_participantes USING btree (socio_id);


--
-- Name: idx_pwd_reset_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pwd_reset_socio ON public.password_reset_tokens USING btree (socio_id);


--
-- Name: idx_reconocimientos_informe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reconocimientos_informe ON public.informe_salida_reconocimientos USING btree (informe_id);


--
-- Name: idx_reconocimientos_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reconocimientos_socio ON public.informe_salida_reconocimientos USING btree (socio_id);


--
-- Name: idx_reconocimientos_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reconocimientos_tipo ON public.informe_salida_reconocimientos USING btree (tipo);


--
-- Name: idx_refresh_tokens_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_hash ON public.refresh_tokens USING btree (token_hash);


--
-- Name: idx_refresh_tokens_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_socio ON public.refresh_tokens USING btree (socio_id);


--
-- Name: idx_refresh_tokens_socio_platform; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_socio_platform ON public.refresh_tokens USING btree (socio_id, platform) WHERE (revoked = false);


--
-- Name: idx_ruta_documentos_ruta_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ruta_documentos_ruta_id ON public.ruta_documentos USING btree (ruta_id);


--
-- Name: idx_rutas_aprobada; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rutas_aprobada ON public.rutas USING btree (aprobada);


--
-- Name: idx_rutas_mountain; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rutas_mountain ON public.rutas USING btree (mountain_id);


--
-- Name: idx_salida_eliminada; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_salida_eliminada ON public.salida USING btree (eliminada);


--
-- Name: idx_salida_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_salida_estado ON public.salida USING btree (estado);


--
-- Name: idx_salida_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_salida_fecha ON public.salida USING btree (fecha_inicio);


--
-- Name: idx_salida_ruta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_salida_ruta ON public.salida USING btree (ruta_id);


--
-- Name: idx_security_events_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_events_event_type ON public.security_events USING btree (event_type, created_at DESC);


--
-- Name: idx_security_events_ip; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_events_ip ON public.security_events USING btree (ip_address, created_at DESC);


--
-- Name: idx_security_events_socio_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_events_socio_id ON public.security_events USING btree (socio_id, created_at DESC);


--
-- Name: idx_segmentos_informe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_segmentos_informe ON public.segmentos_viaje USING btree (informe_salida_id);


--
-- Name: idx_socios_cedula; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_socios_cedula ON public.socios USING btree (cedula);


--
-- Name: idx_socios_correo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_socios_correo ON public.socios USING btree (correo);


--
-- Name: idx_socios_estado_acceso; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_socios_estado_acceso ON public.socios USING btree (estado_acceso_id);


--
-- Name: idx_socios_fecha_nac; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_socios_fecha_nac ON public.socios USING btree (fecha_nacimiento);


--
-- Name: idx_socios_habilitacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_socios_habilitacion ON public.socios USING btree (estado_habilitacion_id);


--
-- Name: idx_socios_rol; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_socios_rol ON public.socios USING btree (rol_sistema_id);


--
-- Name: idx_socios_tipo_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_socios_tipo_socio ON public.socios USING btree (tipo_socio_id);


--
-- Name: idx_usuarios_auth_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuarios_auth_username ON public.usuarios_auth USING btree (username);


--
-- Name: uq_asistentes_acta_nombre_raw; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asistentes_acta_nombre_raw ON public.asistentes_reunion USING btree (acta_id, nombre_raw) WHERE ((nombre_raw IS NOT NULL) AND (socio_id IS NULL));


--
-- Name: uq_asistentes_acta_socio; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asistentes_acta_socio ON public.asistentes_reunion USING btree (acta_id, socio_id) WHERE (socio_id IS NOT NULL);


--
-- Name: actas_reunion trg_actas_search_vector; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_actas_search_vector BEFORE INSERT OR UPDATE ON public.actas_reunion FOR EACH ROW EXECUTE FUNCTION public.fn_actas_update_search_vector();


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_max_compromiso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_max_compromiso_id_fkey FOREIGN KEY (max_compromiso_id) REFERENCES public.compromiso(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_max_hielo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_max_hielo_id_fkey FOREIGN KEY (max_hielo_id) REFERENCES public.dificultad_hielo_wi(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_max_ifas_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_max_ifas_id_fkey FOREIGN KEY (max_ifas_id) REFERENCES public.escala_alpina_ifas(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_max_roca_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_max_roca_id_fkey FOREIGN KEY (max_roca_id) REFERENCES public.dificultad_roca_uiaa_francesa(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_max_sadday_fisico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_max_sadday_fisico_id_fkey FOREIGN KEY (max_sadday_fisico_id) REFERENCES public.sadday_riesgo_exigencia(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_max_sadday_tecnico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_max_sadday_tecnico_id_fkey FOREIGN KEY (max_sadday_tecnico_id) REFERENCES public.sadday_riesgo_exigencia(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_max_yosemite_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_max_yosemite_id_fkey FOREIGN KEY (max_yosemite_id) REFERENCES public.sistema_clases_yosemite(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_nivel_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_nivel_socio_id_fkey FOREIGN KEY (nivel_socio_id) REFERENCES public.clasificacion_socio(id);


--
-- Name: acceso_ruta_por_nivel acceso_ruta_por_nivel_updated_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_ruta_por_nivel
    ADD CONSTRAINT acceso_ruta_por_nivel_updated_by_id_fkey FOREIGN KEY (updated_by_id) REFERENCES public.socios(id);


--
-- Name: acta_informes_salida acta_informes_salida_acta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_informes_salida
    ADD CONSTRAINT acta_informes_salida_acta_id_fkey FOREIGN KEY (acta_id) REFERENCES public.actas_reunion(id) ON DELETE CASCADE;


--
-- Name: acta_informes_salida acta_informes_salida_informe_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_informes_salida
    ADD CONSTRAINT acta_informes_salida_informe_id_fkey FOREIGN KEY (informe_id) REFERENCES public.informe_salida(id);


--
-- Name: actas_reunion actas_reunion_creada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas_reunion
    ADD CONSTRAINT actas_reunion_creada_por_id_fkey FOREIGN KEY (creada_por_id) REFERENCES public.socios(id);


--
-- Name: actas_reunion actas_reunion_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas_reunion
    ADD CONSTRAINT actas_reunion_documento_id_fkey FOREIGN KEY (documento_id) REFERENCES public.documentos(id);


--
-- Name: actas_reunion actas_reunion_presidente_reunion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas_reunion
    ADD CONSTRAINT actas_reunion_presidente_reunion_id_fkey FOREIGN KEY (presidente_reunion_id) REFERENCES public.socios(id);


--
-- Name: actas_reunion actas_reunion_secretaria_reunion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas_reunion
    ADD CONSTRAINT actas_reunion_secretaria_reunion_id_fkey FOREIGN KEY (secretaria_reunion_id) REFERENCES public.socios(id);


--
-- Name: asistentes_reunion asistentes_reunion_acta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistentes_reunion
    ADD CONSTRAINT asistentes_reunion_acta_id_fkey FOREIGN KEY (acta_id) REFERENCES public.actas_reunion(id) ON DELETE CASCADE;


--
-- Name: asistentes_reunion asistentes_reunion_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistentes_reunion
    ADD CONSTRAINT asistentes_reunion_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id);


--
-- Name: configuracion_sistema configuracion_sistema_updated_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_sistema
    ADD CONSTRAINT configuracion_sistema_updated_by_id_fkey FOREIGN KEY (updated_by_id) REFERENCES public.socios(id) ON DELETE SET NULL;


--
-- Name: contactos_rutas contactos_rutas_contacto_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos_rutas
    ADD CONSTRAINT contactos_rutas_contacto_id_fkey FOREIGN KEY (contacto_id) REFERENCES public.contactos(id);


--
-- Name: contactos_rutas contactos_rutas_ruta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contactos_rutas
    ADD CONSTRAINT contactos_rutas_ruta_id_fkey FOREIGN KEY (ruta_id) REFERENCES public.rutas(id) ON DELETE CASCADE;


--
-- Name: email_verification_tokens email_verification_tokens_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE;


--
-- Name: estado_cuotas estado_cuotas_registrado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_cuotas
    ADD CONSTRAINT estado_cuotas_registrado_por_id_fkey FOREIGN KEY (registrado_por_id) REFERENCES public.socios(id);


--
-- Name: estado_cuotas estado_cuotas_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estado_cuotas
    ADD CONSTRAINT estado_cuotas_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE;


--
-- Name: informe_salida informe_salida_contacto_camping_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_contacto_camping_id_fkey FOREIGN KEY (contacto_camping_id) REFERENCES public.contactos(id);


--
-- Name: informe_salida informe_salida_contacto_guia_global_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_contacto_guia_global_id_fkey FOREIGN KEY (contacto_guia_id) REFERENCES public.contactos(id);


--
-- Name: informe_salida informe_salida_contacto_refugio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_contacto_refugio_id_fkey FOREIGN KEY (contacto_refugio_id) REFERENCES public.contactos(id);


--
-- Name: informe_salida informe_salida_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_documento_id_fkey FOREIGN KEY (documento_id) REFERENCES public.documentos(id);


--
-- Name: informe_salida_reconocimientos informe_salida_reconocimientos_informe_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida_reconocimientos
    ADD CONSTRAINT informe_salida_reconocimientos_informe_id_fkey FOREIGN KEY (informe_id) REFERENCES public.informe_salida(id) ON DELETE CASCADE;


--
-- Name: informe_salida_reconocimientos informe_salida_reconocimientos_registrado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida_reconocimientos
    ADD CONSTRAINT informe_salida_reconocimientos_registrado_por_id_fkey FOREIGN KEY (registrado_por_id) REFERENCES public.socios(id);


--
-- Name: informe_salida_reconocimientos informe_salida_reconocimientos_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida_reconocimientos
    ADD CONSTRAINT informe_salida_reconocimientos_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id);


--
-- Name: informe_salida informe_salida_salida_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_salida_id_fkey FOREIGN KEY (salida_id) REFERENCES public.salida(id);


--
-- Name: informe_salida informe_salida_validado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informe_salida
    ADD CONSTRAINT informe_salida_validado_por_id_fkey FOREIGN KEY (validado_por_id) REFERENCES public.socios(id);


--
-- Name: mfa_challenge_tokens mfa_challenge_tokens_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mfa_challenge_tokens
    ADD CONSTRAINT mfa_challenge_tokens_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE;


--
-- Name: password_reset_tokens password_reset_tokens_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE;


--
-- Name: refresh_tokens refresh_tokens_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE;


--
-- Name: ruta_documentos ruta_documentos_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_documentos
    ADD CONSTRAINT ruta_documentos_documento_id_fkey FOREIGN KEY (documento_id) REFERENCES public.documentos(id);


--
-- Name: ruta_documentos ruta_documentos_ruta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_documentos
    ADD CONSTRAINT ruta_documentos_ruta_id_fkey FOREIGN KEY (ruta_id) REFERENCES public.rutas(id) ON DELETE CASCADE;


--
-- Name: ruta_documentos ruta_documentos_subido_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ruta_documentos
    ADD CONSTRAINT ruta_documentos_subido_por_id_fkey FOREIGN KEY (subido_por_id) REFERENCES public.socios(id) ON DELETE SET NULL;


--
-- Name: rutas_alpinismo rutas_alpinismo_compromiso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_compromiso_id_fkey FOREIGN KEY (compromiso_id) REFERENCES public.compromiso(id);


--
-- Name: rutas_alpinismo rutas_alpinismo_dificultad_hielo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_dificultad_hielo_id_fkey FOREIGN KEY (dificultad_hielo_id) REFERENCES public.dificultad_hielo_wi(id);


--
-- Name: rutas_alpinismo rutas_alpinismo_dificultad_roca_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_dificultad_roca_id_fkey FOREIGN KEY (dificultad_roca_id) REFERENCES public.dificultad_roca_uiaa_francesa(id);


--
-- Name: rutas_alpinismo rutas_alpinismo_equipo_montana_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_equipo_montana_id_fkey FOREIGN KEY (equipo_montana_id) REFERENCES public.equipo_montana(id);


--
-- Name: rutas_alpinismo rutas_alpinismo_escala_alpina_ifas_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_escala_alpina_ifas_id_fkey FOREIGN KEY (escala_alpina_ifas_id) REFERENCES public.escala_alpina_ifas(id);


--
-- Name: rutas_alpinismo rutas_alpinismo_ruta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_ruta_id_fkey FOREIGN KEY (ruta_id) REFERENCES public.rutas(id) ON DELETE CASCADE;


--
-- Name: rutas_alpinismo rutas_alpinismo_sadday_nivel_fisico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_sadday_nivel_fisico_id_fkey FOREIGN KEY (sadday_nivel_fisico_id) REFERENCES public.sadday_riesgo_exigencia(id);


--
-- Name: rutas_alpinismo rutas_alpinismo_sadday_nivel_tecnico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_sadday_nivel_tecnico_id_fkey FOREIGN KEY (sadday_nivel_tecnico_id) REFERENCES public.sadday_riesgo_exigencia(id);


--
-- Name: rutas_alpinismo rutas_alpinismo_yosemite_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_alpinismo
    ADD CONSTRAINT rutas_alpinismo_yosemite_id_fkey FOREIGN KEY (yosemite_id) REFERENCES public.sistema_clases_yosemite(id);


--
-- Name: rutas rutas_aprobada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas
    ADD CONSTRAINT rutas_aprobada_por_id_fkey FOREIGN KEY (aprobada_por_id) REFERENCES public.socios(id);


--
-- Name: rutas_ciclismo rutas_ciclismo_ruta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_ciclismo
    ADD CONSTRAINT rutas_ciclismo_ruta_id_fkey FOREIGN KEY (ruta_id) REFERENCES public.rutas(id) ON DELETE CASCADE;


--
-- Name: rutas_escalada rutas_escalada_dificultad_roca_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_escalada
    ADD CONSTRAINT rutas_escalada_dificultad_roca_id_fkey FOREIGN KEY (dificultad_roca_id) REFERENCES public.dificultad_roca_uiaa_francesa(id);


--
-- Name: rutas_escalada rutas_escalada_ruta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_escalada
    ADD CONSTRAINT rutas_escalada_ruta_id_fkey FOREIGN KEY (ruta_id) REFERENCES public.rutas(id) ON DELETE CASCADE;


--
-- Name: rutas rutas_mountain_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas
    ADD CONSTRAINT rutas_mountain_id_fkey FOREIGN KEY (mountain_id) REFERENCES public.mountains(id);


--
-- Name: rutas rutas_nivel_minimo_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas
    ADD CONSTRAINT rutas_nivel_minimo_socio_id_fkey FOREIGN KEY (nivel_minimo_socio_id) REFERENCES public.clasificacion_socio(id);


--
-- Name: rutas rutas_propuesta_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas
    ADD CONSTRAINT rutas_propuesta_por_id_fkey FOREIGN KEY (propuesta_por_id) REFERENCES public.socios(id);


--
-- Name: rutas_trekking rutas_trekking_dificultad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_trekking
    ADD CONSTRAINT rutas_trekking_dificultad_id_fkey FOREIGN KEY (dificultad_id) REFERENCES public.dificultad_senderismo(id);


--
-- Name: rutas_trekking rutas_trekking_ruta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rutas_trekking
    ADD CONSTRAINT rutas_trekking_ruta_id_fkey FOREIGN KEY (ruta_id) REFERENCES public.rutas(id) ON DELETE CASCADE;


--
-- Name: salida salida_cancelada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_cancelada_por_id_fkey FOREIGN KEY (cancelada_por_id) REFERENCES public.socios(id);


--
-- Name: salida salida_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.socios(id);


--
-- Name: salida salida_eliminada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_eliminada_por_id_fkey FOREIGN KEY (eliminada_por_id) REFERENCES public.socios(id);


--
-- Name: salida salida_formato_salida_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_formato_salida_id_fkey FOREIGN KEY (formato_salida_id) REFERENCES public.formato_salida(id);


--
-- Name: salida salida_nivel_minimo_requerido_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_nivel_minimo_requerido_id_fkey FOREIGN KEY (nivel_minimo_requerido_id) REFERENCES public.clasificacion_socio(id);


--
-- Name: salida_participante_dignidades salida_participante_dignidades_dignidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participante_dignidades
    ADD CONSTRAINT salida_participante_dignidades_dignidad_id_fkey FOREIGN KEY (dignidad_id) REFERENCES public.dignidades(id);


--
-- Name: salida_participante_dignidades salida_participante_dignidades_participante_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participante_dignidades
    ADD CONSTRAINT salida_participante_dignidades_participante_id_fkey FOREIGN KEY (participante_id) REFERENCES public.salida_participantes(id) ON DELETE CASCADE;


--
-- Name: salida_participantes salida_participantes_riesgo_aprobado_por_directivo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participantes
    ADD CONSTRAINT salida_participantes_riesgo_aprobado_por_directivo_fkey FOREIGN KEY (riesgo_aprobado_por_directivo) REFERENCES public.socios(id);


--
-- Name: salida_participantes salida_participantes_riesgo_aprobado_por_jefe_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participantes
    ADD CONSTRAINT salida_participantes_riesgo_aprobado_por_jefe_fkey FOREIGN KEY (riesgo_aprobado_por_jefe) REFERENCES public.socios(id);


--
-- Name: salida_participantes salida_participantes_salida_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participantes
    ADD CONSTRAINT salida_participantes_salida_id_fkey FOREIGN KEY (salida_id) REFERENCES public.salida(id) ON DELETE CASCADE;


--
-- Name: salida_participantes salida_participantes_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida_participantes
    ADD CONSTRAINT salida_participantes_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id);


--
-- Name: salida salida_publico_objetivo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_publico_objetivo_id_fkey FOREIGN KEY (publico_objetivo_id) REFERENCES public.publico_objetivo(id);


--
-- Name: salida salida_ruta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salida
    ADD CONSTRAINT salida_ruta_id_fkey FOREIGN KEY (ruta_id) REFERENCES public.rutas(id);


--
-- Name: security_events security_events_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT security_events_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE SET NULL;


--
-- Name: segmentos_viaje segmentos_viaje_contacto_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.segmentos_viaje
    ADD CONSTRAINT segmentos_viaje_contacto_id_fkey FOREIGN KEY (contacto_id) REFERENCES public.contactos(id);


--
-- Name: segmentos_viaje segmentos_viaje_informe_salida_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.segmentos_viaje
    ADD CONSTRAINT segmentos_viaje_informe_salida_id_fkey FOREIGN KEY (informe_salida_id) REFERENCES public.informe_salida(id) ON DELETE CASCADE;


--
-- Name: socio_habilitacion_log socio_habilitacion_log_cambiado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socio_habilitacion_log
    ADD CONSTRAINT socio_habilitacion_log_cambiado_por_id_fkey FOREIGN KEY (cambiado_por_id) REFERENCES public.socios(id);


--
-- Name: socio_habilitacion_log socio_habilitacion_log_estado_anterior_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socio_habilitacion_log
    ADD CONSTRAINT socio_habilitacion_log_estado_anterior_id_fkey FOREIGN KEY (estado_anterior_id) REFERENCES public.estado_habilitacion(id);


--
-- Name: socio_habilitacion_log socio_habilitacion_log_estado_nuevo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socio_habilitacion_log
    ADD CONSTRAINT socio_habilitacion_log_estado_nuevo_id_fkey FOREIGN KEY (estado_nuevo_id) REFERENCES public.estado_habilitacion(id);


--
-- Name: socio_habilitacion_log socio_habilitacion_log_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socio_habilitacion_log
    ADD CONSTRAINT socio_habilitacion_log_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE;


--
-- Name: socios socios_estado_acceso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_estado_acceso_id_fkey FOREIGN KEY (estado_acceso_id) REFERENCES public.estado_acceso(id);


--
-- Name: socios socios_estado_habilitacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_estado_habilitacion_id_fkey FOREIGN KEY (estado_habilitacion_id) REFERENCES public.estado_habilitacion(id);


--
-- Name: socios socios_nivel_tecnico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_nivel_tecnico_id_fkey FOREIGN KEY (nivel_tecnico_id) REFERENCES public.clasificacion_socio(id);


--
-- Name: socios socios_rol_sistema_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_rol_sistema_id_fkey FOREIGN KEY (rol_sistema_id) REFERENCES public.roles_sistema(id);


--
-- Name: socios socios_tipo_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.socios
    ADD CONSTRAINT socios_tipo_socio_id_fkey FOREIGN KEY (tipo_socio_id) REFERENCES public.tipo_socio_club(id);


--
-- Name: usuarios_auth usuarios_auth_socio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_auth
    ADD CONSTRAINT usuarios_auth_socio_id_fkey FOREIGN KEY (socio_id) REFERENCES public.socios(id) ON DELETE CASCADE;
