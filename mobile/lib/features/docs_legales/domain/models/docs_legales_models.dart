class LegalDoc {
  const LegalDoc({
    required this.id,
    required this.code,
    required this.title,
    required this.documentType,
    required this.requiredStage,
    required this.version,
    required this.active,
    required this.required,
    this.content,
    this.approvedAt,
  });

  final String id;
  final String code;
  final String title;
  final String documentType;
  final String requiredStage;
  final int version;
  final bool active;
  final bool required;
  final String? content;
  final DateTime? approvedAt;

  factory LegalDoc.fromJson(Map<String, dynamic> j) => LegalDoc(
        id: j['id'] as String,
        code: j['code'] as String,
        title: j['title'] as String,
        documentType: j['documentType'] as String,
        requiredStage: j['requiredStage'] as String,
        version: (j['version'] as num).toInt(),
        active: j['active'] as bool? ?? false,
        required: j['required'] as bool? ?? false,
        content: j['content'] as String?,
        approvedAt: j['approvedAt'] != null
            ? DateTime.tryParse(j['approvedAt'] as String)
            : null,
      );
}

class LegalDocAcceptance {
  const LegalDocAcceptance({
    required this.id,
    required this.documentCode,
    required this.documentTitle,
    required this.documentVersion,
    required this.acceptedAt,
  });

  final String id;
  final String documentCode;
  final String documentTitle;
  final int documentVersion;
  final DateTime acceptedAt;

  factory LegalDocAcceptance.fromJson(Map<String, dynamic> j) =>
      LegalDocAcceptance(
        id: j['id'] as String,
        documentCode: j['documentCode'] as String,
        documentTitle: j['documentTitle'] as String,
        documentVersion: (j['documentVersion'] as num).toInt(),
        acceptedAt: DateTime.parse(j['acceptedAt'] as String),
      );
}

class EmergencyContact {
  const EmergencyContact({
    required this.id,
    required this.orden,
    required this.nombreCompleto,
    required this.relacion,
    required this.celular,
    this.direccion,
  });

  final String id;
  final int orden;
  final String nombreCompleto;
  final String relacion;
  final String celular;
  final String? direccion;

  factory EmergencyContact.fromJson(Map<String, dynamic> j) => EmergencyContact(
        id: j['id'] as String,
        orden: (j['orden'] as num).toInt(),
        nombreCompleto: j['nombreCompleto'] as String,
        relacion: j['relacion'] as String,
        celular: j['celular'] as String? ?? '',
        direccion: j['direccion'] as String?,
      );
}

class MedicalInfo {
  const MedicalInfo({
    this.id,
    this.bloodType,
    required this.hasRelevantAllergies,
    this.allergiesDetail,
    required this.hasRelevantMedicalCondition,
    this.medicalConditionDetail,
    required this.usesEmergencyMedication,
    this.emergencyMedicationDetail,
    this.additionalNotes,
    this.updatedAt,
  });

  final String? id;
  final String? bloodType;
  final bool hasRelevantAllergies;
  final String? allergiesDetail;
  final bool hasRelevantMedicalCondition;
  final String? medicalConditionDetail;
  final bool usesEmergencyMedication;
  final String? emergencyMedicationDetail;
  final String? additionalNotes;
  final DateTime? updatedAt;

  factory MedicalInfo.fromJson(Map<String, dynamic> j) => MedicalInfo(
        id: j['id'] as String?,
        bloodType: j['bloodType'] as String?,
        hasRelevantAllergies: j['hasRelevantAllergies'] as bool? ?? false,
        allergiesDetail: j['allergiesDetail'] as String?,
        hasRelevantMedicalCondition:
            j['hasRelevantMedicalCondition'] as bool? ?? false,
        medicalConditionDetail: j['medicalConditionDetail'] as String?,
        usesEmergencyMedication: j['usesEmergencyMedication'] as bool? ?? false,
        emergencyMedicationDetail: j['emergencyMedicationDetail'] as String?,
        additionalNotes: j['additionalNotes'] as String?,
        updatedAt: j['updatedAt'] != null
            ? DateTime.tryParse(j['updatedAt'] as String)
            : null,
      );

  Map<String, dynamic> toJson() => {
        if (bloodType != null) 'bloodType': bloodType,
        'hasRelevantAllergies': hasRelevantAllergies,
        if (allergiesDetail != null) 'allergiesDetail': allergiesDetail,
        'hasRelevantMedicalCondition': hasRelevantMedicalCondition,
        if (medicalConditionDetail != null)
          'medicalConditionDetail': medicalConditionDetail,
        'usesEmergencyMedication': usesEmergencyMedication,
        if (emergencyMedicationDetail != null)
          'emergencyMedicationDetail': emergencyMedicationDetail,
        if (additionalNotes != null) 'additionalNotes': additionalNotes,
      };
}

class ProfileCompletionStatus {
  const ProfileCompletionStatus({
    required this.profileComplete,
    required this.canEnrollActivities,
    required this.missingRequirements,
    required this.pendingDocuments,
    required this.expiredDocuments,
  });

  final bool profileComplete;
  final bool canEnrollActivities;
  final List<String> missingRequirements;
  final List<String> pendingDocuments;
  final List<String> expiredDocuments;

  factory ProfileCompletionStatus.fromJson(Map<String, dynamic> j) =>
      ProfileCompletionStatus(
        profileComplete: j['profileComplete'] as bool? ?? false,
        canEnrollActivities: j['canEnrollActivities'] as bool? ?? true,
        missingRequirements: (j['missingRequirements'] as List?)
                ?.map((e) => e as String)
                .toList() ??
            const [],
        pendingDocuments: (j['pendingDocuments'] as List?)
                ?.map((e) => e as String)
                .toList() ??
            const [],
        expiredDocuments: (j['expiredDocuments'] as List?)
                ?.map((e) => e as String)
                .toList() ??
            const [],
      );
}
