import { useState, useEffect } from "react"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { toast } from "sonner"
import { useCreateMountain, useUpdateMountain } from "@/hooks/use-mountains"
import type { MountainSummary } from "@/types/mountains"

interface Props {
  open: boolean
  onClose: () => void
  mode: "create" | "edit"
  mountain?: MountainSummary
}

export function MountainFormDialog({ open, onClose, mode, mountain }: Props) {
  const createMutation = useCreateMountain()
  const updateMutation = useUpdateMountain(mountain?.id ?? 0)

  const [nombre, setNombre] = useState("")
  const [region, setRegion] = useState("")
  const [altitud, setAltitud] = useState("")
  const [pais, setPais] = useState("Ecuador")

  useEffect(() => {
    if (mode === "edit" && mountain) {
      setNombre(mountain.nombre)
      setRegion(mountain.region)
      setAltitud(String(mountain.altitud))
      setPais(mountain.pais)
    } else if (mode === "create") {
      setNombre("")
      setRegion("")
      setAltitud("")
      setPais("Ecuador")
    }
  }, [mode, mountain, open])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const payload = { nombre, region, altitud: Number(altitud), pais }

    try {
      if (mode === "create") {
        await createMutation.mutateAsync(payload)
        toast.success("Montaña creada")
      } else {
        await updateMutation.mutateAsync(payload)
        toast.success("Montaña actualizada")
      }
      onClose()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al guardar")
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "Nueva montaña" : "Editar montaña"}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="nombre">Nombre *</Label>
            <Input id="nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Chimborazo" required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="region">Región *</Label>
            <Input id="region" value={region} onChange={(e) => setRegion(e.target.value)} placeholder="Andes" required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="altitud">Altitud (msnm) *</Label>
            <Input id="altitud" type="number" min={1} value={altitud} onChange={(e) => setAltitud(e.target.value)} placeholder="6263" required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="pais">País *</Label>
            <Input id="pais" value={pais} onChange={(e) => setPais(e.target.value)} placeholder="Ecuador" required />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Guardando..." : mode === "create" ? "Crear" : "Guardar cambios"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
