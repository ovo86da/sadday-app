-- NIST SP 800-63B §5.1.4.2 / RFC 6238 §5.2: cada OTP sólo puede usarse una vez.
-- Guardamos el último counter TOTP aceptado para detectar y rechazar replays.
ALTER TABLE public.usuarios_auth
    ADD COLUMN last_used_totp_counter bigint NOT NULL DEFAULT -1;
