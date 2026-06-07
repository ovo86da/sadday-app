import 'package:dio/dio.dart';
import '../domain/models/docs_legales_models.dart';

class DocsLegalesRemoteDataSource {
  const DocsLegalesRemoteDataSource(this._dio);
  final Dio _dio;

  // ── Public ──────────────────────────────────────────────────────────────────

  Future<LegalDoc> getActiveDocByCode(String code) async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/legal-documents/$code/active');
    return LegalDoc.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<LegalDoc> getDocById(String id) async {
    final res =
        await _dio.get<Map<String, dynamic>>('/v1/legal-documents/$id');
    return LegalDoc.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  // ── Socio (authenticated) ─────────────────────────────────────────────────

  Future<void> acceptDoc(String docId) =>
      _dio.post<void>('/v1/legal-documents/$docId/accept');

  Future<List<LegalDocAcceptance>> getMisAceptaciones() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/me/accepted-documents');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => LegalDocAcceptance.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<LegalDoc>> getDocsActivos() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/legal-documents',
        queryParameters: {'active': true},
    );
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => LegalDoc.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  // ── Emergency contacts ────────────────────────────────────────────────────

  Future<List<EmergencyContact>> getMisContactos() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/me/emergency-contacts');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => EmergencyContact.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<EmergencyContact>> upsertContactos(
      List<Map<String, dynamic>> contactos) async {
    final res = await _dio.put<Map<String, dynamic>>(
      '/v1/me/emergency-contacts',
      data: {'contactos': contactos},
    );
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => EmergencyContact.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  // ── Medical info ──────────────────────────────────────────────────────────

  Future<MedicalInfo?> getMiInfoMedica() async {
    final res =
        await _dio.get<Map<String, dynamic>>('/v1/me/medical-info');
    final data = res.data!['data'];
    if (data == null) return null;
    return MedicalInfo.fromJson(data as Map<String, dynamic>);
  }

  Future<MedicalInfo> updateInfoMedica(Map<String, dynamic> data) async {
    final res = await _dio.put<Map<String, dynamic>>(
        '/v1/me/medical-info', data: data);
    return MedicalInfo.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  // ── Profile completion ────────────────────────────────────────────────────

  Future<ProfileCompletionStatus> getProfileStatus() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/me/profile-completion-status');
    return ProfileCompletionStatus.fromJson(
        res.data!['data'] as Map<String, dynamic>);
  }

  // ── Admin: legal docs CRUD ────────────────────────────────────────────────

  Future<List<LegalDoc>> getAdminDocs() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/admin/legal-documents');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => LegalDoc.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<LegalDoc> createDoc(Map<String, dynamic> data) async {
    final res = await _dio.post<Map<String, dynamic>>(
        '/v1/admin/legal-documents', data: data);
    return LegalDoc.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<LegalDoc> createNewVersion(
      {required String docId, required String content}) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '/v1/admin/legal-documents/$docId/new-version',
      data: {'content': content},
    );
    return LegalDoc.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<LegalDoc> activateDoc(String docId) async {
    final res = await _dio.patch<Map<String, dynamic>>(
        '/v1/admin/legal-documents/$docId/activate');
    return LegalDoc.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<List<Map<String, dynamic>>> getDocAcceptances(String docId) async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/admin/legal-documents/$docId/acceptances');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data.cast<Map<String, dynamic>>();
  }

  Future<List<Map<String, dynamic>>> getPendingAcceptances() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/admin/legal-documents/pending-acceptances');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data.cast<Map<String, dynamic>>();
  }

  // ── Admin: retire socio ───────────────────────────────────────────────────

  Future<void> retireSocio(
      {required String socioId, required String reason}) async {
    await _dio.post<void>(
      '/v1/admin/socios/$socioId/retire',
      data: {'reason': reason},
    );
  }
}
