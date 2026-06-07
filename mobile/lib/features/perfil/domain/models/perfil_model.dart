class ContactoEmergencia {
  const ContactoEmergencia({
    required this.nombre,
    required this.telefono,
    this.direccion,
  });

  final String nombre;
  final String telefono;
  final String? direccion;
}

class PerfilSocio {
  const PerfilSocio({
    required this.id,
    required this.nombre,
    required this.apellido,
    this.cedula,
    this.correo,
    this.telefono,
    this.tipoSangre,
    this.direccion,
    this.rol,
    this.tipoSocio,
    this.estadoHabilitacion,
    this.nivelTecnico,
    this.fechaNacimiento,
    this.edad,
    this.contactosEmergencia = const [],
  });

  final String id; // UUID del backend
  final String nombre;
  final String apellido;
  final String? cedula;
  final String? correo;
  final String? telefono;
  final String? tipoSangre;
  final String? direccion;
  final String? rol;
  final String? tipoSocio;
  final String? estadoHabilitacion;
  final String? nivelTecnico;
  final DateTime? fechaNacimiento;
  final int? edad;
  final List<ContactoEmergencia> contactosEmergencia;

  String get nombreCompleto => '$nombre $apellido'.trim();

  factory PerfilSocio.fromJson(Map<String, dynamic> j) {
    return PerfilSocio(
      id: j['id']?.toString() ?? '',
      nombre: j['nombre'] as String? ?? '',
      apellido: j['apellido'] as String? ?? '',
      cedula: j['cedula'] as String?,
      correo: j['correo'] as String?,
      telefono: j['telefono'] as String?,
      tipoSangre: j['tipoSangre'] as String?,
      direccion: j['direccion'] as String?,
      rol: j['rolSistema'] as String?,
      tipoSocio: j['tipoSocio'] as String?,
      estadoHabilitacion: j['estadoHabilitacion'] as String?,
      nivelTecnico: j['nivelTecnico'] as String?,
      fechaNacimiento: j['fechaNacimiento'] != null
          ? DateTime.tryParse(j['fechaNacimiento'] as String)
          : null,
      edad: (j['edad'] as num?)?.toInt(),
      contactosEmergencia: const [],
    );
  }

  PerfilSocio copyWith({
    String? correo,
    String? telefono,
    String? tipoSangre,
    String? direccion,
    List<ContactoEmergencia>? contactosEmergencia,
  }) =>
      PerfilSocio(
        id: id,
        nombre: nombre,
        apellido: apellido,
        cedula: cedula,
        correo: correo ?? this.correo,
        telefono: telefono ?? this.telefono,
        tipoSangre: tipoSangre ?? this.tipoSangre,
        direccion: direccion ?? this.direccion,
        rol: rol,
        tipoSocio: tipoSocio,
        estadoHabilitacion: estadoHabilitacion,
        nivelTecnico: nivelTecnico,
        fechaNacimiento: fechaNacimiento,
        edad: edad,
        contactosEmergencia: contactosEmergencia ?? this.contactosEmergencia,
      );
}

class SesionActiva {
  const SesionActiva({
    required this.id,
    this.plataforma,
    this.browser,
    this.os,
    this.ciudad,
    this.pais,
    this.ip,
    this.creadaEn,
    this.ultimaActividad,
    this.esCurrent = false,
  });

  final String id; // UUID (sessionId)
  final String? plataforma;
  final String? browser;
  final String? os;
  final String? ciudad;
  final String? pais;
  final String? ip;
  final DateTime? creadaEn;
  final DateTime? ultimaActividad;
  final bool esCurrent;

  factory SesionActiva.fromJson(Map<String, dynamic> j) => SesionActiva(
        id: j['sessionId']?.toString() ?? '',
        plataforma: j['platform'] as String?,
        browser: j['browser'] as String?,
        os: j['os'] as String?,
        ciudad: j['city'] as String?,
        pais: j['country'] as String?,
        ip: j['ipAddress'] as String?,
        creadaEn: j['createdAt'] != null
            ? DateTime.tryParse(j['createdAt'] as String)
            : null,
        ultimaActividad: j['lastUsedAt'] != null
            ? DateTime.tryParse(j['lastUsedAt'] as String)
            : null,
        esCurrent: j['isCurrent'] as bool? ?? j['current'] as bool? ?? false,
      );
}
