import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../data/dashboard_remote_data_source.dart';
import '../../data/dashboard_repository.dart';
import '../../domain/models/dashboard_models.dart';

final dashboardRepositoryProvider = Provider<DashboardRepository>((ref) {
  return DashboardRepository(
      DashboardRemoteDataSource(ref.watch(dioClientProvider)));
});

final dashboardProvider =
    FutureProvider.autoDispose.family<DashboardStats, int>((ref, meses) {
  final auth = ref.watch(authNotifierProvider).asData?.value;
  final socioId = auth is AuthAuthenticated ? auth.user.socioId : null;
  return ref
      .watch(dashboardRepositoryProvider)
      .getDashboard(meses: meses, socioId: socioId);
});
