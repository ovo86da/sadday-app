class CreateActaRequest {
  CreateActaRequest({
    this.numero,
    this.fecha,
    required this.tipo,
    this.lugar,
    this.descripcion,
    this.actividadesRealizadas,
    this.actividadesPorRealizar,
    this.acuerdos,
    this.varios,
    this.observaciones,
  });

  String? numero;
  DateTime? fecha;
  String tipo;
  String? lugar;
  String? descripcion;
  String? actividadesRealizadas;
  String? actividadesPorRealizar;
  String? acuerdos;
  String? varios;
  String? observaciones;

  Map<String, dynamic> toJson() {
    final m = <String, dynamic>{'tipoActa': tipo};
    if (numero != null && numero!.isNotEmpty) m['numeroReunion'] = numero;
    if (fecha != null) m['fecha'] = fecha!.toIso8601String().substring(0, 10);
    _put(m, 'lugar', lugar);
    _put(m, 'descripcion', descripcion);
    _put(m, 'actividadesRealizadasDesc', actividadesRealizadas);
    _put(m, 'actividadesPorRealizar', actividadesPorRealizar);
    _put(m, 'acuerdos', acuerdos);
    _put(m, 'varios', varios);
    _put(m, 'observaciones', observaciones);
    return m;
  }

  static void _put(Map<String, dynamic> m, String key, String? v) {
    if (v != null && v.isNotEmpty) m[key] = v;
  }
}

class ActaAsistente {
  const ActaAsistente({
    required this.id,
    required this.nombre,
    this.socioId,
  });
  final int id; // Long
  final String? socioId;
  final String nombre;

  factory ActaAsistente.fromJson(Map<String, dynamic> j) => ActaAsistente(
        id: (j['id'] as num?)?.toInt() ?? 0,
        socioId: j['socioId']?.toString(),
        nombre: '${j['socioNombre'] ?? j['nombre'] ?? ''} '
                '${j['socioApellido'] ?? j['apellido'] ?? ''}'
            .trim(),
      );
}

class Acta {
  const Acta({
    required this.id,
    this.numero,
    this.fecha,
    this.tipo,
    this.descripcion,
    this.lugar,
    this.tienePdf = false,
    this.totalAsistentes = 0,
  });

  final String id; // UUID
  final String? numero;
  final DateTime? fecha;
  final String? tipo; // DIRECTIVA | SOCIOS
  final String? descripcion;
  final String? lugar;
  final bool tienePdf;
  final int totalAsistentes;

  factory Acta.fromJson(Map<String, dynamic> j) => Acta(
        id: j['id']?.toString() ?? '',
        numero: j['numeroReunion']?.toString() ?? j['numero']?.toString(),
        fecha: j['fecha'] != null
            ? DateTime.tryParse(j['fecha'] as String)
            : null,
        tipo: j['tipoActa'] as String? ?? j['tipo'] as String?,
        descripcion: j['descripcion'] as String?,
        lugar: j['lugar'] as String?,
        tienePdf: j['documentoId'] != null || (j['tienePdf'] as bool? ?? false),
        totalAsistentes: (j['totalAsistentes'] as num?)?.toInt() ?? 0,
      );
}

class ActaDetalle extends Acta {
  const ActaDetalle({
    required super.id,
    super.numero,
    super.fecha,
    super.tipo,
    super.descripcion,
    super.lugar,
    super.tienePdf,
    super.totalAsistentes,
    this.asistentes = const [],
    this.actividadesRealizadas,
    this.actividadesPorRealizar,
    this.acuerdos,
    this.varios,
    this.observaciones,
    this.presidenteNombre,
    this.secretariaNombre,
  });

  final List<ActaAsistente> asistentes;
  final String? actividadesRealizadas;
  final String? actividadesPorRealizar;
  final String? acuerdos;
  final String? varios;
  final String? observaciones;
  final String? presidenteNombre;
  final String? secretariaNombre;

  factory ActaDetalle.fromJson(Map<String, dynamic> j) {
    final base = Acta.fromJson(j);
    final asistentes = (j['asistentes'] as List<dynamic>? ?? [])
        .map((a) => ActaAsistente.fromJson(a as Map<String, dynamic>))
        .toList();
    return ActaDetalle(
      id: base.id,
      numero: base.numero,
      fecha: base.fecha,
      tipo: base.tipo,
      descripcion: base.descripcion,
      lugar: base.lugar,
      tienePdf: base.tienePdf,
      totalAsistentes: asistentes.length,
      asistentes: asistentes,
      actividadesRealizadas: j['actividadesRealizadasDesc'] as String?,
      actividadesPorRealizar: j['actividadesPorRealizar'] as String?,
      acuerdos: j['acuerdos'] as String?,
      varios: j['varios'] as String?,
      observaciones: j['observaciones'] as String?,
      presidenteNombre: j['presidenteReunionNombre'] as String?,
      secretariaNombre: j['secretariaReunionNombre'] as String?,
    );
  }
}
