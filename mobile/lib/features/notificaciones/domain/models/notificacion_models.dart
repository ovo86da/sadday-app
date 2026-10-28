/// Un motivo por el que una inscripción está retenida (FR-028).
///
/// El servidor calcula el sentido y los valores: dependen del `rank` de cada
/// catálogo de dificultad, que el cliente no conoce.
class HallazgoNivel {
  const HallazgoNivel({
    required this.tipo,
    required this.detalle,
    this.escala,
    this.exigeRuta,
    this.permiteNivel,
  });

  /// `EXCEDE` cuando la ruta pide más de lo que el nivel permite.
  /// `SIN_COMPROBAR` cuando falta el dato para saberlo — frena igual.
  final String tipo;

  /// Frase lista para mostrar, construida en el servidor.
  final String detalle;

  /// «Hielo», «Senderismo»… o null si el hueco no es de una escala concreta.
  final String? escala;
  final String? exigeRuta;
  final String? permiteNivel;

  bool get excede => tipo == 'EXCEDE';
  bool get sinComprobar => tipo == 'SIN_COMPROBAR';

  factory HallazgoNivel.fromJson(Map<String, dynamic> j) => HallazgoNivel(
        // Un tipo que no se reconozca cuenta como «no se pudo comprobar», nunca
        // como visto bueno: si el servidor añade un caso y esta app es vieja,
        // el fallo seguro es retener, no dejar pasar.
        tipo: j['tipo'] == 'EXCEDE' ? 'EXCEDE' : 'SIN_COMPROBAR',
        detalle: j['detalle'] as String? ?? '',
        escala: j['escala'] as String?,
        exigeRuta: j['exigeRuta'] as String?,
        permiteNivel: j['permiteNivel'] as String?,
      );
}

/// Qué dice la matriz de acceso sobre un socio y una ruta (FR-028).
class EvaluacionNivel {
  const EvaluacionNivel({required this.apto, required this.hallazgos});

  final bool apto;
  final List<HallazgoNivel> hallazgos;

  List<HallazgoNivel> get queExceden =>
      hallazgos.where((h) => h.excede).toList();
  List<HallazgoNivel> get sinComprobar =>
      hallazgos.where((h) => h.sinComprobar).toList();

  factory EvaluacionNivel.fromJson(Map<String, dynamic> j) => EvaluacionNivel(
        apto: j['apto'] as bool? ?? false,
        hallazgos: (j['hallazgos'] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(HallazgoNivel.fromJson)
            .toList(),
      );
}

class AprobacionPendiente {
  const AprobacionPendiente({
    required this.participanteId,
    required this.salidaId,
    required this.salidaNombre,
    required this.socioId,
    required this.socioNombre,
    this.fechaSalida,
    this.nivelSocio,
    this.nivelMinimo,
    this.aprobadoPorDirectivo = false,
    this.aprobadoPorJefe = false,
    this.evaluacionNivel,
  });

  final int participanteId; // Long
  final String salidaId; // UUID
  final String salidaNombre;
  final String socioId; // UUID
  final DateTime? fechaSalida;
  final String socioNombre;
  final String? nivelSocio;
  final String? nivelMinimo;
  final bool aprobadoPorDirectivo;
  final bool aprobadoPorJefe;

  /// Por qué está parada, escala por escala. Null si el servidor no lo manda.
  final EvaluacionNivel? evaluacionNivel;

  factory AprobacionPendiente.fromJson(Map<String, dynamic> j) =>
      AprobacionPendiente(
        participanteId: (j['participanteId'] as num?)?.toInt() ?? 0,
        salidaId: j['salidaId']?.toString() ?? '',
        salidaNombre: j['salidaNombre'] as String? ?? '',
        socioId: j['socioId']?.toString() ?? '',
        fechaSalida: j['fechaSalida'] != null
            ? DateTime.tryParse(j['fechaSalida'] as String)
            : null,
        socioNombre:
            '${j['socioNombre'] ?? ''} ${j['socioApellido'] ?? ''}'.trim(),
        nivelSocio: j['nivelSocioNombre'] as String?,
        nivelMinimo: j['nivelMinimoNombre'] as String?,
        aprobadoPorDirectivo: j['aprobadoPorDirectivo'] as bool? ?? false,
        aprobadoPorJefe: j['aprobadoPorJefe'] as bool? ?? false,
        evaluacionNivel: j['evaluacionNivel'] is Map<String, dynamic>
            ? EvaluacionNivel.fromJson(
                j['evaluacionNivel'] as Map<String, dynamic>)
            : null,
      );
}

class AlertaSinJefe {
  const AlertaSinJefe({
    required this.salidaId,
    required this.salidaNombre,
    this.fechaSalida,
    this.jefeAbandonoNombre,
  });

  final String salidaId;
  final String salidaNombre;
  final DateTime? fechaSalida;
  final String? jefeAbandonoNombre;

  factory AlertaSinJefe.fromJson(Map<String, dynamic> j) => AlertaSinJefe(
        salidaId: j['salidaId']?.toString() ?? '',
        salidaNombre: j['salidaNombre'] as String? ?? '',
        fechaSalida: j['fechaSalida'] != null
            ? DateTime.tryParse(j['fechaSalida'] as String)
            : null,
        jefeAbandonoNombre: j['jefeAbandonoNombre'] as String?,
      );
}
