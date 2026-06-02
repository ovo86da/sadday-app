import 'package:dio/dio.dart';
import '../domain/models/dashboard_models.dart';

class DashboardRemoteDataSource {
  const DashboardRemoteDataSource(this._dio);
  final Dio _dio;

  Future<DashboardStats> getDashboard({int meses = 12, String? socioId}) async {
    // Todas las llamadas en paralelo. Las que pueden 403 según rol usan _safeGet.
    final results = await Future.wait([
      _dio.get<Map<String, dynamic>>('/v1/estadisticas/dashboard',
          queryParameters: {'meses': meses}),
      _dio.get<Map<String, dynamic>>('/v1/salidas', queryParameters: {
        'estado': 'PLANIFICADA',
        'size': 5,
        'sort': 'fechaInicio,asc',
      }),
      _dio.get<Map<String, dynamic>>('/v1/notificaciones/cumpleanos'),
      _safeGet('/v1/socios', queryParameters: const {'size': 100}),
      _safeGet('/v1/salidas/aprobaciones-pendientes'),
      _safeGet('/v1/salidas/alertas-sin-jefe'),
      if (socioId != null && socioId.isNotEmpty)
        _safeGet('/v1/estadisticas/socios/$socioId'),
    ]);

    final dashData =
        (results[0].data!['data'] as Map<String, dynamic>?) ?? {};
    final salidasData =
        (results[1].data!['data'] as Map<String, dynamic>?) ?? {};
    final cumplWrapper =
        (results[2].data!['data'] as Map<String, dynamic>?) ?? {};

    // ── Socios: conteo total + distribución por tipo ──────────────────────
    int totalSocios = 0;
    bool showSocios = false;
    final sociosPorTipo = <ChartDataPoint>[];
    final sociosRes = results[3];
    if (sociosRes.statusCode == 200) {
      final sociosData =
          (sociosRes.data?['data'] as Map<String, dynamic>?) ?? {};
      final meta = sociosData['page'] as Map<String, dynamic>? ?? sociosData;
      totalSocios = (meta['totalElements'] as num?)?.toInt() ?? 0;
      showSocios = true;
      final conteo = <String, int>{};
      for (final s in (sociosData['content'] as List<dynamic>? ?? [])) {
        final tipo = (s as Map<String, dynamic>)['tipoSocio'] as String?;
        if (tipo == null || tipo.isEmpty) continue;
        conteo[tipo] = (conteo[tipo] ?? 0) + 1;
      }
      conteo.forEach((tipo, count) {
        sociosPorTipo.add(ChartDataPoint(label: tipo, value: count.toDouble()));
      });
    }

    // ── Aprobaciones pendientes de riesgo ─────────────────────────────────
    final aprobaciones = <AprobacionPendiente>[];
    final aprobRes = results[4];
    if (aprobRes.statusCode == 200) {
      for (final e in (aprobRes.data?['data'] as List<dynamic>? ?? [])) {
        aprobaciones
            .add(AprobacionPendiente.fromJson(e as Map<String, dynamic>));
      }
    }

    // ── Salidas sin Jefe de Salida ────────────────────────────────────────
    final sinJefe = <SalidaSinJefe>[];
    final sinJefeRes = results[5];
    if (sinJefeRes.statusCode == 200) {
      for (final e in (sinJefeRes.data?['data'] as List<dynamic>? ?? [])) {
        sinJefe.add(SalidaSinJefe.fromJson(e as Map<String, dynamic>));
      }
    }

    // ── Próximas salidas donde el usuario es Jefe de Salida ───────────────
    final proximasComoJefe = <SalidaComoJefe>[];
    if (socioId != null && socioId.isNotEmpty && results.length > 6) {
      final histRes = results[6];
      if (histRes.statusCode == 200) {
        final hist =
            (histRes.data?['data'] as Map<String, dynamic>?) ?? {};
        final hoy = DateTime.now();
        final items = (hist['historial'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>()
            .where((h) {
          final esJefe = h['esJefeSalida'] as bool? ?? false;
          final estado = h['estadoInscripcion'] as String? ?? '';
          final fecha = DateTime.tryParse(h['fecha'] as String? ?? '');
          return esJefe &&
              estado != 'CANCELADO' &&
              fecha != null &&
              !fecha.isBefore(DateTime(hoy.year, hoy.month, hoy.day));
        }).toList()
          ..sort((a, b) =>
              (a['fecha'] as String).compareTo(b['fecha'] as String));
        for (final h in items) {
          proximasComoJefe.add(SalidaComoJefe(
            salidaId: h['salidaId']?.toString() ?? '',
            salidaNombre: h['salidaNombre'] as String? ?? '',
            montanaNombre: h['mountainNombre'] as String?,
            fecha: DateTime.tryParse(h['fecha'] as String? ?? ''),
          ));
        }
      }
    }

    final salidasContent = salidasData['content'] as List<dynamic>? ?? [];
    final cumplList = cumplWrapper['cumpleanos'] as List<dynamic>? ?? [];
    final salidasPorMes = (dashData['salidasPorMes'] as List<dynamic>? ?? [])
        .map((e) => SalidaMesPoint.fromJson(e as Map<String, dynamic>))
        .toList();

    return DashboardStats(
      kpis: DashboardKpis(
        totalSocios: totalSocios,
        showSocios: showSocios,
        salidasPlanificadas:
            (dashData['totalPlanificadas'] as num?)?.toInt() ?? 0,
        salidasRealizadas:
            (dashData['totalRealizadas'] as num?)?.toInt() ?? 0,
        totalSalidas: (dashData['totalSalidas'] as num?)?.toInt() ?? 0,
        totalEnCurso: (dashData['totalEnCurso'] as num?)?.toInt() ?? 0,
        totalCanceladas:
            (dashData['totalCanceladas'] as num?)?.toInt() ?? 0,
      ),
      proximasSalidas: salidasContent
          .map((s) => ProximaSalida.fromJson(s as Map<String, dynamic>))
          .toList(),
      cumpleanos: cumplList
          .map((c) => CumpleanosItem.fromJson(c as Map<String, dynamic>))
          .toList(),
      salidasPorMes: salidasPorMes,
      sociosPorTipo: sociosPorTipo,
      aprobacionesPendientes: aprobaciones,
      salidasSinJefe: sinJefe,
      proximasComoJefe: proximasComoJefe,
    );
  }

  Future<JefeAlertasData> getJefeAlertas({String? socioId}) async {
    final futures = <Future<Response<dynamic>>>[
      _safeGet('/v1/salidas/aprobaciones-pendientes'),
      _safeGet('/v1/salidas/alertas-sin-jefe'),
      if (socioId != null && socioId.isNotEmpty)
        _safeGet('/v1/estadisticas/socios/$socioId'),
    ];
    final results = await Future.wait(futures);

    final aprobaciones = <AprobacionPendiente>[];
    if (results[0].statusCode == 200) {
      for (final e in (results[0].data?['data'] as List<dynamic>? ?? [])) {
        aprobaciones
            .add(AprobacionPendiente.fromJson(e as Map<String, dynamic>));
      }
    }

    final sinJefe = <SalidaSinJefe>[];
    if (results[1].statusCode == 200) {
      for (final e in (results[1].data?['data'] as List<dynamic>? ?? [])) {
        sinJefe.add(SalidaSinJefe.fromJson(e as Map<String, dynamic>));
      }
    }

    final proximasComoJefe = <SalidaComoJefe>[];
    if (socioId != null && socioId.isNotEmpty && results.length > 2) {
      final histRes = results[2];
      if (histRes.statusCode == 200) {
        final hist =
            (histRes.data?['data'] as Map<String, dynamic>?) ?? {};
        final hoy = DateTime.now();
        final items = (hist['historial'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>()
            .where((h) {
              final esJefe = h['esJefeSalida'] as bool? ?? false;
              final estado = h['estadoInscripcion'] as String? ?? '';
              final fecha = DateTime.tryParse(h['fecha'] as String? ?? '');
              return esJefe &&
                  estado != 'CANCELADO' &&
                  fecha != null &&
                  !fecha.isBefore(DateTime(hoy.year, hoy.month, hoy.day));
            }).toList()
          ..sort((a, b) =>
              (a['fecha'] as String).compareTo(b['fecha'] as String));
        for (final h in items) {
          proximasComoJefe.add(SalidaComoJefe(
            salidaId: h['salidaId']?.toString() ?? '',
            salidaNombre: h['salidaNombre'] as String? ?? '',
            montanaNombre: h['mountainNombre'] as String?,
            fecha: DateTime.tryParse(h['fecha'] as String? ?? ''),
          ));
        }
      }
    }

    return JefeAlertasData(
      aprobacionesPendientes: aprobaciones,
      salidasSinJefe: sinJefe,
      proximasComoJefe: proximasComoJefe,
    );
  }

  /// Retorna una Response con statusCode != 200 ante error, para degradar
  /// sin tumbar el resto de la dashboard (ej. endpoints restringidos por rol).
  Future<Response<dynamic>> _safeGet(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) async {
    try {
      return await _dio.get<dynamic>(
        path,
        queryParameters: queryParameters,
      );
    } on DioException catch (e) {
      return Response<dynamic>(
        requestOptions: e.requestOptions,
        statusCode: e.response?.statusCode ?? 0,
      );
    }
  }
}
