import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../data/notificaciones_remote_data_source.dart';
import '../../data/notificaciones_repository.dart';
import '../../domain/models/notificacion_models.dart';

final notificacionesRepositoryProvider =
    Provider<NotificacionesRepository>((ref) {
  return NotificacionesRepository(
      NotificacionesRemoteDataSource(ref.watch(dioClientProvider)));
});

final aprobacionesPendientesProvider =
    FutureProvider.autoDispose<List<AprobacionPendiente>>((ref) {
  return ref
      .watch(notificacionesRepositoryProvider)
      .getAprobacionesPendientes();
});

final alertasSinJefeProvider =
    FutureProvider.autoDispose<List<AlertaSinJefe>>((ref) {
  final authVal = ref.watch(authNotifierProvider).asData?.value;
  final role = authVal is AuthAuthenticated ? authVal.user.rol : null;
  final isPrivilegiado = role == UserRole.admin ||
      role == UserRole.secretaria ||
      role == UserRole.directivo;
  if (!isPrivilegiado) return [];
  return ref.watch(notificacionesRepositoryProvider).getAlertasSinJefe();
});
