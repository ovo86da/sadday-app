import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { MotivosNivel } from "@/components/motivos-nivel"
import type { EvaluacionNivel } from "@/types/salidas"

/**
 * Lo que se le muestra a quien aprueba una inscripción retenida (FR-028).
 *
 * El riesgo que cubre no es de maquetación: es que «no alcanza» y «no lo sé»
 * aparezcan como la misma cosa. Si un hueco de datos se pinta como un riesgo
 * medido, alguien firma creyendo que ha valorado algo que nadie miró.
 */
describe("MotivosNivel", () => {
  const excede: EvaluacionNivel = {
    apto: false,
    hallazgos: [
      {
        escala: "Hielo",
        exigeRuta: "WI3",
        permiteNivel: "WI2",
        tipo: "EXCEDE",
        detalle: "La ruta pide WI3 y el nivel del socio llega a WI2.",
      },
    ],
  }

  const hueco: EvaluacionNivel = {
    apto: false,
    hallazgos: [
      {
        escala: "Senderismo",
        exigeRuta: "Moderado",
        permiteNivel: null,
        tipo: "SIN_COMPROBAR",
        detalle:
          "El nivel no tiene fijado un máximo de senderismo, así que no se puede comprobar si Moderado lo excede.",
      },
    ],
  }

  it("nombra la escala y los dos valores, no solo que falla", () => {
    render(<MotivosNivel evaluacion={excede} />)

    expect(screen.getByText("Hielo:")).toBeInTheDocument()
    expect(screen.getByText("WI3")).toBeInTheDocument()
    expect(screen.getByText("WI2")).toBeInTheDocument()
  })

  it("separa «no alcanza» de «no se pudo comprobar»", () => {
    const { rerender } = render(<MotivosNivel evaluacion={excede} />)
    expect(screen.getByText(/excede el nivel del socio/i)).toBeInTheDocument()
    expect(screen.queryByText(/no se pudo comprobar/i)).not.toBeInTheDocument()

    rerender(<MotivosNivel evaluacion={hueco} />)
    expect(screen.getByText(/no se pudo comprobar/i)).toBeInTheDocument()
    expect(screen.queryByText(/excede el nivel del socio/i)).not.toBeInTheDocument()
  })

  it("dice que un hueco frena igual, para que no se lea como un visto bueno", () => {
    render(<MotivosNivel evaluacion={hueco} />)

    expect(screen.getByText(/no es un visto bueno/i)).toBeInTheDocument()
    expect(screen.getByText(/se detiene igual/i)).toBeInTheDocument()
  })

  it("agrupa los dos tipos cuando se dan a la vez", () => {
    render(<MotivosNivel evaluacion={{ apto: false, hallazgos: [...excede.hallazgos, ...hueco.hallazgos] }} />)

    expect(screen.getByText(/excede el nivel del socio/i)).toBeInTheDocument()
    expect(screen.getByText(/no se pudo comprobar/i)).toBeInTheDocument()
  })

  it("no pinta nada cuando no hay nada que decir", () => {
    const { container, rerender } = render(<MotivosNivel evaluacion={{ apto: true, hallazgos: [] }} />)
    expect(container).toBeEmptyDOMElement()

    rerender(<MotivosNivel evaluacion={null} />)
    expect(container).toBeEmptyDOMElement()
  })
})
