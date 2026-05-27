import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../../montanas/domain/models/montana_model.dart';
import '../../../montanas/presentation/providers/montanas_provider.dart';
import '../../../rutas/domain/models/ruta_model.dart';
import '../../../rutas/presentation/providers/rutas_provider.dart';
import '../../../socios/domain/models/socio_model.dart';
import '../../../socios/presentation/providers/socios_provider.dart';
import '../../data/salidas_remote_data_source.dart';
import '../../data/salidas_repository.dart';
import '../../domain/models/salida_model.dart';

final salidasRepositoryProvider = Provider<SalidasRepository>((ref) {
  return SalidasRepository(
      SalidasRemoteDataSource(ref.watch(dioClientProvider)));
});

final salidaDetailProvider =
    FutureProvider.autoDispose.family<SalidaDetalle, String>((ref, id) {
  return ref.watch(salidasRepositoryProvider).getSalidaDetail(id);
});

// ── Providers de soporte para el formulario de salida ─────────────────────

/// Catálogos de salida (público objetivo, formato).
final salidaLookupsProvider =
    FutureProvider.autoDispose<SalidaLookups>((ref) {
  return ref.watch(salidasRepositoryProvider).getLookups();
});

/// Clasificaciones / niveles técnicos — para "nivel mínimo".
final clasificacionesProvider =
    FutureProvider.autoDispose<List<Clasificacion>>((ref) async {
  final lookups = await ref.watch(sociosRepositoryProvider).getLookups();
  return lookups.clasificaciones;
});

/// Lista completa de montañas para el selector de Alpinismo.
final allMontanasProvider = FutureProvider.autoDispose<List<Montana>>((ref) {
  return ref.watch(montanasRepositoryProvider).getAllMontanas();
});

/// Rutas aprobadas de una montaña.
final rutasByMontanaProvider =
    FutureProvider.autoDispose.family<List<Ruta>, int>((ref, mountainId) {
  return ref.watch(rutasRepositoryProvider).getRutasByMountain(mountainId);
});

/// Rutas aprobadas por tipo de actividad (no Alpinismo).
final rutasByActividadProvider =
    FutureProvider.autoDispose.family<List<Ruta>, String>((ref, tipo) {
  return ref.watch(rutasRepositoryProvider).getRutasByActividad(tipo);
});
