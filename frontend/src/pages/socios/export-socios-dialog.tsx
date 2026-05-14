import { useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useLookups } from "@/hooks/use-socios"
import { useExportarSociosCsv, useExportarSociosPdf, useExportarSociosFirmas } from "@/hooks/use-socios"
import { toast } from "sonner"
import { Download, FileText, Table } from "lucide-react"

// ─── Campos exportables ──────────────────────────────

interface FieldDef {
  key: string
  label: string
  defaultOn: boolean
}

const EXPORT_FIELDS: FieldDef[] = [
  { key: "apellido",              label: "Apellido",                         defaultOn: true  },
  { key: "nombre",                label: "Nombre",                           defaultOn: true  },
  { key: "cedula",                label: "Cédula",                           defaultOn: true  },
  { key: "correo",                label: "Correo electrónico",               defaultOn: true  },
  { key: "telefono",              label: "Teléfono",                         defaultOn: false },
  { key: "fechaNacimiento",       label: "Fecha de nacimiento",              defaultOn: false },
  { key: "edad",                  label: "Edad (años)",                      defaultOn: false },
  { key: "fechaIngreso",          label: "Fecha de ingreso",                 defaultOn: false },
  { key: "antiguedadAnios",       label: "Antigüedad (años)",                defaultOn: false },
  { key: "fechaSalida",           label: "Fecha de salida",                  defaultOn: false },
  { key: "direccion",             label: "Dirección",                        defaultOn: false },
  { key: "tipoSangre",            label: "Tipo de sangre",                   defaultOn: false },
  { key: "tipoSocio",             label: "Tipo de socio",                    defaultOn: true  },
  { key: "nivelTecnico",          label: "Nivel técnico",                    defaultOn: false },
  { key: "estadoHabilitacion",    label: "Estado habilitación",              defaultOn: true  },
  { key: "estadoAcceso",          label: "Estado de acceso",                 defaultOn: false },
  { key: "emergencyContactName",  label: "Contacto emergencia 1 — nombre",  defaultOn: false },
  { key: "emergencyContactPhone", label: "Contacto emergencia 1 — teléfono",defaultOn: false },
  { key: "emergencyContactName2", label: "Contacto emergencia 2 — nombre",  defaultOn: false },
  { key: "emergencyContactPhone2",label: "Contacto emergencia 2 — teléfono",defaultOn: false },
]

type ExportType = "csv" | "pdf" | "firmas"

// ─── Estado inicial de campos ─────────────────────────

function initialFieldState(): Record<string, { checked: boolean; order: number }> {
  const state: Record<string, { checked: boolean; order: number }> = {}
  let orderCounter = 1
  EXPORT_FIELDS.forEach((f) => {
    state[f.key] = {
      checked: f.defaultOn,
      order:   f.defaultOn ? orderCounter++ : 0,
    }
  })
  return state
}

// ─── Props ───────────────────────────────────────────

interface ExportSociosDialogProps {
  open: boolean
  onClose: () => void
  initialTipoId?: string
  initialEstadoId?: string
  initialQ?: string
}

// ─── Componente ──────────────────────────────────────

