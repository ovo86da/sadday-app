class ChartDataPoint {
  const ChartDataPoint({required this.label, required this.value});
  final String label;
  final double value;
}

class SalidaMesPoint {
  const SalidaMesPoint({
    required this.label,
    required this.total,
    this.realizadas = 0,
    this.canceladas = 0,
    this.enCurso = 0,
    this.planificadas = 0,
  });

  final String label;
  final int total;
  final int realizadas;
  final int canceladas;
  final int enCurso;
  final int planificadas;

  factory SalidaMesPoint.fromJson(Map<String, dynamic> j) {
    final anio = (j['anio'] as num?)?.toInt() ?? 0;
    final mes = (j['mes'] as num?)?.toInt() ?? 0;
    const nombresMes = [
      'Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun',
      'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic',
    ];
    final mesNombre =
        (mes >= 1 && mes <= 12) ? nombresMes[mes - 1] : mes.toString();
    final yy = anio.toString().padLeft(4, '0').substring(2);
    return SalidaMesPoint(
      label: '$mesNombre $yy',
      total: (j['total'] as num?)?.toInt() ?? 0,
      realizadas: (j['realizadas'] as num?)?.toInt() ?? 0,
      canceladas: (j['canceladas'] as num?)?.toInt() ?? 0,
      enCurso: (j['enCurso'] as num?)?.toInt() ?? 0,
      planificadas: (j['planificadas'] as num?)?.toInt() ?? 0,
    );
  }
}

class DashboardKpis {
  const DashboardKpis({
    required this.totalSocios,
    required this.salidasPlanificadas,
    required this.salidasRealizadas,
    this.totalSalidas = 0,
    this.totalEnCurso = 0,
    this.totalCanceladas = 0,
    this.showSocios = true,
  });
  final int totalSocios;
  final int salidasPlanificadas;
  final int salidasRealizadas;
  final int totalSalidas;
  final int totalEnCurso;
  final int totalCanceladas;
  /// false cuando el usuario no es admin/secretaria/directivo y no se pudo
  /// obtener el conteo de socios (403). El widget oculta el KPI.
  final bool showSocios;

  int get totalSociosActivos => totalSocios;
}

class ProximaSalida {
  const ProximaSalida({
    required this.id,
    required this.nombre,
    required this.estado,
    this.fechaInicio,
    this.rutaNombre,
    this.montanaNombre,
    this.totalInscritos,
    this.capacidadMaxima,
  });
  final String id; // UUID del backend
  final String nombre;
  final String estado;
  final DateTime? fechaInicio;
  final String? rutaNombre;
  final String? montanaNombre;
  final int? totalInscritos;
  final int? capacidadMaxima;

  factory ProximaSalida.fromJson(Map<String, dynamic> j) => ProximaSalida(
        id: j['id']?.toString() ?? '',
        nombre: j['nombre'] as String? ?? '',
        estado: j['estado'] as String? ?? '',
        fechaInicio: j['fechaInicio'] != null
            ? DateTime.tryParse(j['fechaInicio'] as String)
            : null,
        rutaNombre: j['rutaNombre'] as String?,
        montanaNombre: j['montanaNombre'] as String?,
        totalInscritos: (j['totalInscritos'] as num?)?.toInt(),
        capacidadMaxima: (j['capacidadMaxima'] as num?)?.toInt(),
      );
}

class CumpleanosItem {
  const CumpleanosItem({required this.nombre, required this.edad});
  final String nombre;
  final int edad;

  factory CumpleanosItem.fromJson(Map<String, dynamic> j) => CumpleanosItem(
        nombre: '${j['nombre'] ?? ''} ${j['apellido'] ?? ''}'.trim(),
        edad: j['edad'] as int? ?? 0,
      );
}

/// Inscripción pendiente de aprobación de riesgo (banner Jefe de Salida).
class AprobacionPendiente {
  const AprobacionPendiente({
    required this.salidaId,
    required this.salidaNombre,
    required this.socioNombre,
    this.nivelSocio,
    this.nivelMinimo,
  });
  final String salidaId;
  final String salidaNombre;
  final String socioNombre;
  final String? nivelSocio;
  final String? nivelMinimo;

  factory AprobacionPendiente.fromJson(Map<String, dynamic> j) =>
      AprobacionPendiente(
        salidaId: j['salidaId']?.toString() ?? '',
        salidaNombre: j['salidaNombre'] as String? ?? '',
        socioNombre:
            '${j['socioNombre'] ?? ''} ${j['socioApellido'] ?? ''}'.trim(),
        nivelSocio: j['nivelSocioNombre'] as String?,
        nivelMinimo: j['nivelMinimoNombre'] as String?,
      );
}

/// Salida cuyo Jefe de Salida abandonó y aún no tiene reemplazo.
class SalidaSinJefe {
  const SalidaSinJefe({
    required this.salidaId,
    required this.salidaNombre,
    this.fechaSalida,
    this.jefeAbandonoNombre,
  });
  final String salidaId;
  final String salidaNombre;
  final DateTime? fechaSalida;
  final String? jefeAbandonoNombre;

  factory SalidaSinJefe.fromJson(Map<String, dynamic> j) => SalidaSinJefe(
        salidaId: j['salidaId']?.toString() ?? '',
        salidaNombre: j['salidaNombre'] as String? ?? '',
        fechaSalida: j['fechaSalida'] != null
            ? DateTime.tryParse(j['fechaSalida'] as String)
            : null,
        jefeAbandonoNombre: j['jefeAbandonoNombre'] as String?,
      );
}

/// Próxima salida en la que el usuario autenticado es Jefe de Salida.
class SalidaComoJefe {
  const SalidaComoJefe({
    required this.salidaId,
    required this.salidaNombre,
    this.montanaNombre,
    this.fecha,
  });
  final String salidaId;
  final String salidaNombre;
  final String? montanaNombre;
  final DateTime? fecha;
}

/// Datos mínimos de alertas de Jefe de Salida, reutilizables fuera del dashboard.
class JefeAlertasData {
  const JefeAlertasData({
    this.aprobacionesPendientes = const [],
    this.salidasSinJefe = const [],
    this.proximasComoJefe = const [],
  });
  final List<AprobacionPendiente> aprobacionesPendientes;
  final List<SalidaSinJefe> salidasSinJefe;
  final List<SalidaComoJefe> proximasComoJefe;

  bool get hasContent =>
      aprobacionesPendientes.isNotEmpty ||
      salidasSinJefe.isNotEmpty ||
      proximasComoJefe.isNotEmpty;
}

class DashboardStats {
  const DashboardStats({
    required this.kpis,
    required this.proximasSalidas,
    required this.cumpleanos,
    required this.salidasPorMes,
    this.sociosPorTipo = const [],
    this.aprobacionesPendientes = const [],
    this.salidasSinJefe = const [],
    this.proximasComoJefe = const [],
  });
  final DashboardKpis kpis;
  final List<ProximaSalida> proximasSalidas;
  final List<CumpleanosItem> cumpleanos;
  final List<SalidaMesPoint> salidasPorMes;
  final List<ChartDataPoint> sociosPorTipo;
  final List<AprobacionPendiente> aprobacionesPendientes;
  final List<SalidaSinJefe> salidasSinJefe;
  final List<SalidaComoJefe> proximasComoJefe;
}
