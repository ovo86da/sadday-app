import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/socios_remote_data_source.dart';
import '../../data/socios_repository.dart';
import '../../domain/models/socio_model.dart';

final sociosRepositoryProvider = Provider<SociosRepository>((ref) {
  return SociosRepository(SociosRemoteDataSource(ref.watch(dioClientProvider)));
});

final socioDetailProvider =
    FutureProvider.autoDispose.family<SocioDetalle, String>((ref, id) {
  return ref.watch(sociosRepositoryProvider).getSocioDetail(id);
});

final invitacionesProvider =
    FutureProvider.autoDispose<List<Invitacion>>((ref) {
  return ref.watch(sociosRepositoryProvider).getInvitaciones();
});

final sociosLookupsProvider = FutureProvider<SociosLookups>((ref) {
  return ref.watch(sociosRepositoryProvider).getLookups();
});
