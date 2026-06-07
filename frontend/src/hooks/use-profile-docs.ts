import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"

const EC_KEY = "emergency-contacts"
const MI_KEY = "medical-info"
const PC_KEY = "profile-completion"

export interface EmergencyContact {
  id: string
  orden: number
  nombreCompleto: string
  relacion: string
  celular: string
  direccion: string
}

export interface EmergencyContactInput {
  orden: number
  nombreCompleto: string
  relacion: string
  celular?: string
  direccion?: string
}

export interface MedicalInfo {
  id: string | null
  bloodType: string | null
  hasRelevantAllergies: boolean
  allergiesDetail: string | null
  hasRelevantMedicalCondition: boolean
  medicalConditionDetail: string | null
  usesEmergencyMedication: boolean
  emergencyMedicationDetail: string | null
  additionalNotes: string | null
  updatedAt: string | null
}

export interface MedicalInfoUpdate {
  bloodType?: string
  hasRelevantAllergies?: boolean
  allergiesDetail?: string
  hasRelevantMedicalCondition?: boolean
  medicalConditionDetail?: string
  usesEmergencyMedication?: boolean
  emergencyMedicationDetail?: string
  additionalNotes?: string
}

export interface ProfileCompletionStatus {
  profileComplete: boolean
  canEnrollActivities: boolean
  missingRequirements: string[]
  pendingDocuments: string[]
  expiredDocuments: string[]
}

export interface MedicalSummary {
  socioId: string
  nombreCompleto: string
  bloodType: string | null
  hasRelevantAllergies: boolean
  allergiesDetail: string | null
  usesEmergencyMedication: boolean
  emergencyMedicationDetail: string | null
}

// ─── Emergency Contacts ───────────────────────────────────────────────────────

export function useMyEmergencyContacts() {
  return useQuery({
    queryKey: [EC_KEY, "me"],
    queryFn: () =>
      api.get<{ data: EmergencyContact[] }>("/v1/me/emergency-contacts").then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
  })
}

export function useUpsertEmergencyContacts() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (contactos: EmergencyContactInput[]) =>
      api
        .put<{ data: EmergencyContact[] }>("/v1/me/emergency-contacts", { contactos })
        .then((r) => r.data.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [EC_KEY, "me"] })
      qc.invalidateQueries({ queryKey: [PC_KEY, "me"] })
    },
  })
}

// ─── Medical Info ─────────────────────────────────────────────────────────────

export function useMyMedicalInfo() {
  return useQuery({
    queryKey: [MI_KEY, "me"],
    queryFn: () =>
      api.get<{ data: MedicalInfo | null }>("/v1/me/medical-info").then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
  })
}

export function useUpdateMedicalInfo() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: MedicalInfoUpdate) =>
      api.put<{ data: MedicalInfo }>("/v1/me/medical-info", req).then((r) => r.data.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [MI_KEY, "me"] })
      qc.invalidateQueries({ queryKey: [PC_KEY, "me"] })
    },
  })
}

// ─── Profile Completion Status ────────────────────────────────────────────────

export function useProfileCompletionStatus(enabled = true) {
  return useQuery({
    queryKey: [PC_KEY, "me"],
    queryFn: () =>
      api
        .get<{ data: ProfileCompletionStatus }>("/v1/me/profile-completion-status")
        .then((r) => r.data.data),
    staleTime: 2 * 60 * 1000,
    enabled,
  })
}

// ─── Medical Summary (admin/directivo) ───────────────────────────────────────

export function useMedicalSummary(socioId: string | null) {
  return useQuery({
    queryKey: [MI_KEY, "summary", socioId],
    queryFn: () =>
      api
        .get<{ data: MedicalSummary }>(`/v1/admin/socios/${socioId}/medical-summary`)
        .then((r) => r.data.data),
    enabled: !!socioId,
    staleTime: 5 * 60 * 1000,
  })
}
