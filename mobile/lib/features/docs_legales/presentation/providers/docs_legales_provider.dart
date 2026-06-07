import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/docs_legales_remote_data_source.dart';
import '../../data/docs_legales_repository.dart';
import '../../domain/models/docs_legales_models.dart';

final docsLegalesRepositoryProvider = Provider<DocsLegalesRepository>((ref) {
  return DocsLegalesRepository(
    DocsLegalesRemoteDataSource(ref.watch(dioClientProvider)),
  );
});

// ── Socio providers ───────────────────────────────────────────────────────────

final misContactosProvider =
    FutureProvider.autoDispose<List<EmergencyContact>>((ref) {
  return ref.watch(docsLegalesRepositoryProvider).getMisContactos();
});

final miInfoMedicaProvider =
    FutureProvider.autoDispose<MedicalInfo?>((ref) {
  return ref.watch(docsLegalesRepositoryProvider).getMiInfoMedica();
});

final profileStatusProvider =
    FutureProvider.autoDispose<ProfileCompletionStatus>((ref) {
  return ref.watch(docsLegalesRepositoryProvider).getProfileStatus();
});

final misAceptacionesProvider =
    FutureProvider.autoDispose<List<LegalDocAcceptance>>((ref) {
  return ref.watch(docsLegalesRepositoryProvider).getMisAceptaciones();
});

// ── Admin providers ───────────────────────────────────────────────────────────

final adminDocsProvider =
    FutureProvider.autoDispose<List<LegalDoc>>((ref) {
  return ref.watch(docsLegalesRepositoryProvider).getAdminDocs();
});

final docAcceptancesProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, docId) {
  return ref.watch(docsLegalesRepositoryProvider).getDocAcceptances(docId);
});

final pendingAcceptancesProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) {
  return ref.watch(docsLegalesRepositoryProvider).getPendingAcceptances();
});
