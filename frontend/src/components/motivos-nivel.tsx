import { AlertTriangle, HelpCircle } from "lucide-react"
import type { EvaluacionNivel } from "@/types/salidas"

/**
 * Por qué una inscripción está retenida, escala por escala (FR-028).
 *
 * Existe porque «nivel insuficiente» no sirve para decidir. Quien aprueba
 * necesita saber qué escala no da y por cuánto —«la ruta pide WI3 y su nivel
 * llega a WI2»— y, sobre todo, si el sistema no ha podido comprobar algo.
 *
 * Esos dos casos se pintan distinto a propósito. Uno es un juicio («no
 * alcanza») y el otro es la ausencia de un dato («no lo sé»): juntarlos en un
 * mismo aviso ámbar llevaría a aprobar un hueco de datos creyendo que se está
 * aceptando un riesgo medido.
 *
 * Las frases vienen del servidor. El orden de las escalas depende del `rank` de
 * cada catálogo, que el cliente no conoce, así que aquí no se recalcula nada.
 */
export function MotivosNivel({ evaluacion }: { evaluacion: EvaluacionNivel | null }) {
  if (!evaluacion || evaluacion.hallazgos.length === 0) return null

  const excede = evaluacion.hallazgos.filter((h) => h.tipo === "EXCEDE")
  const sinComprobar = evaluacion.hallazgos.filter((h) => h.tipo === "SIN_COMPROBAR")

  return (
    <div className="space-y-2 rounded-md border border-border bg-muted/40 p-3">
      {excede.length > 0 && (
        <section>
          <h4 className="flex items-center gap-1.5 text-xs font-semibold text-amber-700">
            <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            La ruta excede el nivel del socio
          </h4>
          <ul className="mt-1 space-y-1">
            {excede.map((h, i) => (
              <li key={`excede-${h.escala ?? i}`} className="flex flex-wrap items-baseline gap-x-2 text-xs">
                {h.escala && <span className="font-medium">{h.escala}:</span>}
                <span className="text-muted-foreground">
                  la ruta pide <strong className="text-foreground">{h.exigeRuta}</strong>
                  {" · "}su nivel llega a <strong className="text-foreground">{h.permiteNivel}</strong>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {sinComprobar.length > 0 && (
        <section>
          <h4 className="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
            <HelpCircle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            Esto no se pudo comprobar
          </h4>
          <ul className="mt-1 space-y-1">
            {sinComprobar.map((h, i) => (
              <li key={`hueco-${h.escala ?? i}`} className="text-xs text-muted-foreground">
                {h.detalle}
              </li>
            ))}
          </ul>
          <p className="mt-1.5 text-xs italic text-muted-foreground">
            Falta un dato, no es un visto bueno: la inscripción se detiene igual.
          </p>
        </section>
      )}
    </div>
  )
}
