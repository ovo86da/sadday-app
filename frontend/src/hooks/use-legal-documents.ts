import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"

export interface LegalDoc {
  id: string
  code: string
  title: string
  description: string
  documentType: string
  requiredStage: string
  version: number
  content: string
  required: boolean
  active: boolean
  requiresReacceptanceOnNewVersion: boolean
  approvedAt: string | null
  approvedByName: string | null
  createdAt: string
  updatedAt: string
}

export interface LegalAcceptance {
  id: string
  documentId: string
  documentCode: string
  documentTitle: string
  documentVersion: number
  acceptedAt: string
  accepted: boolean
}

export function useActiveDocuments(stage?: string, enabled = true) {
  return useQuery({
    queryKey: ["legal-documents", "active", stage ?? "all"],
    queryFn: () =>
      api
        .get<{ data: LegalDoc[] }>(
          "/v1/legal-documents/active" + (stage ? `?stage=${stage}` : ""),
        )
        .then((r) => r.data.data),
    staleTime: 10 * 60 * 1000,
    enabled,
  })
}

export function useMyAcceptances(enabled = true) {
  return useQuery({
    queryKey: ["legal-acceptances", "me"],
    queryFn: () =>
      api.get<{ data: LegalAcceptance[] }>("/v1/me/legal-acceptances").then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
    enabled,
  })
}

export function useAcceptDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (documentId: string) =>
      api
        .post<{ data: LegalAcceptance }>(`/v1/legal-documents/${documentId}/accept`)
        .then((r) => r.data.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["legal-acceptances", "me"] })
    },
  })
}

export function usePendingRequiredDocs(stage?: string, enabled = true) {
  const { data: docs, isLoading: loadingDocs } = useActiveDocuments(stage, enabled)
  const { data: acceptances, isLoading: loadingAcceptances } = useMyAcceptances(enabled)

  const acceptedIds = new Set((acceptances ?? []).map((a) => a.documentId))
  const pending = (docs ?? []).filter((d) => d.required && !acceptedIds.has(d.id))

  return {
    pending,
    isLoading: loadingDocs || loadingAcceptances,
  }
}
