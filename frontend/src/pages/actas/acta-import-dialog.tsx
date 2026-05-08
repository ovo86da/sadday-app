import { useCallback, useRef, useState } from "react"
import { toast } from "sonner"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Upload, CheckCircle2, AlertCircle, Loader2 } from "lucide-react"
import { useImportarActaPreview, useConfirmarImportActa } from "@/hooks/use-actas"
import type {
  TipoActa,
  ActaImportPreview,
  AsistenteImport,
  PersonaImport,
  ActaImportConfirmRequest,
} from "@/types/actas"

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  onImported?: () => void
  tipo: TipoActa
}

type Step = "upload" | "preview" | "confirming"

export function ActaImportDialog({ open, onOpenChange, onImported, tipo }: Props) {
  const [step, setStep] = useState<Step>("upload")
  const [preview, setPreview] = useState<ActaImportPreview | null>(null)

  // Resoluciones manuales: indexadas por posición en el array de asistentes
  const [asistenteOverrides, setAsistenteOverrides] = useState<Record<number, string | null>>({})
  const [presidenteOverride, setPresidenteOverride] = useState<string | null | undefined>(undefined)
  const [secretariaOverride, setSecretariaOverride] = useState<string | null | undefined>(undefined)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const previewMutation   = useImportarActaPreview()
  const confirmarMutation = useConfirmarImportActa()

  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      if (!file.name.endsWith(".md")) {
        toast.error("Solo se aceptan archivos .md")
        return
      }
      try {
        const result = await previewMutation.mutateAsync(file)
        setPreview(result)
        setAsistenteOverrides({})
        setPresidenteOverride(undefined)
        setSecretariaOverride(undefined)
        setStep("preview")
      } catch (error) { console.error(error);
        toast.error("Error al procesar el archivo. Verifica que tenga el formato correcto.")
      }
    },
    [previewMutation],
  )

  const handleConfirmar = useCallback(async () => {
    if (!preview) return
    setStep("confirming")

    const resolverPersona = (
      persona: PersonaImport | null,
      override: string | null | undefined,
    ): string | null => {
      if (override !== undefined) return override
      return persona?.socioId ?? null
    }

    const req: ActaImportConfirmRequest = {
      tipoActa:               tipo,
      numeroReunion:          preview.numeroReunion,
      fecha:                  preview.fecha!,
      hora:                   preview.hora!,
      horaFin:                preview.horaFin,
      lugar:                  preview.lugar,
      presidenteReunionId:    resolverPersona(preview.presidenteReunion, presidenteOverride),
      presidenteReunionNombreRaw: preview.presidenteReunion?.nombreRaw ?? null,
      secretariaReunionId:    resolverPersona(preview.secretariaReunion, secretariaOverride),
      secretariaReunionNombreRaw: preview.secretariaReunion?.nombreRaw ?? null,
      asistentes: preview.asistentes.map((a, idx) => ({
        nombreRaw: a.nombreRaw,
        socioId: asistenteOverride(a, idx),
      })),
      actividadesRealizadasDesc: preview.actividadesRealizadasDesc,
      actividadesPorRealizar:    preview.actividadesPorRealizar,
      acuerdos:                  preview.acuerdos,
      varios:                    preview.varios,
      observaciones:             preview.observaciones,
    }

    try {
      await confirmarMutation.mutateAsync(req)
      toast.success("Acta importada correctamente")
      onImported?.()
      handleClose()
    } catch (error) { console.error(error);
      toast.error("Error al guardar el acta. Intenta de nuevo.")
      setStep("preview")
    }
  }, [preview, asistenteOverrides, presidenteOverride, secretariaOverride, confirmarMutation, onImported])

  const asistenteOverride = (a: AsistenteImport, idx: number): string | null => {
    if (idx in asistenteOverrides) return asistenteOverrides[idx]
    return a.socioId
  }

  const handleClose = () => {
    setStep("upload")
    setPreview(null)
    setAsistenteOverrides({})
    setPresidenteOverride(undefined)
    setSecretariaOverride(undefined)
    if (fileInputRef.current) fileInputRef.current.value = ""
    onOpenChange(false)
  }

  const noResueltos = preview
    ? (
        (!preview.presidenteReunion?.resuelto && presidenteOverride === undefined ? 1 : 0) +
        (!preview.secretariaReunion?.resuelto && secretariaOverride === undefined ? 1 : 0) +
        preview.asistentes.filter(
          (a, idx) => !a.resuelto && !(idx in asistenteOverrides),
        ).length
      )
    : 0

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) handleClose() }}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Importar acta desde archivo .md</DialogTitle>
          <DialogDescription>
            {step === "upload"
              ? "Sube el archivo .md del acta con el formato estándar del club."
              : "Revisa los datos extraídos. Asigna manualmente los nombres no reconocidos."}
          </DialogDescription>
        </DialogHeader>

        {/* ── Step 1: Upload ── */}
        {step === "upload" && (
          <div className="flex flex-col items-center gap-6 py-8">
            <div
              className="w-full border-2 border-dashed border-muted-foreground/30 rounded-lg p-10 flex flex-col items-center gap-3 cursor-pointer hover:border-primary/50 transition-colors"
              onClick={() => fileInputRef.current?.click()}
            >
              {previewMutation.isPending ? (
                <Loader2 className="h-10 w-10 text-muted-foreground animate-spin" />
              ) : (
                <Upload className="h-10 w-10 text-muted-foreground" />
              )}
              <p className="text-sm text-muted-foreground text-center">
                {previewMutation.isPending
                  ? "Procesando archivo..."
                  : "Haz clic para seleccionar el archivo .md"}
              </p>
              <p className="text-xs text-muted-foreground/60">Solo archivos .md con el formato del acta</p>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".md"
              className="hidden"
              onChange={handleFileChange}
            />
          </div>
        )}

        {/* ── Step 2: Preview ── */}
        {(step === "preview" || step === "confirming") && preview && (
          <div className="max-h-[60vh] overflow-y-auto pr-3">
            <div className="space-y-4">

              {/* Cabecera */}
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <span className="text-muted-foreground">Tipo: </span>
                  <Badge variant={tipo === "DIRECTIVA" ? "secondary" : "outline"}>
                    {tipo === "DIRECTIVA" ? "Directiva" : "Socios"}
                  </Badge>
                  {preview.numeroReunion && (
                    <span className="ml-2 font-medium">No. {preview.numeroReunion}</span>
                  )}
                </div>
                <div>
                  <span className="text-muted-foreground">Fecha: </span>
                  <span className="font-medium">{preview.fecha ?? "—"}</span>
                  {preview.hora && <span className="ml-2 text-muted-foreground">{preview.hora}{preview.horaFin ? ` – ${preview.horaFin}` : ""}</span>}
                </div>
              </div>

              <Separator />

              {/* Presidente */}
              <PersonaField
                label="Preside la reunión"
                persona={preview.presidenteReunion}
                override={presidenteOverride}
                onOverride={setPresidenteOverride}
              />

              {/* Secretaria */}
              <PersonaField
                label="Secretaria de la reunión"
                persona={preview.secretariaReunion}
                override={secretariaOverride}
                onOverride={setSecretariaOverride}
              />

              <Separator />

              {/* Asistentes */}
              <div>
                <p className="text-sm font-medium mb-2">
                  Asistentes ({preview.asistentes.length})
                  {noResueltos > 0 && (
                    <Badge variant="outline" className="ml-2 text-orange-600 border-orange-300">
                      {noResueltos} sin asignar
                    </Badge>
                  )}
                </p>
                <div className="space-y-2">
                  {preview.asistentes.map((a, idx) => (
                    <AsistenteRow
                      key={idx}
                      asistente={a}
                      override={asistenteOverrides[idx]}
                      onOverride={(v) =>
                        setAsistenteOverrides((prev) => ({ ...prev, [idx]: v }))
                      }
                    />
                  ))}
                </div>
              </div>

              <Separator />

              {/* Contenido */}
              <ContentSection label="Actividades realizadas"   text={preview.actividadesRealizadasDesc} />
              <ContentSection label="Actividades por realizar" text={preview.actividadesPorRealizar} />
              <ContentSection label="Acuerdos"                 text={preview.acuerdos} />
              <ContentSection label="Varios"                   text={preview.varios} />
            </div>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={step === "confirming"}>
            Cancelar
          </Button>
          {step === "preview" && (
            <Button onClick={handleConfirmar} disabled={confirmarMutation.isPending}>
              {confirmarMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Guardar acta
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Sub-componentes ────────────────────────────────────────────────────────────

function PersonaField({
  label,
  persona,
  override,
  onOverride,
}: {
  label: string
  persona: PersonaImport | null | undefined
  override: string | null | undefined
  onOverride: (v: string | null) => void
}) {
  if (!persona) return null

  const resolved = override !== undefined ? override : persona.socioId
  const isOk = resolved !== null

  return (
    <div className="flex items-center gap-2 text-sm">
      {isOk
        ? <CheckCircle2 className="h-4 w-4 text-green-500 shrink-0" />
        : <AlertCircle  className="h-4 w-4 text-orange-500 shrink-0" />
      }
      <span className="text-muted-foreground w-40 shrink-0">{label}:</span>
      {persona.resuelto && override === undefined ? (
        <span className="font-medium">{persona.socioNombre} {persona.socioApellido}</span>
      ) : (
        <div className="flex items-center gap-2 flex-1">
          <span className="text-muted-foreground italic">{persona.nombreRaw}</span>
          {persona.candidatos.length > 0 && (
            <Select
              value={resolved ?? "__none__"}
              onValueChange={(v) => onOverride(v === "__none__" ? null : v)}
            >
              <SelectTrigger className="h-7 text-xs w-48">
                <SelectValue placeholder="Seleccionar socio" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none__">— No asignar —</SelectItem>
                {persona.candidatos.map((c) => (
                  <SelectItem key={c.socioId} value={c.socioId}>
                    {c.nombre} {c.apellido}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
      )}
    </div>
  )
}

function AsistenteRow({
  asistente,
  override,
  onOverride,
}: {
  asistente: AsistenteImport
  override: string | null | undefined
  onOverride: (v: string | null) => void
}) {
  const resolved = override !== undefined ? override : asistente.socioId
  const isOk = resolved !== null

  return (
    <div className="flex items-center gap-2 text-sm">
      {isOk
        ? <CheckCircle2 className="h-3.5 w-3.5 text-green-500 shrink-0" />
        : <AlertCircle  className="h-3.5 w-3.5 text-orange-400 shrink-0" />
      }
      <span className={isOk ? "font-medium" : "text-muted-foreground italic"}>
        {asistente.resuelto && override === undefined
          ? `${asistente.socioNombre} ${asistente.socioApellido}`
          : asistente.nombreRaw}
      </span>
      {(!asistente.resuelto || override !== undefined) && asistente.candidatos.length > 0 && (
        <Select
          value={resolved ?? "__none__"}
          onValueChange={(v) => onOverride(v === "__none__" ? null : v)}
        >
          <SelectTrigger className="h-6 text-xs w-44 ml-auto">
            <SelectValue placeholder="Asignar socio" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__none__">— No asignar —</SelectItem>
            {asistente.candidatos.map((c) => (
              <SelectItem key={c.socioId} value={c.socioId}>
                {c.nombre} {c.apellido}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
    </div>
  )
}

function ContentSection({ label, text }: { label: string; text: string | null }) {
  if (!text) return null
  return (
    <div>
      <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1">{label}</p>
      <p className="text-sm whitespace-pre-line line-clamp-4">{text}</p>
    </div>
  )
}
