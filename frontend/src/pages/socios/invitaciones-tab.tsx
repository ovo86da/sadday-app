import { useInvitaciones, useReenviarInvitacionPendiente, useEliminarInvitacion } from "@/hooks/use-socios"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import { toast } from "sonner"
import { Mail, RefreshCw, Clock, Loader2, Trash2 } from "lucide-react"
import type { InvitacionPendiente } from "@/types/socios"

function formatFecha(iso: string) {
  return new Date(iso).toLocaleString("es-EC", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  })
}

function tiempoRelativo(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  const horas = Math.floor(diff / 3600000)
  if (horas < 1) return "hace menos de 1 h"
  if (horas < 24) return `hace ${horas} h`
  const dias = Math.floor(horas / 24)
  return `hace ${dias} d`
}

export function InvitacionesTab() {
  const { data: invitaciones, isLoading, isError } = useInvitaciones()
  const reenviarMutation = useReenviarInvitacionPendiente()
  const eliminarMutation = useEliminarInvitacion()

  const handleReenviar = async (inv: InvitacionPendiente) => {
    try {
      await reenviarMutation.mutateAsync(inv.id)
      toast.success(`Invitación reenviada a ${inv.correo}`)
    } catch (error) { console.error(error);
      toast.error("Error al reenviar la invitación")
    }
  }

  const handleEliminar = async (inv: InvitacionPendiente) => {
    if (!confirm(`¿Eliminar la invitación enviada a ${inv.correo} (${inv.cedula})? El enlace dejará de funcionar inmediatamente.`)) return
    try {
      await eliminarMutation.mutateAsync(inv.id)
      toast.success(`Invitación de ${inv.correo} eliminada`)
    } catch (error) { console.error(error);
      toast.error("Error al eliminar la invitación")
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin mr-2" />
        Cargando invitaciones...
      </div>
    )
  }

  if (isError) {
    return (
      <div className="py-16 text-center text-destructive text-sm">
        Error al cargar las invitaciones.
      </div>
    )
  }

  if (!invitaciones || invitaciones.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-muted-foreground gap-2">
        <Mail className="h-8 w-8" />
        <p className="text-sm">No hay invitaciones pendientes.</p>
      </div>
    )
  }

  const pendientes = invitaciones.filter(i => i.estado === "PENDIENTE").length
  const expiradas  = invitaciones.filter(i => i.estado === "EXPIRADO").length

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4 text-sm text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <Clock className="h-3.5 w-3.5" />
          {pendientes} pendiente{pendientes !== 1 ? "s" : ""}
        </span>
        {expiradas > 0 && (
          <span className="text-destructive">
            {expiradas} expirada{expiradas !== 1 ? "s" : ""}
          </span>
        )}
      </div>

      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Cédula</TableHead>
              <TableHead>Correo</TableHead>
              <TableHead>Teléfono</TableHead>
              <TableHead>Nombre (CSV)</TableHead>
              <TableHead>Enviado</TableHead>
              <TableHead>Expira</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {invitaciones.map((inv) => (
              <TableRow key={inv.id}>
                <TableCell className="font-mono text-sm">{inv.cedula}</TableCell>
                <TableCell className="text-sm">{inv.correo}</TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {inv.telefono ?? "—"}
                </TableCell>
                <TableCell className="text-sm">
                  {inv.fromCsvImport && inv.nombre
                    ? `${inv.nombre} ${inv.apellido ?? ""}`.trim()
                    : <span className="text-muted-foreground">—</span>
                  }
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {tiempoRelativo(inv.creadoEn)}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatFecha(inv.expiresAt)}
                </TableCell>
                <TableCell>
                  <Badge variant={inv.estado === "PENDIENTE" ? "default" : "destructive"}>
                    {inv.estado === "PENDIENTE" ? "Pendiente" : "Expirado"}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1">
                    {inv.estado === "PENDIENTE" && (
                      <Button
                        variant="ghost"
                        size="icon"
                        disabled={reenviarMutation.isPending}
                        onClick={() => handleReenviar(inv)}
                        title="Reenviar invitación"
                      >
                        <RefreshCw className="h-4 w-4" />
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="icon"
                      disabled={eliminarMutation.isPending}
                      onClick={() => handleEliminar(inv)}
                      title="Eliminar invitación"
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
