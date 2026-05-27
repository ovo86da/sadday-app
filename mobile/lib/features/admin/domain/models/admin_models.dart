class AdminConfig {
  const AdminConfig({
    required this.clave,
    required this.valor,
    this.descripcion,
  });

  final String clave;
  final String valor;
  final String? descripcion;

  factory AdminConfig.fromJson(Map<String, dynamic> j) => AdminConfig(
        clave: j['clave'] as String? ?? '',
        valor: j['valor']?.toString() ?? '',
        descripcion: j['descripcion'] as String?,
      );

  AdminConfig copyWithValor(String nuevoValor) =>
      AdminConfig(clave: clave, valor: nuevoValor, descripcion: descripcion);
}

class AuditoriaEntry {
  const AuditoriaEntry({
    required this.id,
    required this.actor,
    required this.accion,
    required this.entidad,
    required this.fecha,
    this.detalle,
    this.resultado,
    this.ipAddress,
  });

  final int id; // Long
  final String actor; // actorUsername o actorNombre
  final String accion;
  final String entidad;
  final String? detalle;
  final String? resultado;
  final String? ipAddress;
  final DateTime fecha;

  factory AuditoriaEntry.fromJson(Map<String, dynamic> j) => AuditoriaEntry(
        id: (j['id'] as num?)?.toInt() ?? 0,
        actor: j['actorUsername'] as String? ??
            j['actorNombre'] as String? ??
            j['actor'] as String? ??
            '',
        accion: j['accion'] as String? ?? '',
        entidad: j['entidadAfectada'] as String? ??
            j['entidad'] as String? ??
            '',
        detalle: j['detalle'] as String?,
        resultado: j['resultado'] as String?,
        ipAddress: j['ipAddress'] as String?,
        fecha: DateTime.tryParse(
                (j['createdAt'] ?? j['fecha']) as String? ?? '') ??
            DateTime.now(),
      );
}

class SecurityEvent {
  const SecurityEvent({
    required this.id,
    required this.tipo,
    required this.descripcion,
    required this.fecha,
    this.ip,
    this.pais,
    this.usuarioEmail,
  });

  final String id; // UUID
  final String tipo;
  final String descripcion;
  final DateTime fecha;
  final String? ip;
  final String? pais;
  final String? usuarioEmail;

  factory SecurityEvent.fromJson(Map<String, dynamic> j) => SecurityEvent(
        id: j['id']?.toString() ?? '',
        tipo: j['eventType'] as String? ?? j['tipo'] as String? ?? '',
        descripcion: j['metadata'] as String? ??
            j['descripcion'] as String? ??
            '',
        fecha: DateTime.tryParse(
                (j['createdAt'] ?? j['fecha']) as String? ?? '') ??
            DateTime.now(),
        ip: j['ipAddress'] as String? ?? j['ip'] as String?,
        pais: j['countryCode'] as String? ?? j['pais'] as String?,
        usuarioEmail:
            j['username'] as String? ?? j['usuarioEmail'] as String?,
      );
}

class UsuarioAuth {
  const UsuarioAuth({
    required this.id,
    required this.nombre,
    required this.email,
    required this.estadoAcceso,
    required this.rol,
    this.username,
    this.totpEnabled = false,
    this.loginBlocked = false,
  });

  final String id; // socioId UUID
  final String nombre;
  final String email;
  final String estadoAcceso;
  final String rol;
  final String? username;
  final bool totpEnabled;
  final bool loginBlocked;

  factory UsuarioAuth.fromJson(Map<String, dynamic> j) => UsuarioAuth(
        id: (j['socioId'] ?? j['id'])?.toString() ?? '',
        nombre: '${j['nombre'] ?? ''} ${j['apellido'] ?? ''}'.trim(),
        email: j['correo'] as String? ?? j['email'] as String? ?? '',
        username: j['username'] as String?,
        estadoAcceso: j['estadoAcceso'] as String? ?? '',
        rol: j['rol'] as String? ?? '',
        totpEnabled: j['totpEnabled'] as bool? ?? false,
        loginBlocked: j['loginBlocked'] as bool? ?? false,
      );
}
