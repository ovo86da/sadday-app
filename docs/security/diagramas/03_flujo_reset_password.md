# Diagrama 03 — Flujos de Gestión de Contraseña y Registro

## Flujo 1: Solicitud de Reset de Contraseña (por el usuario)

```mermaid
sequenceDiagram
    actor U as Usuario
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant EMAIL as Servicio Email

    U->>API: POST /api/v1/auth/forgot-password {correo}

    Note over API: ⚠️ Siempre responder igual independientemente<br/>de si el correo existe o no (previene enumeración)

    API->>DB: SELECT socios WHERE correo = ?
    DB-->>API: socio o null

    alt Correo no existe
        API-->>U: 200 "Si el correo está registrado, recibirás un enlace"
    end

    alt Correo existe
        API->>API: token = UUID.randomUUID()
        API->>API: token_hash = SHA256(token)
        API->>DB: UPDATE password_reset_tokens SET used=true\nWHERE socio_id=? AND used=false
        Note over DB: Invalida tokens previos no usados del mismo socio
        API->>DB: INSERT password_reset_tokens\n(socio_id, token_hash, expires_at=NOW()+1h, used=false)
        API->>EMAIL: Enviar email con link:\nhttps://app.sadday.com/reset-password?token={raw_token}
        Note over EMAIL: TLS obligatorio · Link expira en 1 hora
        API->>DB: INSERT INTO auditoria (REQUEST_PASSWORD_RESET)
        API-->>U: 200 "Si el correo está registrado, recibirás un enlace"
    end
```

---

## Flujo 2: Completar Reset de Contraseña

```mermaid
sequenceDiagram
    actor U as Usuario
    participant API as Spring Boot API
    participant DB as PostgreSQL

    U->>API: POST /api/v1/auth/reset-password\n{token, nuevaPassword, confirmarPassword}

    API->>API: Validar política de contraseña\n(min 10 chars, mayús, minús, número, especial)

    alt Contraseñas no coinciden o política no cumplida
        API-->>U: 400 "La contraseña no cumple los requisitos"
    end

    API->>API: token_hash = SHA256(token)
    API->>DB: SELECT * FROM password_reset_tokens\nWHERE token_hash = ?\nAND used = false AND expires_at > NOW()

    alt Token inválido / expirado / ya usado
        API-->>U: 400 "El enlace no es válido o expiró"
        Note over API: Mismo mensaje para todos los casos
    end

    API->>API: nuevo_hash = Argon2id.hash(nuevaPassword)
    API->>DB: UPDATE usuarios_auth SET\npassword_hash = nuevo_hash,\nfailed_attempts = 0,\nlogin_blocked = false,\nblocked_until = null,\npassword_must_change = false
    API->>DB: UPDATE password_reset_tokens SET used=true
    API->>DB: UPDATE refresh_tokens SET revoked=true\nWHERE socio_id = ?\n(invalida todas las sesiones activas)
    API->>DB: INSERT INTO auditoria (PASSWORD_RESET_SUCCESS)
    API-->>U: 200 "Contraseña restablecida correctamente."
```

---

## Flujo 3: Cambio de Contraseña (usuario autenticado)

```mermaid
sequenceDiagram
    actor U as Usuario autenticado
    participant API as Spring Boot API
    participant DB as PostgreSQL

    U->>API: POST /api/v1/auth/change-password\n{passwordActual, nuevaPassword}\n(requiere Access Token)

    API->>DB: SELECT usuarios_auth WHERE socio_id = ?
    API->>API: Argon2id.verify(passwordActual, hash)

    alt Contraseña actual incorrecta
        API-->>U: 400 "Contraseña actual incorrecta"
    end

    API->>API: Validar política de contraseña
    API->>API: nuevo_hash = Argon2id.hash(nuevaPassword)
    API->>DB: UPDATE usuarios_auth SET\npassword_hash = nuevo_hash,\npassword_must_change = false
    API->>DB: UPDATE refresh_tokens SET revoked=true\nWHERE socio_id = ?\n(forzar re-login en otros dispositivos)
    API->>DB: INSERT INTO auditoria (PASSWORD_CHANGED)
    API-->>U: 200 "Contraseña actualizada correctamente."
    Note over U: El cliente debe redirigir al login.<br/>Los refresh tokens de otros dispositivos<br/>ya no son válidos.
```

