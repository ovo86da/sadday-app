-- FR-013: Tabla para API Keys del MCP server
-- Las keys son solo lectura (SCOPE_readonly) y se almacena únicamente el hash SHA-256.

CREATE TABLE public.api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    socio_id     UUID NOT NULL REFERENCES public.socios(id) ON DELETE CASCADE,
    nombre       VARCHAR(100) NOT NULL,
    key_hash     VARCHAR(255) NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_socio_id  ON public.api_keys (socio_id);
CREATE INDEX idx_api_keys_key_hash  ON public.api_keys (key_hash);