export function ExportSociosDialog({
  open,
  onClose,
  initialTipoId = "",
  initialEstadoId = "",
  initialQ = "",
}: ExportSociosDialogProps) {
  const { data: lookups } = useLookups()

  const [exportType, setExportType]   = useState<ExportType>("csv")
  const [fields, setFields]           = useState<Record<string, { checked: boolean; order: number }>>(initialFieldState)
  const [tipoId, setTipoId]           = useState(initialTipoId)
  const [estadoId, setEstadoId]       = useState(initialEstadoId)
  const [excludeAdmin, setExcludeAdmin] = useState(true)
  const [q, setQ]                     = useState(initialQ)

  const csvMutation    = useExportarSociosCsv()
  const pdfMutation    = useExportarSociosPdf()
  const firmasMutation = useExportarSociosFirmas()

  const isPending = csvMutation.isPending || pdfMutation.isPending || firmasMutation.isPending

  // Campos seleccionados con orden válido, ordenados por número de orden
  function buildOrderedFields(): string[] {
    return Object.entries(fields)
      .filter(([, v]) => v.checked && v.order > 0)
      .sort(([, a], [, b]) => a.order - b.order)
      .map(([key]) => key)
  }

  function validate(): string | null {
    if (exportType === "firmas") return null
    const ordered = buildOrderedFields()
    if (ordered.length === 0) {
      return "Selecciona al menos un campo y asígnale un número de orden."
    }
    return null
  }

  function buildParams() {
    return {
      fields:       buildOrderedFields(),
      tipoId:       tipoId   ? Number(tipoId)   : undefined,
      estadoId:     estadoId ? Number(estadoId) : undefined,
      excludeAdmin,
      q:            q || undefined,
    }
  }

  async function handleExport() {
    const error = validate()
    if (error) {
      toast.error(error)
      return
    }
    try {
      const params = buildParams()
      if (exportType === "csv") {
        await csvMutation.mutateAsync(params)
        toast.success("CSV descargado correctamente")
      } else if (exportType === "pdf") {
        await pdfMutation.mutateAsync(params)
        toast.success("PDF descargado correctamente")
      } else {
        await firmasMutation.mutateAsync(params)
        toast.success("Hoja de firmas descargada correctamente")
      }
      onClose()
    } catch {
      toast.error("Error al generar el archivo")
    }
  }

  function toggleField(key: string, checked: boolean) {
    setFields((prev) => {
      const next = { ...prev, [key]: { ...prev[key], checked } }
      if (!checked) {
        next[key] = { checked: false, order: 0 }
      }
      return next
    })
  }

  function setFieldOrder(key: string, value: string) {
    const num = parseInt(value, 10)
    setFields((prev) => ({
      ...prev,
      [key]: { ...prev[key], order: isNaN(num) || num < 1 ? 0 : num },
    }))
  }

  const showFieldSelector  = exportType !== "firmas"
  const orderedCount       = buildOrderedFields().length
  const pdfWarning         = exportType === "pdf" && orderedCount > 6
  const missingOrderWarning = showFieldSelector &&
    Object.values(fields).some((v) => v.checked && v.order <= 0)

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Download className="h-5 w-5" />
            Exportar socios
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-5 py-1">

          {/* ── Tipo de exportación ────────────────────── */}
          <section>
            <p className="text-sm font-medium text-foreground mb-2">Formato de exportación</p>
            <div className="flex gap-2">
              {([
                { value: "csv",    label: "Lista CSV",         icon: Table    },
                { value: "pdf",    label: "Lista PDF",         icon: FileText },
                { value: "firmas", label: "Hoja de Firmas PDF",icon: FileText },
              ] as const).map(({ value, label, icon: Icon }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setExportType(value)}
                  className={`flex-1 flex flex-col items-center gap-1 rounded-lg border px-3 py-2 text-xs transition-colors
                    ${exportType === value
                      ? "border-primary bg-primary/10 text-primary font-semibold"
                      : "border-border bg-card text-muted-foreground hover:border-primary/40"}`}
                >
                  <Icon className="h-4 w-4" />
                  {label}
                </button>
              ))}
            </div>
          </section>

          {/* ── Campos a exportar ─────────────────────── */}
          {showFieldSelector ? (
            <section>
              <p className="text-sm font-medium text-foreground mb-1">
                Campos a exportar
                {exportType === "pdf" && (
                  <span className="ml-2 text-xs text-muted-foreground">(máx. 6 para PDF)</span>
                )}
              </p>
              {pdfWarning && (
                <p className="text-xs text-amber-600 mb-2">
                  Se incluirán solo los primeros 6 campos según su orden.
                </p>
              )}
              {missingOrderWarning && (
                <p className="text-xs text-amber-600 mb-2">
                  Campos marcados sin número de orden no se incluirán en la exportación.
                </p>
              )}
              <div className="rounded-lg border border-border divide-y divide-border max-h-64 overflow-y-auto">
                {EXPORT_FIELDS.map((f) => {
                  const state = fields[f.key]
                  return (
                    <div key={f.key} className="flex items-center gap-3 px-3 py-2">
                      <Checkbox
                        id={`field-${f.key}`}
                        checked={state.checked}
                        onCheckedChange={(v) => toggleField(f.key, Boolean(v))}
                      />
                      <Label htmlFor={`field-${f.key}`} className="flex-1 text-sm cursor-pointer">
                        {f.label}
                      </Label>
                      {state.checked && (
                        <Input
                          type="number"
                          min={1}
                          max={20}
                          value={state.order || ""}
                          onChange={(e) => setFieldOrder(f.key, e.target.value)}
                          placeholder="#"
                          className="w-14 h-7 text-xs text-center"
                        />
                      )}
                    </div>
                  )
                })}
              </div>
            </section>
          ) : (
            <section>
              <p className="text-sm font-medium text-foreground mb-1">Columnas de la hoja</p>
              <div className="rounded-lg border border-border bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
                <span className="font-medium text-foreground">Cédula</span>
                {" · "}
                <span className="font-medium text-foreground">Apellido</span>
                {" · "}
                <span className="font-medium text-foreground">Nombre</span>
                {" · "}
                <span className="font-medium text-foreground">Firma</span>
                <p className="text-xs mt-1">Formato fijo — filas de 2 cm para firma manuscrita</p>
              </div>
            </section>
          )}

          {/* ── Filtros ───────────────────────────────── */}
          <section>
            <p className="text-sm font-medium text-foreground mb-2">Filtros</p>
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label className="text-xs">Tipo de socio</Label>
                  <Select value={tipoId} onValueChange={(v) => setTipoId(v === "all" ? "" : v)}>
                    <SelectTrigger className="h-8 text-xs"><SelectValue placeholder="Todos" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todos</SelectItem>
                      {lookups?.tiposSocio.map((t) => (
                        <SelectItem key={t.id} value={String(t.id)}>{t.nombre}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Estado habilitación</Label>
                  <Select value={estadoId} onValueChange={(v) => setEstadoId(v === "all" ? "" : v)}>
                    <SelectTrigger className="h-8 text-xs"><SelectValue placeholder="Todos" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todos</SelectItem>
                      {lookups?.estadosHabilitacion.map((e) => (
                        <SelectItem key={e.id} value={String(e.id)}>{e.nombre}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Checkbox
                  id="exclude-admin"
                  checked={excludeAdmin}
                  onCheckedChange={(v) => setExcludeAdmin(Boolean(v))}
                />
                <Label htmlFor="exclude-admin" className="text-sm cursor-pointer">
                  Excluir Admin
                </Label>
              </div>
            </div>
          </section>
        </div>

        {/* ── Acciones ──────────────────────────────── */}
        <div className="flex justify-end gap-2 pt-2 border-t border-border mt-2">
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            Cancelar
          </Button>
          <Button onClick={handleExport} disabled={isPending} className="gap-2">
            <Download className="h-4 w-4" />
            {isPending ? "Generando..." : "Descargar"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
