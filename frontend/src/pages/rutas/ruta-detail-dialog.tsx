import { useRef } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useRutaDetail, useSubirDocumentoRuta, useEliminarDocumentoRuta } from "@/hooks/use-rutas"
import { useSalidasByRuta } from "@/hooks/use-salidas"
import { useAuthStore } from "@/stores/auth-store"
import { Phone, Mail, ExternalLink, Calendar, FileText, Upload, Trash2, Download } from "lucide-react"
import { TIPO_ACTIVIDAD_LABELS, TIPO_BICICLETA_LABELS, CATEGORIA_BADGE } from "@/types/rutas"
import { toast } from "sonner"

function formatDate(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("es-EC", {
    day: "numeric", month: "short", year: "numeric",
  })
}

const estadoColor: Record<string, string> = {
  PLANIFICADA: "bg-blue-500/15 text-blue-600",
  EN_CURSO:    "bg-yellow-500/15 text-yellow-600",
  REALIZADA:   "bg-green-500/15 text-green-600",
  CANCELADA:   "bg-red-500/15 text-red-600",
}

const actividadColor = CATEGORIA_BADGE

interface Props {
  open: boolean
  onClose: () => void
  rutaId: number
}

export function RutaDetailDialog({ open, onClose, rutaId }: Props) {
  const user = useAuthStore((s) => s.user)
  const canManageDocs = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(user?.rol?.toUpperCase() ?? "")

  const { data: ruta, isLoading } = useRutaDetail(rutaId)
  const { data: salidasPage } = useSalidasByRuta(rutaId)
  const salidas = salidasPage?.content ?? []

  const subirMutation  = useSubirDocumentoRuta(rutaId)
  const eliminarMutation = useEliminarDocumentoRuta(rutaId)
  const fileInputRef   = useRef<HTMLInputElement>(null)

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      await subirMutation.mutateAsync(file)
      toast.success(`Documento "${file.name}" subido correctamente`)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg ?? "Error al subir el documento")
    }
    e.target.value = ""
  }

  const handleEliminar = async (docId: string, filename: string) => {
    if (!confirm(`¿Eliminar el documento "${filename}"? Esta acción no se puede deshacer.`)) return
    try {
      await eliminarMutation.mutateAsync(docId)
      toast.success("Documento eliminado")
    } catch (error) { console.error(error);
      toast.error("Error al eliminar el documento")
    }
  }

  const handleDescargar = (rutaId: number, docId: string, filename: string) => {
    const url = `/api/v1/rutas/${rutaId}/documentos/${docId}/descargar`
    const a = document.createElement("a")
    a.href = url
    a.download = filename
    a.click()
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Detalle de la ruta</DialogTitle>
        </DialogHeader>

        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : ruta ? (
          <div className="space-y-6">

            {/* Header */}
            <div>
              <h2 className="text-xl font-bold text-foreground">{ruta.nombre}</h2>
              <p className="text-sm text-muted-foreground">
                {ruta.mountainNombre ?? ruta.lugarReferencia}
                {ruta.sectorZona ? ` — ${ruta.sectorZona}` : ""}
              </p>
              <div className="flex flex-wrap gap-2 mt-2">
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${actividadColor[ruta.tipoActividad]}`}>
                  {TIPO_ACTIVIDAD_LABELS[ruta.tipoActividad]}
                </span>
                <Badge variant={ruta.aprobada ? "default" : "secondary"}>
                  {ruta.aprobada ? "Aprobada" : "Pendiente"}
                </Badge>
                {ruta.requierePermisos && <Badge variant="destructive">Requiere permisos</Badge>}
                {ruta.nivelMinimoSocioNombre && (
                  <Badge variant="outline">Nivel mín: {ruta.nivelMinimoSocioNombre}</Badge>
                )}
              </div>
            </div>

            {/* Características comunes */}
            <Section title="Características">
              <InfoRow label="Longitud" value={ruta.longitudKm ? `${ruta.longitudKm} km` : "—"} />
              <InfoRow label="Desnivel" value={ruta.desnivelM ? `${ruta.desnivelM} m` : "—"} />
              {ruta.duracionDias
                ? <InfoRow label="Duración" value={`${ruta.duracionDias} días`} />
                : ruta.duracionHoras
                  ? <InfoRow label="Duración" value={`${ruta.duracionHoras} h`} />
                  : <InfoRow label="Duración" value="—" />
              }
            </Section>

            {/* Dificultad técnica por tipo */}
            {ruta.tipoActividad === "ALPINISMO" && ruta.alpinismo && (
              <Section title="Dificultad técnica — Alpinismo">
                <InfoRow label="Alpina IFAS" value={ruta.alpinismo.escalaAlpinaIfasGrado} />
                <InfoRow label="Roca UIAA"   value={ruta.alpinismo.dificultadRocaUiaa} />
                <InfoRow label="Hielo WI"    value={ruta.alpinismo.dificultadHieloGrado} />
                <InfoRow label="Compromiso"  value={ruta.alpinismo.compromisoTipo} />
                <InfoRow label="Yosemite"    value={ruta.alpinismo.yosemiteTipo} />
                <InfoRow label="Nivel Técnico (Sadday)" value={ruta.alpinismo.saddayNivelTecnicoEscala} />
                <InfoRow label="Nivel Físico (Sadday)"  value={ruta.alpinismo.saddayNivelFisicoEscala} />
                {ruta.alpinismo.equipoMontanaNombre && (
                  <InfoRow label="Equipo" value={ruta.alpinismo.equipoMontanaNombre} />
                )}
              </Section>
            )}

            {ruta.tipoActividad === "ESCALADA" && ruta.escalada && (
              <Section title="Dificultad técnica — Escalada">
                <InfoRow label="Grado roca (UIAA)" value={ruta.escalada.dificultadRocaUiaa} />
                <InfoRow label="Tipo"               value={ruta.escalada.tipoEscalada.charAt(0) + ruta.escalada.tipoEscalada.slice(1).toLowerCase()} />
                {ruta.escalada.numCintas   != null && <InfoRow label="N° cintas"    value={String(ruta.escalada.numCintas)} />}
                {ruta.escalada.alturaViaM  != null && <InfoRow label="Altura vía"   value={`${ruta.escalada.alturaViaM} m`} />}
                {ruta.escalada.tipoRoca              && <InfoRow label="Tipo de roca" value={ruta.escalada.tipoRoca} />}
              </Section>
            )}

            {ruta.tipoActividad === "TREKKING" && ruta.trekking && (
              <Section title="Características — Trekking">
                <InfoRow label="Dificultad"    value={ruta.trekking.dificultadNombre} />
                <InfoRow label="Tipo de ruta"  value={ruta.trekking.esCircular ? "Circular" : "Ida y vuelta"} />
                <InfoRow label="Fuentes agua"  value={ruta.trekking.fuentesAgua ? "Sí" : "No"} />
                {ruta.trekking.tipoTerreno && <InfoRow label="Terreno" value={ruta.trekking.tipoTerreno} />}
              </Section>
            )}

            {ruta.tipoActividad === "CICLISMO" && ruta.ciclismo && (
              <Section title="Características — Ciclismo">
                <InfoRow label="Bicicleta"    value={TIPO_BICICLETA_LABELS[ruta.ciclismo.tipoBicicleta] ?? ruta.ciclismo.tipoBicicleta} />
                {ruta.ciclismo.dificultadTecnica       && <InfoRow label="Dificultad tec." value={ruta.ciclismo.dificultadTecnica} />}
                {ruta.ciclismo.superficiePredominante  && <InfoRow label="Superficie"       value={ruta.ciclismo.superficiePredominante} />}
                {ruta.ciclismo.ciclabilidadPct != null && <InfoRow label="Ciclabilidad"     value={`${ruta.ciclismo.ciclabilidadPct}%`} />}
              </Section>
            )}

            {ruta.peligrosNotas && (
              <div>
                <h3 className="text-sm font-semibold text-foreground border-b border-border pb-2 mb-2">Peligros / Notas</h3>
                <p className="text-sm text-muted-foreground whitespace-pre-wrap">{ruta.peligrosNotas}</p>
              </div>
            )}

            {ruta.trackUrl && (
              <Section title="Enlaces">
                <a href={ruta.trackUrl} target="_blank" rel="noopener noreferrer"
                  className="flex items-center gap-2 text-sm text-primary hover:underline col-span-full">
                  <ExternalLink className="h-3 w-3" /> Track GPS
                </a>
              </Section>
            )}

            {/* Documentos de permiso */}
            {ruta.requierePermisos && (
              <div className="space-y-3">
                <div className="flex items-center justify-between border-b border-border pb-2">
                  <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                    <FileText className="h-4 w-4 text-muted-foreground" />
                    Documentos de permiso
                  </h3>
                  {canManageDocs && (
                    <>
                      <Button
                        size="sm"
                        variant="outline"
                        className="gap-1.5"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={subirMutation.isPending}
                      >
                        <Upload className="h-3.5 w-3.5" />
                        {subirMutation.isPending ? "Subiendo..." : "Subir documento"}
                      </Button>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept=".pdf,.doc,.docx,.xls,.xlsx"
                        className="hidden"
                        onChange={handleFileChange}
                      />
                    </>
                  )}
                </div>

                {ruta.documentosPermiso.length === 0 ? (
                  <p className="text-sm text-muted-foreground py-1">
                    {canManageDocs
                      ? "No hay documentos cargados. Usa el botón para subir el primero."
                      : "No hay documentos de permiso cargados aún."}
                  </p>
                ) : (
                  <ul className="divide-y divide-border rounded-xl border border-border bg-card">
                    {ruta.documentosPermiso.map((doc) => (
                      <li key={doc.id} className="flex items-center gap-3 px-4 py-3">
                        <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate text-foreground">{doc.filename}</p>
                          <p className="text-xs text-muted-foreground">
                            {formatFileSize(doc.sizeBytes)}
                            {doc.subidoPorNombre && ` · ${doc.subidoPorNombre}`}
                          </p>
                        </div>
                        <div className="flex gap-1 shrink-0">
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleDescargar(ruta.id, doc.id, doc.filename)}
                            title="Descargar"
                          >
                            <Download className="h-4 w-4 text-primary" />
                          </Button>
                          {canManageDocs && (
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleEliminar(doc.id, doc.filename)}
                              disabled={eliminarMutation.isPending}
                              title="Eliminar"
                            >
                              <Trash2 className="h-4 w-4 text-destructive" />
                            </Button>
                          )}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}

                {/* Fallback: URL vieja si no hay docs nuevos */}
                {ruta.documentosPermiso.length === 0 && ruta.documentacionUrl && (
                  <a href={ruta.documentacionUrl} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-2 text-sm text-primary hover:underline">
                    <ExternalLink className="h-3 w-3" /> Ver documentación externa
                  </a>
                )}
              </div>
            )}

            {ruta.contactos.length > 0 && (
              <div className="space-y-3">
                <h3 className="text-sm font-semibold text-foreground border-b border-border pb-2">
                  Contactos de apoyo ({ruta.contactos.length})
                </h3>
                {ruta.contactos.map((c) => (
                  <div key={c.id} className="rounded-lg border border-border p-3 space-y-1">
                    <p className="font-medium text-sm">{c.nombre} <span className="text-xs text-muted-foreground">({c.tipoContactoNombre})</span></p>
                    {c.telefono && <p className="flex items-center gap-1 text-sm text-muted-foreground"><Phone className="h-3 w-3" />{c.telefono}</p>}
                    {c.correo && <p className="flex items-center gap-1 text-sm text-muted-foreground"><Mail className="h-3 w-3" />{c.correo}</p>}
                  </div>
                ))}
              </div>
            )}

            {/* Salidas */}
            <div className="space-y-2">
              <div className="flex items-center gap-2 border-b border-border pb-2">
                <Calendar className="h-4 w-4 text-muted-foreground" />
                <h3 className="text-sm font-semibold text-foreground">
                  Salidas realizadas {salidas.length > 0 && `(${salidas.length})`}
                </h3>
              </div>
              {salidas.length === 0 ? (
                <p className="text-sm text-muted-foreground py-2">No hay salidas registradas para esta ruta.</p>
              ) : (
                <ul className="divide-y divide-border rounded-xl border border-border bg-card">
                  {salidas.map((s) => (
                    <li key={s.id} className="flex items-center justify-between gap-3 px-4 py-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-foreground">{s.nombre}</p>
                        <p className="text-xs text-muted-foreground">{formatDate(s.fechaInicio)}</p>
                      </div>
                      <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${estadoColor[s.estado] ?? ""}`}>
                        {s.estado.replace("_", " ")}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-foreground border-b border-border pb-2">{title}</h3>
      <div className="grid gap-2 sm:grid-cols-3">{children}</div>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm font-medium text-foreground">{value}</p>
    </div>
  )
}
