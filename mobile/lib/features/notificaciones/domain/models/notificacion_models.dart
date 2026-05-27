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
