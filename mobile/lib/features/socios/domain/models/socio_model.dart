class Socio {
  const Socio({
    required this.id,
    required this.nombre,
    required this.apellido,
    required this.correo,
    required this.rol,
    required this.estadoHabilitacion,
    required this.tipoSocio,
    required this.esJefeMontana,
    this.cedula,
    this.telefono,
    this.direccion,
    this.tipoSangre,
    this.nivelTecnico,
    this.fechaNacimiento,
    this.fechaIngreso,
    this.edad,
    this.antiguedadAnios,
  });

  final String id; // UUID
  final String nombre;
  final String apellido;
  final String? cedula;
  final String correo;
  final String? telefono;
  final String? direccion;
  final String? tipoSangre;
  final String rol;
  final String estadoHabilitacion;
  final String tipoSocio;
  final String? nivelTecnico;
  final String? fechaNacimiento;
  final String? fechaIngreso;
  final int? edad;
  final int? antiguedadAnios;
  final bool esJefeMontana;

  // Backwards-compat: el screen antiguo usaba `sangre`.
  String? get sangre => tipoSangre;

  String get nombreCompleto => '$nombre $apellido';

  String get initials {
    final n = nombre.isNotEmpty ? nombre[0].toUpperCase() : '';
    final a = apellido.isNotEmpty ? apellido[0].toUpperCase() : '';
    return '$n$a';
  }

  factory Socio.fromJson(Map<String, dynamic> j) => Socio(
        id: j['id']?.toString() ?? '',
        nombre: j['nombre'] as String? ?? '',
        apellido: j['apellido'] as String? ?? '',
        cedula: j['cedula'] as String?,
        correo: j['correo'] as String? ?? '',
        telefono: j['telefono'] as String?,
        direccion: j['direccion'] as String?,
        tipoSangre: j['tipoSangre'] as String? ?? j['sangre'] as String?,
        rol: _str(j['rolSistema'] ?? j['rol']),
        estadoHabilitacion: _str(j['estadoHabilitacion']),
        tipoSocio: _str(j['tipoSocio']),
        nivelTecnico: _strOrNull(j['nivelTecnico']),
        fechaNacimiento: j['fechaNacimiento'] as String?,
        fechaIngreso: j['fechaIngreso'] as String?,
        edad: (j['edad'] as num?)?.toInt(),
        antiguedadAnios: (j['antiguedadAnios'] as num?)?.toInt(),
        esJefeMontana: j['esJefeMontana'] as bool? ?? false,
      );

  static String _str(dynamic v) {
    if (v == null) return '';
    if (v is String) return v;
    if (v is Map) return v['codigo'] as String? ?? v['nombre'] as String? ?? '';
    return v.toString();
  }

  static String? _strOrNull(dynamic v) {
    final s = _str(v);
    return s.isEmpty ? null : s;
  }
}

class SocioDetalle extends Socio {
  const SocioDetalle({
    required super.id,
    required super.nombre,
    required super.apellido,
    required super.correo,
    required super.rol,
    required super.estadoHabilitacion,
    required super.tipoSocio,
    required super.esJefeMontana,
    super.cedula,
    super.telefono,
    super.direccion,
    super.tipoSangre,
    super.nivelTecnico,
    super.fechaNacimiento,
    super.fechaIngreso,
    super.edad,
    super.antiguedadAnios,
    this.habilitacionLog = const [],
    this.cuotas = const [],
    this.emergencyContactName,
    this.emergencyContactPhone,
    this.emergencyContactName2,
    this.emergencyContactPhone2,
  });

  final List<HabilitacionLogEntry> habilitacionLog;
  final List<Cuota> cuotas;
  final String? emergencyContactName;
  final String? emergencyContactPhone;
  final String? emergencyContactName2;
  final String? emergencyContactPhone2;

  factory SocioDetalle.fromJson(Map<String, dynamic> j) {
    final base = Socio.fromJson(j);
    return SocioDetalle(
      id: base.id,
      nombre: base.nombre,
      apellido: base.apellido,
      cedula: base.cedula,
      correo: base.correo,
      telefono: base.telefono,
      direccion: base.direccion,
      tipoSangre: base.tipoSangre,
      rol: base.rol,
      estadoHabilitacion: base.estadoHabilitacion,
      tipoSocio: base.tipoSocio,
      nivelTecnico: base.nivelTecnico,
      fechaNacimiento: base.fechaNacimiento,
      fechaIngreso: base.fechaIngreso,
      edad: base.edad,
      antiguedadAnios: base.antiguedadAnios,
      esJefeMontana: base.esJefeMontana,
      emergencyContactName: j['emergencyContactName'] as String?,
      emergencyContactPhone: j['emergencyContactPhone'] as String?,
      emergencyContactName2: j['emergencyContactName2'] as String?,
      emergencyContactPhone2: j['emergencyContactPhone2'] as String?,
      // El detalle no incluye log/cuotas; vienen de endpoints aparte.
      habilitacionLog: const [],
      cuotas: const [],
    );
  }
}

class HabilitacionLogEntry {
  const HabilitacionLogEntry({
    required this.id,
    required this.estadoNuevo,
    required this.actor,
    required this.fecha,
    this.estadoAnterior,
    this.fuente,
    this.notas,
  });

