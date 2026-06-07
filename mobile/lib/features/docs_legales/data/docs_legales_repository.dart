import '../domain/models/docs_legales_models.dart';
import 'docs_legales_remote_data_source.dart';

class DocsLegalesRepository {
  const DocsLegalesRepository(this._ds);
  final DocsLegalesRemoteDataSource _ds;

  Future<LegalDoc> getActiveDocByCode(String code) =>
      _ds.getActiveDocByCode(code);
  Future<LegalDoc> getDocById(String id) => _ds.getDocById(id);
  Future<void> acceptDoc(String docId) => _ds.acceptDoc(docId);
  Future<List<LegalDocAcceptance>> getMisAceptaciones() =>
      _ds.getMisAceptaciones();
  Future<List<LegalDoc>> getDocsActivos() => _ds.getDocsActivos();

  Future<List<EmergencyContact>> getMisContactos() => _ds.getMisContactos();
  Future<List<EmergencyContact>> upsertContactos(
          List<Map<String, dynamic>> contactos) =>
      _ds.upsertContactos(contactos);

  Future<MedicalInfo?> getMiInfoMedica() => _ds.getMiInfoMedica();
  Future<MedicalInfo> updateInfoMedica(Map<String, dynamic> data) =>
      _ds.updateInfoMedica(data);

  Future<ProfileCompletionStatus> getProfileStatus() =>
      _ds.getProfileStatus();

  Future<List<LegalDoc>> getAdminDocs() => _ds.getAdminDocs();
  Future<LegalDoc> createDoc(Map<String, dynamic> data) =>
      _ds.createDoc(data);
  Future<LegalDoc> createNewVersion(
          {required String docId, required String content}) =>
      _ds.createNewVersion(docId: docId, content: content);
  Future<LegalDoc> activateDoc(String docId) => _ds.activateDoc(docId);
  Future<List<Map<String, dynamic>>> getDocAcceptances(String docId) =>
      _ds.getDocAcceptances(docId);
  Future<List<Map<String, dynamic>>> getPendingAcceptances() =>
      _ds.getPendingAcceptances();

  Future<void> retireSocio(
          {required String socioId, required String reason}) =>
      _ds.retireSocio(socioId: socioId, reason: reason);
}
