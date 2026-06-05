-- =============================================================================
-- V15__audit_log.sql
-- Tabla de auditoría append-only para eventos sensibles del módulo de
-- gestión documental. Nunca se hace UPDATE ni DELETE sobre esta tabla.
-- =============================================================================

CREATE TABLE public.audit_log (
    id            uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_user_id uuid,
    action        character varying(100) NOT NULL,
    resource_type character varying(100),
    resource_id   uuid,
    ip_address    character varying(45),
    user_agent    text,
    metadata_json jsonb,
    created_at    timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT audit_log_pkey PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES public.socios(id),
    CONSTRAINT chk_audit_action CHECK (action IN (
        'LEGAL_DOCUMENT_ACCEPTED',
        'LEGAL_DOCUMENT_CREATED',
        'LEGAL_DOCUMENT_VERSION_CREATED',
        'LEGAL_DOCUMENT_ACTIVATED',
        'MEDICAL_INFO_VIEWED',
        'MEDICAL_INFO_UPDATED',
        'EMERGENCY_CONTACT_UPDATED',
        'SOCIO_RETIRED',
        'SENSITIVE_DATA_DELETED',
        'ACTIVITY_RISK_ACCEPTED',
        'ACTIVITY_ENROLLMENT_BLOCKED'
    ))
);

CREATE INDEX idx_audit_log_actor_created ON public.audit_log (actor_user_id, created_at);
CREATE INDEX idx_audit_log_resource ON public.audit_log (resource_type, resource_id);
CREATE INDEX idx_audit_log_action ON public.audit_log (action, created_at);
