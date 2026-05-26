class SalidaJefe {
  const SalidaJefe({required this.socioId, required this.nombre});
  final String socioId; // UUID
  final String nombre;
}

class Salida {
  const Salida({
    required this.id,
    required this.nombre,
    required this.estado,
    this.fechaInicio,
    this.fechaFin,
    this.horaEncuentro,
    this.rutaNombre,
    this.montanaNombre,
    this.nivelMinimo,
    this.capacidadMaxima,
    this.totalInscritos,
    this.inscripcionesCerradas = false,
    this.jefe,
  });

  final String id; // UUID
  final String nombre;
  final String estado;
  final DateTime? fechaInicio;
  final DateTime? fechaFin;
  final String? horaEncuentro;
  final String? rutaNombre;
  final String? montanaNombre;
  final String? nivelMinimo;
  final int? capacidadMaxima;
  final int? totalInscritos;
  final bool inscripcionesCerradas;
  final SalidaJefe? jefe;

  factory Salida.fromJson(Map<String, dynamic> j) => Salida(
        id: j['id']?.toString() ?? '',
        nombre: j['nombre'] as String? ?? '',
        estado: j['estado'] as String? ?? '',
        fechaInicio: j['fechaInicio'] != null
            ? DateTime.tryParse(j['fechaInicio'] as String)
            : null,
        fechaFin: j['fechaFin'] != null
            ? DateTime.tryParse(j['fechaFin'] as String)
            : null,
        horaEncuentro: j['horaEncuentroClub'] as String?,
        rutaNombre: j['rutaNombre'] as String?,
        // El backend no devuelve montanaNombre en summary; solo en detail si aplica.
        montanaNombre: j['montanaNombre'] as String?,
        nivelMinimo: j['nivelMinimoNombre'] as String? ??
            j['nivelMinimoRequeridoNombre'] as String?,
        capacidadMaxima: (j['capacidadMaxima'] as num?)?.toInt(),
        totalInscritos: (j['totalInscritos'] as num?)?.toInt(),
        inscripcionesCerradas: j['inscripcionesCerradas'] as bool? ?? false,
      );
}

class Dignidad {
  const Dignidad({
    required this.asignadaId,
    required this.id,
    required this.nombre,
  });

  /// Id del registro de asignación (DignidadAsignada.id) — usar para eliminar.
  final int asignadaId;

  /// Id del tipo de dignidad (dignidadId) — usar para filtrar duplicados.
  final int id;

  final String nombre;

  factory Dignidad.fromJson(Map<String, dynamic> j) => Dignidad(
        asignadaId: (j['id'] as num?)?.toInt() ?? 0,
        id: (j['dignidadId'] as num?)?.toInt() ??
            (j['id'] as num?)?.toInt() ??
            0,
        nombre:
            j['dignidadNombre'] as String? ?? j['nombre'] as String? ?? '',
      );
}

class Participante {
  const Participante({
    required this.inscripcionId,
    required this.socioId,
    required this.nombre,
    required this.estadoInscripcion,
    this.esJefe = false,
    this.nivelTecnico,
    this.nivelMinimoRequeridoNombre,
    this.nivelInsuficiente = false,
    this.riesgoAprobadoPorDirectivo = false,
    this.riesgoAprobadoPorJefe = false,
    this.riesgoAprobadoPorDirectivoNombre,
    this.riesgoAprobadoPorJefeNombre,
    this.motivoDirectivo,
    this.motivoJefe,
    this.dignidades = const [],
  });

  final int inscripcionId;
  final String socioId;
  final String nombre;
  final String estadoInscripcion;
  final bool esJefe;
  final String? nivelTecnico;
  final String? nivelMinimoRequeridoNombre;
  final bool nivelInsuficiente;
  final bool riesgoAprobadoPorDirectivo;
  final bool riesgoAprobadoPorJefe;
  final String? riesgoAprobadoPorDirectivoNombre;
  final String? riesgoAprobadoPorJefeNombre;
  final String? motivoDirectivo;
  final String? motivoJefe;
  final List<Dignidad> dignidades;

  factory Participante.fromJson(Map<String, dynamic> j) {
    final nombre = j['socioNombre'] as String? ?? j['nombre'] as String? ?? '';
    final apellido = j['socioApellido'] as String? ?? j['apellido'] as String? ?? '';
    return Participante(
      inscripcionId: (j['id'] as num?)?.toInt() ?? 0,
      socioId: j['socioId']?.toString() ?? '',
      nombre: '$nombre $apellido'.trim(),
      estadoInscripcion: j['estadoInscripcion'] as String? ?? j['estado'] as String? ?? '',
      esJefe: j['esJefeSalida'] as bool? ?? false,
      nivelTecnico: j['nivelSocioNombre'] as String? ?? j['nivelTecnico'] as String?,
      nivelMinimoRequeridoNombre: j['nivelMinimoRequeridoNombre'] as String?,
      nivelInsuficiente: j['nivelInsuficiente'] as bool? ?? false,
      riesgoAprobadoPorDirectivo: j['riesgoAprobadoPorDirectivo'] != null,
      riesgoAprobadoPorJefe: j['riesgoAprobadoPorJefe'] != null,
      riesgoAprobadoPorDirectivoNombre: j['riesgoAprobadoPorDirectivoNombre'] as String?,
      riesgoAprobadoPorJefeNombre: j['riesgoAprobadoPorJefeNombre'] as String?,
      motivoDirectivo: j['motivoDirectivo'] as String?,
      motivoJefe: j['motivoJefe'] as String?,
      dignidades: (j['dignidades'] as List<dynamic>? ?? [])
          .map((d) => Dignidad.fromJson(d as Map<String, dynamic>))
          .toList(),
    );
  }
}

