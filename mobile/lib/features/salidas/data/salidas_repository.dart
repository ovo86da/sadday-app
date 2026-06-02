import '../../../../core/api/paged_response.dart';
import '../domain/models/salida_model.dart';
import 'salidas_remote_data_source.dart';

class SalidasRepository {
  const SalidasRepository(this._ds);
  final SalidasRemoteDataSource _ds;

  Future<PagedResponse<Salida>> getSalidas({
    int page = 0,
    String? estado,
    String? q,
    String? tipoActividad,
    String? nivelMinimoId,
    int? montanaId,
    int? rutaId,
    String? fechaInicio,
    String? fechaFin,
  }) =>
      _ds.getSalidas(
        page: page,
        estado: estado,
        q: q,
        tipoActividad: tipoActividad,
        nivelMinimoId: nivelMinimoId,
        montanaId: montanaId,
        rutaId: rutaId,
        fechaInicio: fechaInicio,
        fechaFin: fechaFin,
      );

  Future<SalidaDetalle> getSalidaDetail(String id) => _ds.getSalidaDetail(id);

  Future<void> inscribirse(String salidaId, {String? socioId}) =>
      _ds.inscribirse(salidaId, socioId: socioId);

  Future<void> cancelarInscripcion(String salidaId, int inscripcionId) =>
      _ds.cancelarInscripcion(salidaId, inscripcionId);

  Future<void> cambiarEstado(String salidaId, String estado) =>
      _ds.cambiarEstado(salidaId, estado);

  Future<SalidaLookups> getLookups() => _ds.getLookups();

  Future<List<Solapamiento>> verificarSolapamiento({
    required String fechaInicio,
    required String fechaFin,
    String? excludeId,
  }) =>
      _ds.verificarSolapamiento(
          fechaInicio: fechaInicio,
          fechaFin: fechaFin,
          excludeId: excludeId);

  Future<void> crearSalida(Map<String, dynamic> data) =>
      _ds.crearSalida(data);

  Future<void> editarSalida(String id, Map<String, dynamic> data) =>
      _ds.editarSalida(id, data);

  Future<void> cancelarSalida(String id, String motivo) =>
      _ds.cancelarSalida(id, motivo);

  Future<void> eliminarSalida(String id, String motivo) =>
      _ds.eliminarSalida(id, motivo);

  Future<void> decidirRiesgo(
          String salidaId, int participanteId, bool aprobar, String motivo) =>
      _ds.decidirRiesgo(salidaId, participanteId, aprobar, motivo);

  Future<void> revocarAprobacion(String salidaId, int participanteId) =>
      _ds.revocarAprobacion(salidaId, participanteId);

  Future<bool> toggleCerrarInscripciones(String salidaId) =>
      _ds.toggleCerrarInscripciones(salidaId);

  Future<void> designarJefeSalida(String salidaId, int participanteId) =>
      _ds.designarJefeSalida(salidaId, participanteId);

  Future<void> agregarDignidad(
          String salidaId, int participanteId, int dignidadId) =>
      _ds.agregarDignidad(salidaId, participanteId, dignidadId);

  Future<void> eliminarDignidad(
          String salidaId, int participanteId, int asignadaId) =>
      _ds.eliminarDignidad(salidaId, participanteId, asignadaId);
}
