import { useState } from "react"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from "@/components/ui/dialog"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { toast } from "sonner"
import { Save, ShieldCheck } from "lucide-react"
import { useAccesoPorNivel, useMountainLookups, useUpdateAccesoNivel } from "@/hooks/use-mountains"
import type { AccesoNivelResponse, UpdateAccesoNivelRequest } from "@/types/mountains"

interface Props {
  open: boolean
  onClose: () => void
}

interface RowState {
  maxIfasId: string
  maxRocaId: string
  maxHieloId: string
  maxCompromisoId: string
  maxYosemiteId: string
  maxSaddayTecnicoId: string
  maxSaddayFisicoId: string
}

export function AccesoNivelDialog({ open, onClose }: Props) {
  const { data: niveles, isLoading: loadingNiveles } = useAccesoPorNivel()
  const { data: lookups, isLoading: loadingLookups } = useMountainLookups()
  const updateMutation = useUpdateAccesoNivel()

  // Track dirty (modified) state per nivelSocioId
  const [dirty, setDirty] = useState<Record<string, RowState>>({})
  const [saving, setSaving] = useState<string | null>(null)

  const isLoading = loadingNiveles || loadingLookups

  function getRowState(nivelSocioId: string): RowState | undefined {
    return dirty[nivelSocioId]
  }

  function getValue(nivel: { nivelSocioId: string; maxIfasId: string; maxRocaId: string; maxHieloId: string; maxCompromisoId: string; maxYosemiteId: string; maxSaddayTecnicoId: string; maxSaddayFisicoId: string }, field: keyof RowState) {
    return dirty[nivel.nivelSocioId]?.[field] ?? nivel[field]
  }

  function handleChange(nivelSocioId: string, nivel: AccesoNivelResponse, field: keyof RowState, value: string) {
    setDirty((prev) => {
      const base: RowState = prev[nivelSocioId] ?? {
        maxIfasId: nivel.maxIfasId,
        maxRocaId: nivel.maxRocaId,
        maxHieloId: nivel.maxHieloId,
        maxCompromisoId: nivel.maxCompromisoId,
        maxYosemiteId: nivel.maxYosemiteId,
        maxSaddayTecnicoId: nivel.maxSaddayTecnicoId,
        maxSaddayFisicoId: nivel.maxSaddayFisicoId,
      }
      return { ...prev, [nivelSocioId]: { ...base, [field]: value } }
    })
  }

  async function handleSave(nivelSocioId: string) {
    const row = getRowState(nivelSocioId)
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
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-5xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-primary" />
            Niveles de acceso por tipo de socio
          </DialogTitle>
          <DialogDescription>
            Define la dificultad máxima permitida para cada tipo de socio en cada escala de clasificación.
            <span className="block text-amber-600 font-medium mt-0.5">Solo aplica para rutas de alpinismo.</span>
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-16 animate-pulse rounded-xl bg-muted" />
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
                  <div className="mb-3 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Badge variant="secondary" className="text-sm font-semibold">
                        {nivel.nivelSocioNombre}
                      </Badge>
                      {isDirty && (
                        <span className="text-xs text-muted-foreground">Sin guardar</span>
                      )}
                    </div>
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
                  </div>

                  {/* Selects grid */}
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                    {/* IFAS */}
                    <div className="space-y-1">
                      <label className="text-xs font-medium text-muted-foreground">IFAS (alpina)</label>
                      <Select
                        value={getValue(nivel, "maxIfasId")}
                        onValueChange={(v) => handleChange(nivel.nivelSocioId, nivel, "maxIfasId", v)}
                      >
                        <SelectTrigger className="h-8 text-sm">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {lookups.escalasAlpina.map((e) => (
                            <SelectItem key={e.id} value={e.id}>
                              {e.grado} — {e.nombre}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    {/* Roca */}
                    <div className="space-y-1">
                      <label className="text-xs font-medium text-muted-foreground">Roca (UIAA)</label>
                      <Select
                        value={getValue(nivel, "maxRocaId")}
                        onValueChange={(v) => handleChange(nivel.nivelSocioId, nivel, "maxRocaId", v)}
                      >
                        <SelectTrigger className="h-8 text-sm">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {lookups.dificultadesRoca.map((r) => (
                            <SelectItem key={r.id} value={r.id}>
                              {r.uiaa} ({r.francesa})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    {/* Hielo */}
                    <div className="space-y-1">
                      <label className="text-xs font-medium text-muted-foreground">Hielo</label>
                      <Select
                        value={getValue(nivel, "maxHieloId")}
                        onValueChange={(v) => handleChange(nivel.nivelSocioId, nivel, "maxHieloId", v)}
                      >
                        <SelectTrigger className="h-8 text-sm">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {lookups.dificultadesHielo.map((h) => (
                            <SelectItem key={h.id} value={h.id}>
                              {h.grado}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    {/* Compromiso */}
                    <div className="space-y-1">
                      <label className="text-xs font-medium text-muted-foreground">Compromiso</label>
                      <Select
                        value={getValue(nivel, "maxCompromisoId")}
                        onValueChange={(v) => handleChange(nivel.nivelSocioId, nivel, "maxCompromisoId", v)}
                      >
                        <SelectTrigger className="h-8 text-sm">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {lookups.compromisos.map((c) => (
                            <SelectItem key={c.id} value={c.id}>
                              {c.tipo}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    {/* Yosemite */}
                    <div className="space-y-1">
                      <label className="text-xs font-medium text-muted-foreground">Yosemite</label>
                      <Select
                        value={getValue(nivel, "maxYosemiteId")}
                        onValueChange={(v) => handleChange(nivel.nivelSocioId, nivel, "maxYosemiteId", v)}
                      >
                        <SelectTrigger className="h-8 text-sm">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {lookups.yosemiteClases.map((y) => (
                            <SelectItem key={y.id} value={y.id}>
                              {y.tipo}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    {/* Sadday Nivel Técnico */}
                    <div className="space-y-1">
                      <label className="text-xs font-medium text-muted-foreground">Sadday Nivel Técnico</label>
                      <Select
                        value={getValue(nivel, "maxSaddayTecnicoId")}
                        onValueChange={(v) => handleChange(nivel.nivelSocioId, nivel, "maxSaddayTecnicoId", v)}
                      >
                        <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {lookups.saddayRiesgos.map((s) => (
                            <SelectItem key={s.id} value={s.id}>{s.escala}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    {/* Sadday Nivel Físico */}
                    <div className="space-y-1">
                      <label className="text-xs font-medium text-muted-foreground">Sadday Nivel Físico</label>
                      <Select
                        value={getValue(nivel, "maxSaddayFisicoId")}
                        onValueChange={(v) => handleChange(nivel.nivelSocioId, nivel, "maxSaddayFisicoId", v)}
                      >
                        <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {lookups.saddayRiesgos.map((s) => (
                            <SelectItem key={s.id} value={s.id}>{s.escala}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