---

## Flujo 4: Registro Inicial de Nuevo Socio (por Secretaria/Admin)

```mermaid
sequenceDiagram
    actor SEC as Secretaria / Admin
    actor U as Nuevo Socio
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant EMAIL as Servicio Email

    SEC->>API: POST /api/v1/socios\n{cedula, correo, nombre, apellido,\ntipo_socio_id, rol_sistema_id, ...}
    Note over API: Requiere rol Secretaria o Admin

    API->>DB: ¿Ya existe cédula o correo?
    alt Ya existe
        API-->>SEC: 409 "Ya existe un socio con esa cédula/correo"
    end

    API->>DB: INSERT socios\n(estado=Inhabilitado, rol=Socio)
    API->>API: token = UUID.randomUUID()
    API->>API: token_hash = SHA256(token)
    API->>DB: INSERT email_verification_tokens\n(token_hash, expires_at=NOW()+72h, used=false)
    API->>EMAIL: Enviar email con link de activación:\nhttps://app.sadday.com/completar-registro?token={raw_token}
    API->>DB: INSERT INTO auditoria (INVITE_SOCIO, creado_por=sec.id)
    API-->>SEC: 201 "Socio creado. Invitación enviada."

    Note over U: El socio recibe el email y abre el link

    U->>API: GET /api/v1/registro/token-info?token={raw_token}
    API->>API: token_hash = SHA256(token)
    API->>DB: Validar token (no usado, no expirado)
    API-->>U: 200 {requiresPersonalData: true/false}
    Note over U: Frontend usa requiresPersonalData para<br/>decidir qué campos mostrar en el formulario

    U->>API: POST /api/v1/registro/complete\n{token, username, password, nombre, apellido,\ntelefono, fechaNacimiento, ...}
    API->>API: Validar política de contraseña
    API->>API: nuevo_hash = Argon2id.hash(password)
    API->>DB: UPDATE socios SET nombre, apellido, telefono, ...
    API->>DB: INSERT usuarios_auth\n(username, password_hash, password_must_change=false)
    API->>DB: UPDATE email_verification_tokens SET used=true
    API->>DB: INSERT INTO auditoria (REGISTRATION_COMPLETE)

    Note over API,DB: ⚠️ El socio queda en estado Inhabilitado.<br/>Secretaria debe habilitarlo manualmente.

    API-->>U: 200 "Cuenta activada correctamente. Ya puedes iniciar sesión."

    Note over SEC: Secretaria habilita al socio cuando corresponda
    SEC->>API: PATCH /api/v1/socios/{id}/habilitar
    API->>DB: UPDATE socios SET estado_habilitacion_id = Habilitado
    API->>DB: INSERT INTO auditoria (HABILITAR_SOCIO)
    API-->>SEC: 200 "Socio habilitado"
```

---

## Flujo 5: Reenvío de Invitación y Desbloqueo de Cuenta

```mermaid
sequenceDiagram
    actor ADM as Admin / Secretaria
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Note over ADM: Reenviar invitación (token expirado o no recibido)
    ADM->>API: POST /api/v1/socios/{id}/reenviar-invitacion
    Note over API: Requiere rol Secretaria o Admin
    API->>DB: Invalidar tokens de invitación previos
    API->>API: Generar nuevo token
    API->>DB: INSERT email_verification_tokens (nuevo token, 72h)
    API->>DB: INSERT INTO auditoria (RESEND_INVITATION)
    API-->>ADM: 200 "Invitación reenviada"

    Note over ADM: Desbloquear cuenta bloqueada por intentos fallidos
    ADM->>API: POST /api/v1/admin/usuarios-auth/{socioId}/desbloquear
    Note over API: Requiere rol Admin (solo Admin, no Secretaria)
    API->>DB: UPDATE usuarios_auth SET\nlogin_blocked=false,\nblocked_until=null,\nfailed_attempts=0
    API->>DB: INSERT INTO auditoria (DESBLOQUEAR_CUENTA,\nregistrado_por=adm.username)
    API-->>ADM: 200 "Cuenta desbloqueada correctamente"
```