class SalidaDetalle extends Salida {
  const SalidaDetalle({
    required super.id,
    required super.nombre,
    required super.estado,
    super.fechaInicio,
    super.fechaFin,
    super.horaEncuentro,
    super.rutaNombre,
    super.montanaNombre,
    super.nivelMinimo,
    super.capacidadMaxima,
    super.totalInscritos,
    super.inscripcionesCerradas,
    super.jefe,
    this.descripcion,
    this.publicoObjetivo,
    this.tipoActividad,
    this.motivoCancelacion,
    this.rutaId,
    this.publicoObjetivoId,
    this.formatoSalidaId,
    this.nivelMinimoRequeridoId,
    this.horaEstimadaRegreso,
    this.participantes = const [],
  });

  final String? descripcion;
  final String? publicoObjetivo;
  final String? tipoActividad;
  final String? motivoCancelacion;
  final int? rutaId;
  final String? publicoObjetivoId;
  final String? formatoSalidaId;
  final String? nivelMinimoRequeridoId;
  final String? horaEstimadaRegreso;
  final List<Participante> participantes;

  factory SalidaDetalle.fromJson(Map<String, dynamic> j) {
    final base = Salida.fromJson(j);
    final participantes = (j['participantes'] as List<dynamic>? ?? [])
        .map((p) => Participante.fromJson(p as Map<String, dynamic>))
        .toList();
    // El backend no expone "jefe" como objeto; lo derivamos del participante con esJefeSalida=true.
    SalidaJefe? jefe;
    for (final p in participantes) {
      if (p.esJefe) {
        jefe = SalidaJefe(socioId: p.socioId, nombre: p.nombre);
        break;
      }
    }
    return SalidaDetalle(
      id: base.id,
      nombre: base.nombre,
      estado: base.estado,
      fechaInicio: base.fechaInicio,
      fechaFin: base.fechaFin,
      horaEncuentro: base.horaEncuentro,
      rutaNombre: base.rutaNombre,
      montanaNombre: base.montanaNombre,
      nivelMinimo: base.nivelMinimo,
      capacidadMaxima: base.capacidadMaxima,
      totalInscritos: base.totalInscritos,
      inscripcionesCerradas: base.inscripcionesCerradas,
      jefe: jefe,
      descripcion: j['descripcion'] as String?,
      publicoObjetivo: j['publicoObjetivoNombre'] as String?,
      tipoActividad: j['tipoActividad'] as String?,
      motivoCancelacion: j['motivoCancelacion'] as String?,
      rutaId: (j['rutaId'] as num?)?.toInt(),
      publicoObjetivoId: j['publicoObjetivoId'] as String?,
      formatoSalidaId: j['formatoSalidaId'] as String?,
      nivelMinimoRequeridoId: j['nivelMinimoRequeridoId'] as String?,
      horaEstimadaRegreso: j['horaEstimadaRegresoClub'] as String?,
      participantes: participantes,
    );
  }
}

/// Item de catálogo de salidas (público objetivo, formato) — id UUID + nombre.
class SalidaLookupItem {
  const SalidaLookupItem({required this.id, required this.nombre});
  final String id;
  final String nombre;

  factory SalidaLookupItem.fromJson(Map<String, dynamic> j) =>
      SalidaLookupItem(
        id: j['id']?.toString() ?? '',
        nombre: j['nombre'] as String? ?? '',
      );
}

/// Catálogos del módulo Salidas — de GET /v1/salidas/lookups.
class SalidaLookups {
  const SalidaLookups({
    this.publicosObjetivo = const [],
    this.formatosSalida = const [],
    this.dignidades = const [],
  });

  final List<SalidaLookupItem> publicosObjetivo;
  final List<SalidaLookupItem> formatosSalida;
  final List<Dignidad> dignidades;

  factory SalidaLookups.fromJson(Map<String, dynamic> j) {
    List<SalidaLookupItem> parseLookup(String key) =>
        (j[key] as List<dynamic>? ?? [])
            .map((e) => SalidaLookupItem.fromJson(e as Map<String, dynamic>))
            .toList();
    return SalidaLookups(
      publicosObjetivo: parseLookup('publicosObjetivo'),
      formatosSalida: parseLookup('formatosSalida'),
      dignidades: (j['dignidades'] as List<dynamic>? ?? [])
          .map((e) => Dignidad.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

/// Salida activa que solapa con un rango de fechas — de /v1/salidas/solapamiento.
class Solapamiento {
  const Solapamiento({
    required this.id,
    required this.nombre,
    required this.fechaInicio,
    required this.fechaFin,
  });

  final String id;
  final String nombre;
  final String fechaInicio;
  final String fechaFin;

  factory Solapamiento.fromJson(Map<String, dynamic> j) => Solapamiento(
        id: j['id']?.toString() ?? '',
        nombre: j['nombre'] as String? ?? '',
        fechaInicio: j['fechaInicio'] as String? ?? '',
        fechaFin: j['fechaFin'] as String? ?? '',
      );
}