  final int id;
  final String? estadoAnterior;
  final String estadoNuevo;
  final String actor;
  final DateTime fecha;
  final String? fuente;
  final String? notas;

  // Backwards-compat para UI.
  String get estado => estadoNuevo;
  String? get motivo => notas;

  factory HabilitacionLogEntry.fromJson(Map<String, dynamic> j) =>
      HabilitacionLogEntry(
        id: (j['id'] as num?)?.toInt() ?? 0,
        estadoAnterior: j['estadoAnterior'] as String?,
        estadoNuevo: j['estadoNuevo'] as String? ?? j['estado'] as String? ?? '',
        actor: j['cambiadoPorNombre'] as String? ?? j['actor'] as String? ?? '',
        fecha: DateTime.tryParse(
                (j['cambiadoEn'] ?? j['fecha']) as String? ?? '') ??
            DateTime.now(),
        fuente: j['fuente'] as String?,
        notas: j['notas'] as String? ?? j['motivo'] as String?,
      );
}

class Cuota {
  const Cuota({
    required this.id,
    required this.fecha,
    required this.valor,
    required this.estado,
    this.registradoPor,
    this.createdAt,
  });

  final int id;
  final DateTime? fecha;
  final double valor;
  final String estado;
  final String? registradoPor;
  final DateTime? createdAt;

  String get periodo {
    final f = fecha;
    if (f == null) return '';
    return '${f.month.toString().padLeft(2, '0')}/${f.year}';
  }

  double get monto => valor;
  bool get pagado => estado.toUpperCase() == 'PAGADA';
  DateTime? get fechaPago => pagado ? fecha : null;

  factory Cuota.fromJson(Map<String, dynamic> j) => Cuota(
        id: (j['id'] as num?)?.toInt() ?? 0,
        fecha: j['fecha'] != null
            ? DateTime.tryParse(j['fecha'] as String)
            : null,
        valor: (j['valor'] as num?)?.toDouble() ??
            (j['monto'] as num?)?.toDouble() ??
            0.0,
        estado: j['estado'] as String? ?? '',
        registradoPor: j['registradoPorNombre'] as String?,
        createdAt: j['createdAt'] != null
            ? DateTime.tryParse(j['createdAt'] as String)
            : null,
      );
}

class Invitacion {
  const Invitacion({
    required this.id,
    required this.nombre,
    required this.apellido,
    required this.correo,
    required this.estado,
    required this.creadaEn,
    this.cedula,
    this.telefono,
  });

  final String id; // UUID
  final String nombre;
  final String apellido;
  final String correo;
  final String estado;
  final DateTime creadaEn;
  final String? cedula;
  final String? telefono;

  String get nombreCompleto => '$nombre $apellido';

  factory Invitacion.fromJson(Map<String, dynamic> j) => Invitacion(
        id: j['id']?.toString() ?? '',
        nombre: j['nombre'] as String? ?? '',
        apellido: j['apellido'] as String? ?? '',
        correo: j['correo'] as String? ?? '',
        cedula: j['cedula'] as String?,
        telefono: j['telefono'] as String?,
        estado: j['estado'] as String? ?? '',
        creadaEn: DateTime.tryParse(
                (j['creadoEn'] ?? j['creadaEn']) as String? ?? '') ??
            DateTime.now(),
      );
}

/// Item de catálogo (rol, estado, tipo de socio) — id numérico + nombre.
class SocioLookupItem {
  const SocioLookupItem({required this.id, required this.nombre});
  final int id;
  final String nombre;

  factory SocioLookupItem.fromJson(Map<String, dynamic> j) => SocioLookupItem(
        id: (j['id'] as num?)?.toInt() ?? 0,
        nombre: j['nombre'] as String? ?? '',
      );
}

/// Clasificación / nivel técnico — id UUID + nombre + nivel numérico.
class Clasificacion {
  const Clasificacion({
    required this.id,
    required this.nombre,
    this.nivel = 0,
  });

  final String id;
  final String nombre;
  final int nivel;

  factory Clasificacion.fromJson(Map<String, dynamic> j) => Clasificacion(
        id: j['id']?.toString() ?? '',
        nombre: j['nombre'] as String? ?? '',
        nivel: (j['nivel'] as num?)?.toInt() ?? 0,
      );
}

/// Catálogos del módulo Socios — de GET /v1/socios/lookups.
class SociosLookups {
  const SociosLookups({
    this.roles = const [],
    this.estados = const [],
    this.tipos = const [],
    this.clasificaciones = const [],
  });

  final List<SocioLookupItem> roles;
  final List<SocioLookupItem> estados;
  final List<SocioLookupItem> tipos;
  final List<Clasificacion> clasificaciones;

  factory SociosLookups.fromJson(Map<String, dynamic> j) {
    List<SocioLookupItem> parse(String key) =>
        (j[key] as List<dynamic>? ?? [])
            .map((e) => SocioLookupItem.fromJson(e as Map<String, dynamic>))
            .toList();
    return SociosLookups(
      roles: parse('rolesSistema'),
      estados: parse('estadosHabilitacion'),
      tipos: parse('tiposSocio'),
      clasificaciones: (j['clasificaciones'] as List<dynamic>? ?? [])
          .map((e) => Clasificacion.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
