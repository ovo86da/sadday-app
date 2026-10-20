import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/ruta_model.dart';

class RutasRemoteDataSource {
  const RutasRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PagedResponse<Ruta>> getRutas({
    int page = 0,
    String? q,
    String? tipoActividad,
    String? nivelMinimoSocioId,
    int? mountainId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas',
        queryParameters: {
          'page': page,
          'size': 20,
          if (q != null && q.isNotEmpty) 'q': q,
          'tipoActividad': ?tipoActividad,
          'nivelMinimoSocioId': ?nivelMinimoSocioId,
          'mountainId': ?mountainId,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Ruta.fromJson);
  }

  Future<Ruta> getRutaDetail(int id) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas/$id');
    return Ruta.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  /// Rutas aprobadas de una montaña — para selección en salidas de Alpinismo.
  Future<List<Ruta>> getRutasByMountain(int mountainId) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas',
        queryParameters: {
          'mountainId': mountainId,
          'estado': 'APROBADA',
          'size': 500,
          'sort': 'nombre,asc',
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Ruta.fromJson).items;
  }

  /// Crea la ruta y devuelve su id.
  ///
  /// Devolvia `void`, y hacia falta el id para subirle los documentos justo
  /// despues: en el formulario el fichero se elige antes de que la ruta exista.
  Future<int> crearRuta(Map<String, dynamic> data) async {
    final res = await _dio.post<Map<String, dynamic>>('/v1/rutas', data: data);
    return (res.data!['data'] as Map<String, dynamic>)['id'] as int;
  }

  /// Sube un documento a una ruta. `reemplazarId` vacio = añadir uno nuevo.
  Future<void> subirDocumentoRuta(
    int rutaId,
    String filePath,
    String tipo, {
    String? reemplazarId,
  }) async {
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(filePath),
      'tipo': tipo,
      'reemplazarId': ?reemplazarId,
    });
    await _dio.post<void>('/v1/rutas/$rutaId/documentos', data: formData);
  }

  /// Descarga un documento y devuelve sus bytes.
  ///
  /// Pasa por dio y no por una URL abierta en el navegador del sistema: una
  /// navegacion no lleva la cabecera Authorization y el backend responde 401.
  /// Es el mismo fallo que estuvo oculto en la web desde el commit inicial.
  Future<List<int>> descargarDocumentoRuta(int rutaId, String docId) async {
    final res = await _dio.get<List<int>>(
      '/v1/rutas/$rutaId/documentos/$docId/descargar',
      options: Options(responseType: ResponseType.bytes),
    );
    return res.data ?? const [];
  }

  Future<void> eliminarDocumentoRuta(int rutaId, String docId) =>
      _dio.delete<void>('/v1/rutas/$rutaId/documentos/$docId');

  /// Confirma que el documento sirve. Devuelve el total de confirmaciones.
  Future<int> confirmarDocumentoRuta(int rutaId, String docId) async {
    final res = await _dio.post<Map<String, dynamic>>(
        '/v1/rutas/$rutaId/documentos/$docId/confirmar');
    return (res.data!['data'] as num).toInt();
  }

  Future<int> retirarConfirmacionRuta(int rutaId, String docId) async {
    final res = await _dio.delete<Map<String, dynamic>>(
        '/v1/rutas/$rutaId/documentos/$docId/confirmar');
    return (res.data!['data'] as num).toInt();
  }

  Future<void> aprobarRuta(int id) => _dio.patch<void>('/v1/rutas/$id/aprobar');

  Future<void> rechazarRuta(int id, String motivo) =>
      _dio.patch<void>('/v1/rutas/$id/rechazar', data: {'motivo': motivo});

  /// Rutas aprobadas filtradas por tipo de actividad
  /// (CICLISMO, ESCALADA, TREKKING).
  Future<List<Ruta>> getRutasByActividad(String tipoActividad) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas',
        queryParameters: {
          'tipoActividad': tipoActividad,
          'estado': 'APROBADA',
          'size': 500,
          'sort': 'nombre,asc',
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Ruta.fromJson).items;
  }
}
