import { useState } from "react"
import { AlertTriangle, ArrowDown, ArrowUp, Minus } from "lucide-react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { useCambiosUmbralPendientes, useResolverCambioUmbral } from "@/hooks/use-mountains"
import type { CambioEscala, CambioUmbralResponse } from "@/types/mountains"

/**
 * Los cambios de umbral que esperan una segunda aprobación (FR-026).
 *
 * Lo importante de este componente no es la lista, es **qué se ve antes del
 * botón**. Aprobar un umbral de seguridad sin ver qué dice es exactamente el
 * descuido que FR-026 existe para evitar, así que se enseñan las siete escalas
 * con el valor vigente y el propuesto, marcando cuáles cambian de verdad: si de
 * siete solo se toca una, eso tiene que saltar a la vista en vez de esconderse
 * entre seis filas iguales.
 *
 * Y se dice si cada cambio **relaja o endurece**, aunque el flujo de aprobación
 * sea el mismo para los dos. Es la información que hace falta para decidir, no
 * una regla de permisos.
 */
export function CambiosUmbralPendientes({ puedeVer }: { puedeVer: boolean }) {
  const { data: cambios, isLoading } = useCambiosUmbralPendientes(puedeVer)
  const { aprobar, rechazar, cancelar } = useResolverCambioUmbral()
  const [rechazando, setRechazando] = useState<string | null>(null)
  const [motivo, setMotivo] = useState("")

  if (!puedeVer || isLoading || !cambios || cambios.length === 0) return null

  const resolver = async (accion: () => Promise<unknown>, ok: string) => {
    try {
      await accion()
      toast.success(ok)
      setRechazando(null)
      setMotivo("")
    } catch (error) {
      toast.error((error as { response?: { data?: { message?: string } } })
        .response?.data?.message ?? "No se pudo completar la acción")
    }
  }

  return (
    <div className="mb-6 space-y-4">
      {cambios.map((c) => (
        <div key={c.id} className="rounded-lg border border-amber-500/40 bg-amber-500/5 p-4">
          <div className="flex items-start gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-500" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-foreground">
                Cambio pendiente en «{c.nivelSocioNombre}»
              </p>
              <p className="text-xs text-muted-foreground">
                Lo pidió {c.solicitadoPorNombre} · {antiguedad(c.solicitadoEn)}.
                El umbral vigente sigue siendo el anterior.
              </p>
            </div>
          </div>

          <table className="mt-3 w-full text-xs">
            <thead>
              <tr className="text-muted-foreground">
                <th className="py-1 text-left font-medium">Escala</th>
                <th className="py-1 text-left font-medium">Ahora</th>
                <th className="py-1 text-left font-medium">Propuesto</th>
                <th className="py-1 text-left font-medium">Efecto</th>
              </tr>
            </thead>
            <tbody>
              {c.escalas.map((e) => <FilaEscala key={e.escala} escala={e} />)}
            </tbody>
          </table>

          <div className="mt-3 flex flex-wrap items-center gap-2">
            {c.puedoAprobar ? (
              <>
                <Button size="sm" disabled={aprobar.isPending}
                        onClick={() => resolver(() => aprobar.mutateAsync(c.id),
                                                "Cambio aprobado y aplicado")}>
                  Aprobar y aplicar
                </Button>
                <Button size="sm" variant="outline"
                        onClick={() => setRechazando(rechazando === c.id ? null : c.id)}>
                  Rechazar
                </Button>
              </>
            ) : (
              // Quien lo pidió no puede aprobarlo, y conviene decir por qué en
              // vez de limitarse a no dibujar el botón.
              <p className="text-xs text-muted-foreground">
                {c.puedoCancelar
                  ? "Lo pediste tú, así que tiene que aprobarlo otra persona de la directiva."
                  : "No puedes resolver este cambio."}
              </p>
            )}
            {c.puedoCancelar && (
              <Button size="sm" variant="ghost" disabled={cancelar.isPending}
                      onClick={() => resolver(() => cancelar.mutateAsync(c.id),
                                              "Cambio cancelado")}>
                Cancelar mi solicitud
              </Button>
            )}
          </div>

          {rechazando === c.id && (
            <div className="mt-3 space-y-2">
              <textarea
                className="w-full min-h-[64px] rounded-lg border border-border bg-background px-3 py-2 text-xs"
                placeholder="Por qué se rechaza. Quien lo pidió va a leerlo."
                maxLength={1000}
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                autoFocus
              />
              <Button size="sm" variant="destructive"
                      disabled={!motivo.trim() || rechazar.isPending}
                      onClick={() => resolver(
                        () => rechazar.mutateAsync({ id: c.id, motivo }),
                        "Cambio rechazado")}>
                Confirmar rechazo
              </Button>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function FilaEscala({ escala }: { escala: CambioEscala }) {
  const cambia = escala.sentido !== "IGUAL"
  const relaja = escala.sentido === "RELAJA"
  return (
    <tr className={cambia ? "font-medium text-foreground" : "text-muted-foreground"}>
      <td className="py-1">{escala.escala}</td>
      <td className="py-1">{escala.valorActual ?? "—"}</td>
      <td className="py-1">{escala.valorPropuesto}</td>
      <td className="py-1">
        {!cambia ? (
          <span className="inline-flex items-center gap-1"><Minus className="h-3 w-3" /> sin cambio</span>
        ) : relaja ? (
          // Relajar es lo que de verdad pone a alguien en terreno nuevo, así
          // que se marca distinto de endurecer aunque el flujo sea el mismo.
          <span className="inline-flex items-center gap-1 text-amber-600 dark:text-amber-500">
            <ArrowUp className="h-3 w-3" /> permite más
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 text-sky-600 dark:text-sky-400">
            <ArrowDown className="h-3 w-3" /> exige más
          </span>
        )}
      </td>
    </tr>
  )
}

/** «hace 3 días». Los cambios no caducan, así que su antigüedad es lo que avisa. */
function antiguedad(iso: string): string {
  const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
  if (dias <= 0) return "hoy"
  if (dias === 1) return "hace 1 día"
  return `hace ${dias} días`
}

export type { CambioUmbralResponse }
