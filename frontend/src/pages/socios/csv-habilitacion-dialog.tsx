import { useRef, useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { useUploadCsvHabilitacion } from "@/hooks/use-socios"
import { toast } from "sonner"
import { Upload, FileText, AlertCircle, CheckCircle2, Download, UserX } from "lucide-react"
import type { CsvHabilitacionResult } from "@/types/socios"

interface Props {
  open: boolean
  onClose: () => void
}

export function CsvHabilitacionDialog({ open, onClose }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [resultado, setResultado] = useState<CsvHabilitacionResult | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const uploadMutation = useUploadCsvHabilitacion()

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null
    if (f && !f.name.toLowerCase().endsWith(".csv")) {
      toast.error("El archivo debe ser un CSV (.csv)")
      return
    }
    setFile(f)
    setResultado(null)
  }

  const handleUpload = async () => {
    if (!file) return
    try {
      const res = await uploadMutation.mutateAsync({ file })
      setResultado(res)
      toast.success(`Proceso completado: ${res.habilitados + res.deshabilitados} cambios aplicados`)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg ?? "Error al procesar el CSV")
    }
  }

  const handleClose = () => {
    setFile(null)
    setResultado(null)
    if (fileInputRef.current) fileInputRef.current.value = ""
    onClose()
  }

  const handleDescargarPlantilla = () => {
    const csvContent =
      "Nombre,Cedula,Estado\n" +
      "Juan Pérez,0102030405,Habilitado\n" +
      "María López,0506070809,Habilitado\n" +
      "Ana Torres,1709876543,Inhabilitado\n" +
      "Carlos Ruiz,0987654321,Licencia\n" +
      "Sofia Mora,1122334455,Re-inscripcion"
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" })
    const url = URL.createObjectURL(blob)
    const link = document.createElement("a")
    link.href = url
    link.download = "plantilla_habilitacion.csv"
    link.click()
    URL.revokeObjectURL(url)
  }

  const noEncontrados = resultado?.errores.filter((e) =>
    e.motivo === "Cédula no encontrada en el sistema"
  ) ?? []

  const otrosErrores = resultado?.errores.filter((e) =>
    e.motivo !== "Cédula no encontrada en el sistema"
  ) ?? []

  return (
    <Dialog open={open} onOpenChange={(v) => !v && handleClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Carga masiva de habilitación</DialogTitle>
        </DialogHeader>

        <div className="space-y-5">
          {/* Zona de archivo */}
          {!resultado && (
            <div>
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm font-medium text-foreground">Archivo CSV</p>
                <button
                  onClick={handleDescargarPlantilla}
                  className="flex items-center gap-1 text-xs text-primary hover:underline"
                >
                  <Download className="h-3 w-3" /> Descargar plantilla
                </button>
              </div>

              <div
                className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-border bg-muted/20 px-6 py-8 cursor-pointer hover:border-primary/50 transition-colors"
                onClick={() => fileInputRef.current?.click()}
              >
                {file ? (
                  <div className="flex items-center gap-2 text-sm">
                    <FileText className="h-5 w-5 text-primary" />
                    <span className="font-medium text-foreground">{file.name}</span>
                    <span className="text-muted-foreground">
                      ({(file.size / 1024).toFixed(1)} KB)
                    </span>
                  </div>
                ) : (
                  <>
                    <Upload className="h-8 w-8 text-muted-foreground mb-2" />
                    <p className="text-sm text-muted-foreground text-center">
                      Haz clic para seleccionar un archivo <span className="font-medium text-foreground">.csv</span>
                    </p>
                    <p className="text-xs text-muted-foreground mt-1">Máx. 500 KB · 1 000 filas</p>
                  </>
                )}
              </div>

              <input
                ref={fileInputRef}
                type="file"
                accept=".csv"
                className="hidden"
                onChange={handleFileChange}
              />

              <p className="mt-2 text-xs text-muted-foreground">
                Formato esperado: encabezado{" "}
                <code className="bg-muted px-1 rounded">Nombre,Cedula,Estado</code>
                {" "}— Estado válido por fila:{" "}
                <code className="bg-muted px-1 rounded">Habilitado</code>,{" "}
                <code className="bg-muted px-1 rounded">Inhabilitado</code>,{" "}
                <code className="bg-muted px-1 rounded">Vitalicio</code>,{" "}
                <code className="bg-muted px-1 rounded">Licencia</code>,{" "}
                <code className="bg-muted px-1 rounded">Re-inscripcion</code>.
                Los socios Vitalicios no se modifican.
              </p>
            </div>
          )}

          {/* Botón de envío */}
          {!resultado && (
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={handleClose}>Cancelar</Button>
              <Button
                onClick={handleUpload}
                disabled={!file || uploadMutation.isPending}
              >
                {uploadMutation.isPending ? "Procesando..." : "Procesar CSV"}
              </Button>
            </div>
          )}

          {/* Resultado */}
          {resultado && (
            <div className="space-y-4">
              {/* Resumen */}
              <div className="rounded-lg border border-border bg-card p-4 space-y-3">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="h-5 w-5 text-green-500" />
                  <p className="font-semibold text-foreground">Proceso completado</p>
                </div>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {[
                    { label: "Revisados", value: resultado.procesados, color: "text-foreground" },
                    { label: "Habilitados", value: resultado.habilitados, color: "text-primary" },
                    { label: "Deshabilitados", value: resultado.deshabilitados, color: "text-destructive" },
                    { label: "Sin cambio", value: resultado.sinCambio, color: "text-muted-foreground" },
                  ].map(({ label, value, color }) => (
                    <div key={label} className="text-center rounded-md border border-border py-2">
                      <p className={`text-xl font-bold ${color}`}>{value}</p>
                      <p className="text-xs text-muted-foreground">{label}</p>
                    </div>
                  ))}
                </div>
              </div>

              {/* Socios no encontrados */}
              {noEncontrados.length > 0 && (
                <div className="rounded-lg border border-yellow-500/40 bg-yellow-50/10 p-4 space-y-3">
                  <div className="flex items-center gap-2">
                    <UserX className="h-4 w-4 text-yellow-500" />
                    <p className="text-sm font-semibold text-foreground">
                      {noEncontrados.length} socio{noEncontrados.length > 1 ? "s" : ""} no encontrado{noEncontrados.length > 1 ? "s" : ""} en el sistema
                    </p>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Estos socios no están registrados. Puedes invitarlos para que activen su cuenta.
                  </p>
                  <ul className="divide-y divide-border rounded-md border border-border bg-card max-h-40 overflow-y-auto">
                    {noEncontrados.map((e) => (
                      <li key={e.fila} className="flex items-center justify-between px-3 py-2 gap-3">
                        <span className="text-sm font-medium text-foreground truncate">{e.nombre || "—"}</span>
                        <span className="text-xs font-mono text-muted-foreground shrink-0">{e.cedula}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Otros errores */}
              {otrosErrores.length > 0 && (
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <AlertCircle className="h-4 w-4 text-destructive" />
                    <p className="text-sm font-medium text-foreground">
                      {otrosErrores.length} fila{otrosErrores.length > 1 ? "s" : ""} con error
                    </p>
                  </div>
                  <ul className="divide-y divide-border rounded-lg border border-border bg-card max-h-40 overflow-y-auto">
                    {otrosErrores.map((e) => (
                      <li key={e.fila} className="flex items-start gap-3 px-3 py-2">
                        <Badge variant="outline" className="shrink-0 text-xs">Fila {e.fila}</Badge>
                        <div className="min-w-0">
                          {e.cedula && (
                            <p className="text-xs font-mono text-muted-foreground">{e.cedula}</p>
                          )}
                          <p className="text-xs text-foreground">{e.motivo}</p>
                        </div>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Acciones post-resultado */}
              <div className="flex justify-between">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => { setFile(null); setResultado(null); if (fileInputRef.current) fileInputRef.current.value = "" }}
                >
                  Procesar otro archivo
                </Button>
                <Button size="sm" onClick={handleClose}>Cerrar</Button>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
