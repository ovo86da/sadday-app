-- =============================================================================
-- V14__activity_risk_documents.sql
-- Documentos de riesgos específicos por salida y sus aceptaciones.
-- Cada salida puede tener su propio documento de riesgo versionado.
-- =============================================================================

CREATE TABLE public.activity_risk_documents (
    id           uuid DEFAULT gen_random_uuid() NOT NULL,
    activity_id  uuid NOT NULL,
    version      integer NOT NULL DEFAULT 1,
    content      text NOT NULL,
    content_hash character varying(64) NOT NULL,
    active       boolean NOT NULL DEFAULT false,
    approved_at  timestamp without time zone,
    approved_by  uuid,
    created_at   timestamp without time zone DEFAULT now() NOT NULL,
    updated_at   timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT activity_risk_documents_pkey PRIMARY KEY (id),
    CONSTRAINT activity_risk_documents_activity_version_key UNIQUE (activity_id, version),
    CONSTRAINT fk_ard_activity FOREIGN KEY (activity_id) REFERENCES public.salida(id),
    CONSTRAINT fk_ard_approved_by FOREIGN KEY (approved_by) REFERENCES public.socios(id)
);

CREATE INDEX idx_ard_activity_id ON public.activity_risk_documents (activity_id, active);

CREATE TABLE public.activity_risk_acceptances (
    id                        uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id                  uuid NOT NULL,
    activity_id               uuid NOT NULL,
    activity_risk_document_id uuid NOT NULL,
    document_version          integer NOT NULL,
    content_hash              character varying(64) NOT NULL,
    accepted_at               timestamp without time zone DEFAULT now() NOT NULL,
    ip_address                character varying(45),
    user_agent                text,
    created_at                timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT activity_risk_acceptances_pkey PRIMARY KEY (id),
    CONSTRAINT activity_risk_acceptances_unique_key UNIQUE (socio_id, activity_id, activity_risk_document_id),
    CONSTRAINT fk_ara_socio FOREIGN KEY (socio_id) REFERENCES public.socios(id),
    CONSTRAINT fk_ara_activity FOREIGN KEY (activity_id) REFERENCES public.salida(id),
    CONSTRAINT fk_ara_document FOREIGN KEY (activity_risk_document_id) REFERENCES public.activity_risk_documents(id)
);

CREATE INDEX idx_ara_socio_activity ON public.activity_risk_acceptances (socio_id, activity_id);
