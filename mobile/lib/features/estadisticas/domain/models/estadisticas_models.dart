import '../../../dashboard/domain/models/dashboard_models.dart';

export '../../../dashboard/domain/models/dashboard_models.dart'
    show ChartDataPoint;

class EstadisticasClub {
  const EstadisticasClub({
    required this.totalSalidas,
    required this.sociosActivos,
    required this.cumbresLogradas,
    required this.tasaExito,
    required this.salidasPorMes,
    required this.distribucionNiveles,
    required this.salidasPorActividad,
  });

  final int totalSalidas;
  final int sociosActivos;
  final int cumbresLogradas;
  final double tasaExito;
  final List<ChartDataPoint> salidasPorMes;
  final List<ChartDataPoint> distribucionNiveles;
  final List<ChartDataPoint> salidasPorActividad;

  /// Construye el modelo de UI combinando los responses de
  /// /v1/estadisticas/dashboard (DashboardEstadisticasResponse) y
  /// /v1/estadisticas/club (ClubEstadisticasResponse).
  factory EstadisticasClub.fromBackend({
    required Map<String, dynamic> dash,
    required Map<String, dynamic> club,
  }) {
    final totalSalidas = (dash['totalSalidas'] as num?)?.toInt() ?? 0;
    final totalRealizadas = (dash['totalRealizadas'] as num?)?.toInt() ?? 0;
    final habilitados = (club['habilitados'] as num?)?.toInt() ??
        (club['totalSocios'] as num?)?.toInt() ??
        0;
    final tasa = totalSalidas == 0 ? 0.0 : (totalRealizadas / totalSalidas) * 100.0;

    final salidasPorMes =
        (dash['salidasPorMes'] as List<dynamic>? ?? []).map((e) {
      final m = e as Map<String, dynamic>;
      final anio = (m['anio'] as num?)?.toInt() ?? 0;
      final mes = (m['mes'] as num?)?.toInt() ?? 0;
      return ChartDataPoint(
        label: '${mes.toString().padLeft(2, '0')}/$anio',
        value: (m['total'] as num?)?.toDouble() ?? 0,
      );
    }).toList();

    final distribucionNiveles =
        (club['porNivelTecnico'] as List<dynamic>? ?? []).map((e) {
      final m = e as Map<String, dynamic>;
      return ChartDataPoint(
        label: m['nombre'] as String? ?? '',
        value: (m['total'] as num?)?.toDouble() ?? 0,
      );
    }).toList();

    final salidasPorActividad =
        (club['porTipoSocio'] as List<dynamic>? ?? []).map((e) {
      final m = e as Map<String, dynamic>;
      return ChartDataPoint(
        label: m['nombre'] as String? ?? '',
        value: (m['total'] as num?)?.toDouble() ?? 0,
      );
    }).toList();

    return EstadisticasClub(
      totalSalidas: totalSalidas,
      sociosActivos: habilitados,
      cumbresLogradas: totalRealizadas,
      tasaExito: tasa,
      salidasPorMes: salidasPorMes,
      distribucionNiveles: distribucionNiveles,
      salidasPorActividad: salidasPorActividad,
    );
  }
}

class RankingItem {
  const RankingItem({
    required this.posicion,
    required this.socioId,
    required this.nombre,
    required this.valor,
  });

  final int posicion;
  final String socioId; // UUID
  final String nombre;
  final int valor;

  factory RankingItem.fromJson(Map<String, dynamic> j, int index) {
    final nombre = j['nombre'] as String? ?? '';
    final apellido = j['apellido'] as String? ?? '';
    return RankingItem(
      posicion: index + 1,
      socioId: j['socioId']?.toString() ?? '',
      nombre: '$nombre $apellido'.trim(),
      valor: (j['total'] as num?)?.toInt() ??
          (j['participaciones'] as num?)?.toInt() ??
          (j['cumbres'] as num?)?.toInt() ??
          (j['asistencias'] as num?)?.toInt() ??
          0,
    );
  }
}

class MontanaRankingItem {
  const MontanaRankingItem({
    required this.nombre,
    required this.totalAscensos,
    this.mountainId,
    this.region,
    this.altitud,
  });
  final String nombre;
  final int totalAscensos;
  final int? mountainId;
  final String? region;
  final int? altitud;

  factory MontanaRankingItem.fromJson(Map<String, dynamic> j) =>
      MontanaRankingItem(
        nombre: j['nombre'] as String? ??
            j['mountainNombre'] as String? ??
            j['montanaNombre'] as String? ??
            '',
        totalAscensos: (j['totalSalidas'] as num?)?.toInt() ??
            (j['totalAscensos'] as num?)?.toInt() ??
            (j['count'] as num?)?.toInt() ??
            0,
        mountainId: (j['mountainId'] as num?)?.toInt(),
        region: j['region'] as String?,
        altitud: (j['altitud'] as num?)?.toInt(),
      );
}

/// Una salida del historial de participación de un socio (Kipu).
class SalidaHistorialItem {
  const SalidaHistorialItem({
    required this.salidaId,
    required this.salidaNombre,
    required this.estadoInscripcion,
    required this.estadoSalida,
    this.fecha,
    this.mountainNombre,
    this.mountainAltitud,
    this.rutaNombre,
    this.esJefeSalida = false,
    this.seRealizo,
  });

  final String salidaId;
  final String salidaNombre;
  final String estadoInscripcion;
  final String estadoSalida;
  final DateTime? fecha;
  final String? mountainNombre;
  final int? mountainAltitud;
  final String? rutaNombre;
  final bool esJefeSalida;
  final bool? seRealizo;

  factory SalidaHistorialItem.fromJson(Map<String, dynamic> j) =>
      SalidaHistorialItem(
        salidaId: j['salidaId']?.toString() ?? '',
        salidaNombre: j['salidaNombre'] as String? ?? '',
        estadoInscripcion: j['estadoInscripcion'] as String? ?? '',
        estadoSalida: j['estadoSalida'] as String? ?? '',
        fecha: j['fecha'] != null
            ? DateTime.tryParse(j['fecha'] as String)
            : null,
        mountainNombre: j['mountainNombre'] as String?,
        mountainAltitud: (j['mountainAltitud'] as num?)?.toInt(),
        rutaNombre: j['rutaNombre'] as String?,
        esJefeSalida: j['esJefeSalida'] as bool? ?? false,
        seRealizo: j['seRealizo'] as bool?,
      );
}

/// Historial de participación del socio — de /v1/estadisticas/socios/{id}.
class SocioHistorial {
  const SocioHistorial({
    required this.totalParticipaciones,
    required this.totalCumbresLogradas,
    required this.vecesJefeSalida,
    required this.historial,
  });

  final int totalParticipaciones;
  final int totalCumbresLogradas;
  final int vecesJefeSalida;
  final List<SalidaHistorialItem> historial;

  factory SocioHistorial.fromJson(Map<String, dynamic> j) => SocioHistorial(
        totalParticipaciones:
            (j['totalParticipaciones'] as num?)?.toInt() ?? 0,
        totalCumbresLogradas:
            (j['totalCumbresLogradas'] as num?)?.toInt() ?? 0,
        vecesJefeSalida: (j['vecesJefeSalida'] as num?)?.toInt() ?? 0,
        historial: (j['historial'] as List<dynamic>? ?? [])
            .map((e) =>
                SalidaHistorialItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
