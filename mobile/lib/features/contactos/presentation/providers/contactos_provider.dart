import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/contactos_remote_data_source.dart';
import '../../data/contactos_repository.dart';

final contactosRepositoryProvider = Provider<ContactosRepository>((ref) {
  return ContactosRepository(
      ContactosRemoteDataSource(ref.watch(dioClientProvider)));
});
