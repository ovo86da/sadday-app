import { useState } from "react"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { toast } from "sonner"
import { Save, ShieldCheck, Info } from "lucide-react"
import { useAuthStore } from "@/stores/auth-store"
import { useAccesoPorNivel, useMountainLookups, useUpdateAccesoNivel } from "@/hooks/use-mountains"
import type { AccesoNivelResponse, MountainLookups, UpdateAccesoNivelRequest } from "@/types/mountains"

interface RowState {
  maxIfasId: string
  maxRocaId: string
  maxHieloId: string
  maxCompromisoId: string
  maxYosemiteId: string
  maxSaddayTecnicoId: string
  maxSaddayFisicoId: string
}

export function AccesoNivelPage() {
  const user = useAuthStore((s) => s.user)
  const userRole = user?.rol?.toUpperCase() ?? ""
  const canEdit = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)

  const { data: niveles, isLoading: loadingNiveles } = useAccesoPorNivel()
  const { data: lookups, isLoading: loadingLookups } = useMountainLookups()
  const updateMutation = useUpdateAccesoNivel()

  const [dirty, setDirty] = useState<Record<string, RowState>>({})
  const [saving, setSaving] = useState<string | null>(null)

  const isLoading = loadingNiveles || loadingLookups

  function getValue(nivel: AccesoNivelResponse, field: keyof RowState) {
    return dirty[nivel.nivelSocioId]?.[field] ?? nivel[field]
  }

  function handleChange(nivel: AccesoNivelResponse, field: keyof RowState, value: string) {
    setDirty((prev) => {
      const base: RowState = prev[nivel.nivelSocioId] ?? {
        maxIfasId: nivel.maxIfasId,
        maxRocaId: nivel.maxRocaId,
        maxHieloId: nivel.maxHieloId,
        maxCompromisoId: nivel.maxCompromisoId,
        maxYosemiteId: nivel.maxYosemiteId,
        maxSaddayTecnicoId: nivel.maxSaddayTecnicoId,
        maxSaddayFisicoId: nivel.maxSaddayFisicoId,
      }
      return { ...prev, [nivel.nivelSocioId]: { ...base, [field]: value } }
    })
  }

  async function handleSave(nivelSocioId: string) {
    const row = dirty[nivelSocioId]
    if (!row) return
    setSaving(nivelSocioId)
    try {
      const req: UpdateAccesoNivelRequest = {
        maxIfasId: row.maxIfasId,
        maxRocaId: row.maxRocaId,
        maxHieloId: row.maxHieloId,
        maxCompromisoId: row.maxCompromisoId,
        maxYosemiteId: row.maxYosemiteId,
        maxSaddayTecnicoId: row.maxSaddayTecnicoId,
        maxSaddayFisicoId: row.maxSaddayFisicoId,
      }
      await updateMutation.mutateAsync({ nivelSocioId, req })
      setDirty((prev) => {
        const next = { ...prev }
        delete next[nivelSocioId]
        return next
      })
      toast.success("Nivel de acceso actualizado")
    } catch (error) { console.error(error);
      toast.error("Error al actualizar nivel de acceso")
    } finally {
      setSaving(null)
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <ShieldCheck className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Niveles de acceso</h1>
          <p className="text-muted-foreground">
            {canEdit
              ? "Define la dificultad máxima permitida para cada tipo de socio."
              : "Consulta los niveles de dificultad permitidos por tipo de socio."}
          </p>
          <p className="text-xs text-amber-600 font-medium mt-0.5">
            Solo aplica para rutas de alpinismo.
          </p>
        </div>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-40 animate-pulse rounded-xl bg-muted" />
          ))}
        </div>
      ) : !niveles || !lookups ? (
        <div className="rounded-xl border border-border bg-card p-8 text-center">
          <p className="text-sm text-muted-foreground">No se pudieron cargar los datos</p>
        </div>
      ) : (
        <div className="space-y-4">
          {niveles.map((nivel) => {
            const isDirty = !!dirty[nivel.nivelSocioId]
            const isSaving = saving === nivel.nivelSocioId

            return (
              <div
                key={nivel.nivelSocioId}
                className={`rounded-xl border bg-card p-4 transition-colors ${isDirty ? "border-primary/50" : "border-border"}`}
              >
                {/* Row header */}
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Badge variant="secondary" className="text-sm font-semibold">
                      {nivel.nivelSocioNombre}
                    </Badge>
                    {isDirty && (
                      <span className="text-xs text-muted-foreground">Sin guardar</span>
                    )}
                  </div>
                  {canEdit && (
                    <Button
                      size="sm"
                      variant={isDirty ? "default" : "outline"}
                      disabled={!isDirty || isSaving}
                      onClick={() => handleSave(nivel.nivelSocioId)}
                      className="gap-1.5"
                    >
                      <Save className="h-3.5 w-3.5" />
                      {isSaving ? "Guardando..." : "Guardar"}
                    </Button>
                  )}
                </div>

                {/* Escalas */}
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {canEdit ? (
                    <>
                      <ScaleField label="IFAS (alpina)" tooltip={tooltipIfas(getValue(nivel, "maxIfasId"), lookups)}>
                        <Select value={getValue(nivel, "maxIfasId")} onValueChange={(v) => handleChange(nivel, "maxIfasId", v)}>
                          <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {lookups.escalasAlpina.map((e) => (
                              <SelectItem key={e.id} value={e.id}>{e.grado} — {e.nombre}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </ScaleField>
                      <ScaleField label="Roca (UIAA)" tooltip={tooltipRoca(getValue(nivel, "maxRocaId"), lookups)}>
                        <Select value={getValue(nivel, "maxRocaId")} onValueChange={(v) => handleChange(nivel, "maxRocaId", v)}>
                          <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {lookups.dificultadesRoca.map((r) => (
                              <SelectItem key={r.id} value={r.id}>{r.uiaa} ({r.francesa})</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </ScaleField>
                      <ScaleField label="Hielo" tooltip={tooltipHielo(getValue(nivel, "maxHieloId"), lookups)}>
                        <Select value={getValue(nivel, "maxHieloId")} onValueChange={(v) => handleChange(nivel, "maxHieloId", v)}>
                          <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {lookups.dificultadesHielo.map((h) => (
                              <SelectItem key={h.id} value={h.id}>{h.grado}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </ScaleField>
                      <ScaleField label="Compromiso" tooltip={tooltipCompromiso(getValue(nivel, "maxCompromisoId"), lookups)}>
                        <Select value={getValue(nivel, "maxCompromisoId")} onValueChange={(v) => handleChange(nivel, "maxCompromisoId", v)}>
                          <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {lookups.compromisos.map((c) => (
                              <SelectItem key={c.id} value={c.id}>{c.tipo}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </ScaleField>
                      <ScaleField label="Yosemite (YDS)" tooltip={tooltipYosemite(getValue(nivel, "maxYosemiteId"), lookups)}>
                        <Select value={getValue(nivel, "maxYosemiteId")} onValueChange={(v) => handleChange(nivel, "maxYosemiteId", v)}>
                          <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {lookups.yosemiteClases.map((y) => (
                              <SelectItem key={y.id} value={y.id}>{y.tipo}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </ScaleField>
                      <ScaleField label="Sadday Nivel Técnico" tooltip={tooltipSadday(getValue(nivel, "maxSaddayTecnicoId"), lookups)}>
                        <Select value={getValue(nivel, "maxSaddayTecnicoId")} onValueChange={(v) => handleChange(nivel, "maxSaddayTecnicoId", v)}>
                          <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {lookups.saddayRiesgos.map((s) => (
                              <SelectItem key={s.id} value={s.id}>{s.escala}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </ScaleField>
                      <ScaleField label="Sadday Nivel Físico" tooltip={tooltipSadday(getValue(nivel, "maxSaddayFisicoId"), lookups)}>
                        <Select value={getValue(nivel, "maxSaddayFisicoId")} onValueChange={(v) => handleChange(nivel, "maxSaddayFisicoId", v)}>
                          <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {lookups.saddayRiesgos.map((s) => (
                              <SelectItem key={s.id} value={s.id}>{s.escala}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </ScaleField>
                    </>
                  ) : (
                    <>
                      <InfoBadge label="IFAS (alpina)" tooltip={tooltipIfas(nivel.maxIfasId, lookups)} value={nivel.maxIfasGrado} />
                      <InfoBadge label="Roca (UIAA)" tooltip={tooltipRoca(nivel.maxRocaId, lookups)} value={nivel.maxRocaUiaa} />
                      <InfoBadge label="Hielo" tooltip={tooltipHielo(nivel.maxHieloId, lookups)} value={nivel.maxHieloGrado} />
                      <InfoBadge label="Compromiso" tooltip={tooltipCompromiso(nivel.maxCompromisoId, lookups)} value={nivel.maxCompromisoTipo} />
                      <InfoBadge label="Yosemite (YDS)" tooltip={tooltipYosemite(nivel.maxYosemiteId, lookups)} value={nivel.maxYosemiteTipo} />
                      <InfoBadge label="Sadday Nivel Técnico" tooltip={tooltipSadday(nivel.maxSaddayTecnicoId, lookups)} value={nivel.maxSaddayTecnicoEscala} />
                      <InfoBadge label="Sadday Nivel Físico" tooltip={tooltipSadday(nivel.maxSaddayFisicoId, lookups)} value={nivel.maxSaddayFisicoEscala} />
                    </>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ─── Tooltip text helpers ─────────────────────────────────────────────────────

function tooltipIfas(id: string, l: MountainLookups) {
  const item = l.escalasAlpina.find((e) => e.id === id)
  return item ? `${item.grado} — ${item.nombre}` : ""
}
function tooltipRoca(id: string, l: MountainLookups) {
  const item = l.dificultadesRoca.find((r) => r.id === id)
  return item ? `${item.uiaa} (francesa: ${item.francesa})` : ""
}
function tooltipHielo(id: string, l: MountainLookups) {
  const item = l.dificultadesHielo.find((h) => h.id === id)
  return item ? item.grado : ""
}
function tooltipCompromiso(id: string, l: MountainLookups) {
  const item = l.compromisos.find((c) => c.id === id)
  return item ? item.tipo : ""
}
function tooltipYosemite(id: string, l: MountainLookups) {
  const item = l.yosemiteClases.find((y) => y.id === id)
  return item ? item.tipo : ""
}
function tooltipSadday(id: string, l: MountainLookups) {
  const item = l.saddayRiesgos.find((s) => s.id === id)
  return item ? `${item.escala} (nivel ${item.valor})` : ""
}

// ─── UI helpers ───────────────────────────────────────────────────────────────

function LabelWithTooltip({ label, tooltip }: { label: string; tooltip: string }) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="inline-flex cursor-default items-center gap-1 text-xs font-medium text-muted-foreground">
            {label}
            <Info className="h-3 w-3 opacity-50" />
          </span>
        </TooltipTrigger>
        {tooltip && (
          <TooltipContent side="top">
            {tooltip}
          </TooltipContent>
        )}
      </Tooltip>
    </TooltipProvider>
  )
}

function ScaleField({ label, tooltip, children }: { label: string; tooltip: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <LabelWithTooltip label={label} tooltip={tooltip} />
      {children}
    </div>
  )
}

function InfoBadge({ label, tooltip, value }: { label: string; tooltip: string; value: string }) {
  return (
    <div className="space-y-1">
      <LabelWithTooltip label={label} tooltip={tooltip} />
      <Badge variant="outline" className="text-sm">{value}</Badge>
    </div>
  )
}
