import { useState, useEffect } from "react"
import { useInforme, useCreateInforme, useUpdateInforme, useValidarInforme, useGenerarPdfInforme } from "@/hooks/use-informes"
import { useAuthStore } from "@/stores/auth-store"
import { useSalidasList } from "@/hooks/use-salidas"
import api from "@/lib/api"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import { toast } from "sonner"
import { FileText, Search, Eye, Clock, CheckCircle, Award, AlertTriangle, Download, RefreshCw } from "lucide-react"

async function downloadPdf(url: string, filename: string) {
  const response = await api.get(url, { responseType: "blob" })
  const blob = new Blob([response.data], { type: "application/pdf" })
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = objectUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(objectUrl)
}


export function InformesPage() {
  const user = useAuthStore((s) => s.user)
  const userRole = user?.rol?.toUpperCase() ?? ""
  const canValidate = ["ADMIN", "DIRECTIVO"].includes(userRole)
  const canGeneratePdf = ["ADMIN", "DIRECTIVO", "SECRETARIA"].includes(userRole)
  const canEdit = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")
  const [selectedSalidaId, setSelectedSalidaId] = useState<string | null>(null)

  const { data: salidasPage, isLoading } = useSalidasList({
    page,
    q: searchDebounced || undefined,
    estado: "REALIZADA",
  })

  const handleSearch = () => { setSearchDebounced(search); setPage(0) }
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === "Enter") handleSearch() }

  const formatDate = (iso: string) =>
    new Date(iso + "T00:00:00").toLocaleDateString("es-EC", { day: "numeric", month: "short", year: "numeric" })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Informes de Salida</h1>
        <p className="text-muted-foreground">Selecciona una salida realizada para ver o crear su informe</p>
      </div>

      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-blue-500/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-500/10">
              <Search className="h-4 w-4 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Buscar salidas</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Selecciona una salida realizada para ver o gestionar su informe</p>
            </div>
          </div>
        </div>
        <div className="p-5">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input placeholder="Buscar salidas realizadas..." value={search} onChange={(e) => setSearch(e.target.value)} onKeyDown={handleKeyDown} className="pl-9" />
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Salida</TableHead>
              <TableHead className="hidden md:table-cell">Fecha</TableHead>
              <TableHead className="hidden lg:table-cell">Ruta</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 4 }).map((_, j) => (
                    <TableCell key={j}><div className="h-4 w-24 animate-pulse rounded bg-muted" /></TableCell>
                  ))}
                </TableRow>
              ))
            ) : salidasPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                  No hay salidas realizadas
                </TableCell>
              </TableRow>
            ) : (
              salidasPage?.content.map((s) => (
                <TableRow
                  key={s.id}
                  className="cursor-pointer"
                  onClick={() => setSelectedSalidaId(s.id)}
                >
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <FileText className="h-4 w-4 text-muted-foreground shrink-0" />
                      <span className="font-medium">{s.nombre}</span>
                    </div>
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-sm">{formatDate(s.fechaInicio)}</TableCell>
                  <TableCell className="hidden lg:table-cell text-sm">{s.rutaNombre}</TableCell>
                  <TableCell className="text-right">
                    <Eye className="h-4 w-4 ml-auto text-muted-foreground" />
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {salidasPage && salidasPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">Página {salidasPage.page.number + 1} de {salidasPage.page.totalPages}</p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={salidasPage.page.number === 0} onClick={() => setPage((p) => p - 1)}>Anterior</Button>
            <Button variant="outline" size="sm" disabled={salidasPage.page.number >= salidasPage.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>Siguiente</Button>
          </div>
        </div>
      )}

      {selectedSalidaId && (
        <InformeDetailDialog
          salidaId={selectedSalidaId}
          onClose={() => setSelectedSalidaId(null)}
          canValidate={canValidate}
          canGeneratePdf={canGeneratePdf}
          canEdit={canEdit}
        />
      )}
    </div>
  )
}

// ─── Informe Detail Dialog ───────────────────────────

function InformeDetailDialog({
  salidaId, onClose, canValidate, canGeneratePdf, canEdit,
}: {
  salidaId: string; onClose: () => void; canValidate: boolean; canGeneratePdf: boolean; canEdit: boolean
}) {
  const { data: informe, isLoading, isError } = useInforme(salidaId)
  const [editMode, setEditMode] = useState(false)
  const [isDownloading, setIsDownloading] = useState(false)
  const generarPdfMutation = useGenerarPdfInforme(salidaId)

  const handleDescargarPdf = async () => {
    if (!informe) return
    setIsDownloading(true)
    try {
      await downloadPdf(
        `/v1/informes/${salidaId}/pdf`,
        informe.documentoFilename ?? `informe-${salidaId}.pdf`,
      )
    } catch (error) { console.error(error);
      toast.error("Error al descargar el PDF")
    } finally {
      setIsDownloading(false)
    }
  }

  const handleGenerarPdf = async () => {
    try {
      const { blob, filename } = await generarPdfMutation.mutateAsync()
      const objectUrl = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = objectUrl
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(objectUrl)
      toast.success("PDF generado y descargado correctamente")
    } catch (error) { console.error(error);
      toast.error("Error al generar el PDF")
    }
  }

  if (editMode) {
    return <InformeFormDialog salidaId={salidaId} onClose={() => { setEditMode(false); onClose() }} informe={informe ?? undefined} mode={informe ? "edit" : "create"} />
  }

  return (
    <Dialog open onOpenChange={() => onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Informe de Salida</DialogTitle>
        </DialogHeader>

        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : isError || !informe ? (
          <div className="text-center py-8 space-y-4">
            <p className="text-muted-foreground">No se ha creado un informe para esta salida</p>
            {canEdit && <Button onClick={() => setEditMode(true)}>Crear informe</Button>}
          </div>
        ) : (
          <div className="space-y-6">
            {/* Cabecera */}
            <div className="flex items-center justify-between flex-wrap gap-2">
              <h2 className="text-lg font-bold">{informe.salidaNombre}</h2>
              <div className="flex items-center gap-2 flex-wrap">
                <Badge variant={informe.seRealizo ? "default" : "destructive"}>
                  {informe.seRealizo ? "Realizada" : "No realizada"}
                </Badge>
                <Badge variant={informe.lograronCumbre ? "default" : "secondary"}>
                  {informe.lograronCumbre ? "Cumbre lograda" : "Sin cumbre"}
                </Badge>
                {informe.validadoPorId && (
                  <Badge variant="secondary" title={informe.validadoEn ? new Date(informe.validadoEn).toLocaleString("es-EC") : ""}>
                    <CheckCircle className="mr-1 h-3 w-3" />
                    Validado{informe.validadoPorNombre ? ` · ${informe.validadoPorNombre}` : ""}
                  </Badge>
                )}
              </div>
            </div>

            {/* Horarios */}
            <div className="grid gap-2 sm:grid-cols-3">
              {[
                { label: "Salida club",      value: informe.horaSalidaClub },
                { label: "Llegada montaña",  value: informe.horaLlegadaMontana },
                { label: "Cumbre",           value: informe.horaCumbre },
                { label: "Inicio descenso",  value: informe.horaInicioDescenso },
                { label: "Llegada autos",    value: informe.horaLlegadaAutos },
                { label: "Regreso club",     value: informe.horaRegresoClub },
              ].map(({ label, value }) => (
                <div key={label}>
                  <p className="text-xs text-muted-foreground">{label}</p>
                  <p className="text-sm flex items-center gap-1"><Clock className="h-3 w-3" />{value ?? "—"}</p>
                </div>
              ))}
            </div>

            {informe.condicionesMeterologicas && (
              <div>
                <p className="text-xs text-muted-foreground">Condiciones meteorológicas</p>
                <p className="text-sm">{informe.condicionesMeterologicas}</p>
              </div>
            )}

            {/* Segmentos de viaje */}
            {informe.segmentos && informe.segmentos.length > 0 && (
              <div className="space-y-2">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Segmentos de viaje</p>
                {informe.segmentos.map((s) => (
                  <div key={s.id} className="rounded-lg border border-border px-3 py-2 text-sm">
                    <p className="font-medium">{s.origen} → {s.destino}</p>
                    {s.alquiloTransporte && (
                      <div className="text-xs text-muted-foreground mt-0.5 space-x-2">
                        {s.tipoTransporte && <span>{s.tipoTransporte.replace(/_/g, " ")}</span>}
                        {s.contactoNombre && (
                          <span>{s.contactoNombre}{s.contactoTelefono ? ` (${s.contactoTelefono})` : ""}</span>
                        )}
                        {s.costoIndividual != null && <span>Costo total: ${s.costoIndividual.toFixed(2)}</span>}
                      </div>
                    )}
                    {!s.alquiloTransporte && (
                      <p className="text-xs text-muted-foreground mt-0.5">Transporte propio</p>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* Guía */}
            <div className="space-y-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Guía</p>
              <p className="text-sm font-medium">{informe.alquiloGuia ? "Contratado" : "No contratado"}</p>
              {informe.alquiloGuia && (
                <div className="text-xs text-muted-foreground space-y-0.5">
                  {informe.contactoGuiaNombre && (
                    <p>{informe.contactoGuiaNombre}
                      {informe.contactoGuiaTelefono && ` · ${informe.contactoGuiaTelefono}`}
                      {informe.contactoGuiaCorreo && ` · ${informe.contactoGuiaCorreo}`}
                    </p>
                  )}
                  {informe.costoGuia != null && <p>Costo: ${informe.costoGuia.toFixed(2)}</p>}
                </div>
              )}
            </div>

            {/* Alojamiento */}
            {(informe.alquiloRefugio || informe.acampo) && (
              <div className="space-y-1">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Alojamiento</p>
                {informe.alquiloRefugio && (
                  <div className="text-sm">
                    <span className="font-medium">Refugio</span>
                    {informe.nombreRefugio && <span>: {informe.nombreRefugio}</span>}
                    {informe.contactoRefugioNombre && (
                      <span className="text-xs text-muted-foreground"> · {informe.contactoRefugioNombre}</span>
                    )}
                    {informe.costoRefugio != null && (
                      <span className="text-xs text-muted-foreground"> · ${informe.costoRefugio.toFixed(2)}</span>
                    )}
                  </div>
                )}
                {informe.acampo && (
                  <div className="text-sm">
                    <span className="font-medium">Camping</span>
                    {informe.nombreCamping && <span>: {informe.nombreCamping}</span>}
                    {informe.contactoCampingNombre && (
                      <span className="text-xs text-muted-foreground"> · {informe.contactoCampingNombre}</span>
                    )}
                    {informe.costoCamping != null && (
                      <span className="text-xs text-muted-foreground"> · ${informe.costoCamping.toFixed(2)}</span>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* Vehículos / Autos */}
            {informe.dondeAutos && informe.dondeAutos !== "NO_AUTOS" && (
              <div className="space-y-1">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Vehículos</p>
                <p className="text-sm font-medium">{informe.dondeAutos.replace(/_/g, " ")}</p>
                {informe.autosDescripcion && <p className="text-xs text-muted-foreground">{informe.autosDescripcion}</p>}
                {informe.autosLinkUbicacion && (
                  <p className="text-xs text-muted-foreground">Ubicación: {informe.autosLinkUbicacion}</p>
                )}
                {informe.costoParqueadero != null && (
                  <p className="text-xs text-muted-foreground">Costo parqueadero: ${informe.costoParqueadero.toFixed(2)}</p>
                )}
              </div>
            )}

            {/* Costos */}
            {(informe.costoTotal != null || informe.costoPorPersona != null) && (
              <div className="rounded-lg bg-muted/50 px-3 py-2 space-y-1">
                {informe.costoTotal != null && (
                  <div className="flex justify-between text-xs">
                    <span className="text-muted-foreground">Costo total del viaje</span>
                    <span>${informe.costoTotal.toFixed(2)}</span>
                  </div>
                )}
                {informe.costoPorPersona != null && (
                  <div className="flex justify-between text-xs font-semibold">
                    <span className="text-muted-foreground">Costo por persona</span>
                    <span>${informe.costoPorPersona.toFixed(2)}</span>
                  </div>
                )}
              </div>
            )}

            {/* Textos */}
            {informe.cronica && (
              <div>
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Crónica</p>
                <p className="text-sm whitespace-pre-wrap mt-1">{informe.cronica}</p>
              </div>
            )}

            {informe.observaciones && (
              <div>
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Observaciones</p>
                <p className="text-sm whitespace-pre-wrap mt-1">{informe.observaciones}</p>
              </div>
            )}

            {informe.comentariosVarios && (
              <div>
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Comentarios varios</p>
                <p className="text-sm whitespace-pre-wrap mt-1">{informe.comentariosVarios}</p>
              </div>
            )}

            {/* Reconocimientos */}
            {informe.reconocimientos.length > 0 && (
              <div className="space-y-2">
                <h3 className="text-sm font-semibold border-b border-border pb-2">Reconocimientos ({informe.reconocimientos.length})</h3>
                {informe.reconocimientos.map((r) => (
                  <div key={r.id} className="flex items-center justify-between rounded-lg border border-border p-3">
                    <div className="flex items-center gap-2">
                      {r.tipo === "DESTACADO" ? (
                        <Award className="h-4 w-4 text-yellow-500" />
                      ) : (
                        <AlertTriangle className="h-4 w-4 text-red-500" />
                      )}
                      <div>
                        <p className="text-sm font-medium">{r.socioApellido}, {r.socioNombre}</p>
                        <p className="text-xs text-muted-foreground">{r.motivo}</p>
                      </div>
                    </div>
                    <Badge variant={r.tipo === "DESTACADO" ? "default" : "destructive"}>{r.tipo}</Badge>
                  </div>
                ))}
              </div>
            )}

            <div className="flex flex-wrap justify-end gap-2 pt-4 border-t border-border">
              {/* PDF buttons */}
              {informe.validadoPorId && (
                <>
                  {informe.documentoId ? (
                    <>
                      <Button
                        variant="outline"
                        onClick={handleDescargarPdf}
                        disabled={isDownloading}
                        className="gap-2"
                      >
                        <Download className="h-4 w-4" />
                        {isDownloading ? "Descargando..." : "Descargar PDF"}
                      </Button>
                      {canGeneratePdf && (
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={handleGenerarPdf}
                          disabled={generarPdfMutation.isPending}
                          title="Regenerar PDF"
                        >
                          <RefreshCw className={`h-4 w-4 ${generarPdfMutation.isPending ? "animate-spin" : ""}`} />
                        </Button>
                      )}
                    </>
                  ) : (
                    canGeneratePdf && (
                      <Button
                        variant="outline"
                        onClick={handleGenerarPdf}
                        disabled={generarPdfMutation.isPending}
                        className="gap-2"
                      >
                        <FileText className="h-4 w-4" />
                        {generarPdfMutation.isPending ? "Generando..." : "Generar PDF"}
                      </Button>
                    )
                  )}
                </>
              )}
              {canEdit && !informe.validadoPorId && (
                <Button variant="outline" onClick={() => setEditMode(true)}>Editar</Button>
              )}
              {canValidate && !informe.validadoPorId && (
                <ValidarButton salidaId={salidaId} onDone={onClose} />
              )}
              <Button variant="outline" onClick={onClose}>Cerrar</Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function ValidarButton({ salidaId, onDone }: { salidaId: string; onDone: () => void }) {
  const validarMutation = useValidarInforme(salidaId)
  const handleValidar = async () => {
    try { await validarMutation.mutateAsync(); toast.success("Informe validado"); onDone() }
    catch (error) { console.error(error); toast.error("Error al validar") }
  }
  return <Button onClick={handleValidar} disabled={validarMutation.isPending}>{validarMutation.isPending ? "Validando..." : "Validar informe"}</Button>
}

// ─── Informe Form Dialog ────────────────────────────

function InformeFormDialog({ salidaId, onClose, informe, mode }: {
  salidaId: string; onClose: () => void; informe?: import("@/types/informes").InformeResponse; mode: "create" | "edit"
}) {
  const createMutation = useCreateInforme(salidaId)
  const updateMutation = useUpdateInforme(salidaId)

  const [form, setForm] = useState({
    seRealizo: "true",
    lograronCumbre: "true",
    condicionesMeterologicas: "", horaSalidaClub: "", horaLlegadaMontana: "",
    horaCumbre: "", horaInicioDescenso: "", horaLlegadaAutos: "", horaRegresoClub: "",
    cronica: "", observaciones: "", comentariosVarios: "",
    alquiloGuia: "false",
  })

  useEffect(() => {
    if (mode === "edit" && informe) {
      setForm({
        seRealizo: String(informe.seRealizo),
        lograronCumbre: String(informe.lograronCumbre),
        condicionesMeterologicas: informe.condicionesMeterologicas ?? "",
        horaSalidaClub: informe.horaSalidaClub ?? "",
        horaLlegadaMontana: informe.horaLlegadaMontana ?? "",
        horaCumbre: informe.horaCumbre ?? "",
        horaInicioDescenso: informe.horaInicioDescenso ?? "",
        horaLlegadaAutos: informe.horaLlegadaAutos ?? "",
        horaRegresoClub: informe.horaRegresoClub ?? "",
        cronica: informe.cronica ?? "",
        observaciones: informe.observaciones ?? "",
        comentariosVarios: informe.comentariosVarios ?? "",
        alquiloGuia: String(informe.alquiloGuia),
      })
    }
  }, [mode, informe])

  const update = (field: string, value: string) => setForm((p) => ({ ...p, [field]: value }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    // Preserve existing segmentos when editing; default empty array for new
    const existingSegmentos = informe?.segmentos?.map((s) => ({
      origen: s.origen,
      destino: s.destino,
      alquiloTransporte: s.alquiloTransporte,
      tipoTransporte: s.tipoTransporte ?? null,
      costoIndividual: s.costoIndividual ?? null,
      contactoId: s.contactoId ?? null,
    })) ?? [{ origen: "Club Sadday", destino: "", alquiloTransporte: false, tipoTransporte: null, costoIndividual: null, contactoId: null }]

    const payload = {
      seRealizo: form.seRealizo === "true",
      lograronCumbre: form.lograronCumbre === "true",
      condicionesMeterologicas: form.condicionesMeterologicas || undefined,
      horaSalidaClub: form.horaSalidaClub || undefined,
      horaLlegadaMontana: form.horaLlegadaMontana || undefined,
      horaCumbre: form.horaCumbre || undefined,
      horaInicioDescenso: form.horaInicioDescenso || undefined,
      horaLlegadaAutos: form.horaLlegadaAutos || undefined,
      horaRegresoClub: form.horaRegresoClub || undefined,
      cronica: form.cronica || undefined,
      observaciones: form.observaciones || undefined,
      comentariosVarios: form.comentariosVarios || undefined,
      segmentos: existingSegmentos,
      alquiloGuia: form.alquiloGuia === "true",
      alquiloRefugio: informe?.alquiloRefugio ?? false,
      acampo: informe?.acampo ?? false,
    }
    try {
      if (mode === "create") { await createMutation.mutateAsync(payload); toast.success("Informe creado") }
      else { await updateMutation.mutateAsync(payload); toast.success("Informe actualizado") }
      onClose()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al guardar")
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open onOpenChange={() => onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "Crear informe" : "Editar informe"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>¿Se realizó la salida? *</Label>
              <Select value={form.seRealizo} onValueChange={(v) => update("seRealizo", v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="true">Sí</SelectItem>
                  <SelectItem value="false">No</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>¿Lograron la cumbre?</Label>
              <Select value={form.lograronCumbre} onValueChange={(v) => update("lograronCumbre", v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="true">Sí</SelectItem>
                  <SelectItem value="false">No</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <fieldset className="space-y-3">
            <legend className="text-sm font-semibold">Horarios</legend>
            <div className="grid gap-3 sm:grid-cols-3">
              {[
                { key: "horaSalidaClub", label: "Salida club" },
                { key: "horaLlegadaMontana", label: "Llegada montaña" },
                { key: "horaCumbre", label: "Cumbre" },
                { key: "horaInicioDescenso", label: "Inicio descenso" },
                { key: "horaLlegadaAutos", label: "Llegada autos" },
                { key: "horaRegresoClub", label: "Regreso club" },
              ].map(({ key, label }) => (
                <div key={key} className="space-y-1">
                  <Label className="text-xs">{label}</Label>
                  <Input type="time" value={form[key as keyof typeof form]} onChange={(e) => update(key, e.target.value)} />
                </div>
              ))}
            </div>
          </fieldset>

          <div className="space-y-2">
            <Label>Condiciones meteorológicas</Label>
            <textarea className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm" rows={2} value={form.condicionesMeterologicas} onChange={(e) => update("condicionesMeterologicas", e.target.value)} />
          </div>

          <div className="space-y-2">
            <Label>¿Se contrató guía? *</Label>
            <Select value={form.alquiloGuia} onValueChange={(v) => update("alquiloGuia", v)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="false">No</SelectItem>
                <SelectItem value="true">Sí</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label>Crónica</Label>
            <textarea className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm" rows={4} value={form.cronica} onChange={(e) => update("cronica", e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Observaciones</Label>
            <textarea className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm" rows={2} value={form.observaciones} onChange={(e) => update("observaciones", e.target.value)} />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Guardando..." : mode === "create" ? "Crear" : "Guardar"}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
