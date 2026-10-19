import { describe, it, expect } from "vitest"
import { formatearDesnivel } from "./formatear-desnivel"

describe("formatearDesnivel", () => {
  it("muestra las dos cifras cuando se conocen las dos", () => {
    // La travesia Mojanda-San Pablo: sube 333 y baja 1248. Con un solo numero
    // la ruta se describia como «333 m», que es justo el motivo de FR-024.
    expect(formatearDesnivel(333, 1248)).toBe("↑ 333 m · ↓ 1248 m")
  })

  it("trata el cero como un dato, no como ausencia", () => {
    // sincholagua-camping sube 1038 y baja 0 porque termina en el campamento.
    // Un `if (negativo)` lo habria ocultado.
    expect(formatearDesnivel(1038, 0)).toBe("↑ 1038 m · ↓ 0 m")
  })

  it("distingue el cero del desconocido", () => {
    expect(formatearDesnivel(1038, null)).toBe("↑ 1038 m")
    expect(formatearDesnivel(1038, undefined)).toBe("↑ 1038 m")
  })

  it("muestra solo la bajada si es lo unico que se sabe", () => {
    expect(formatearDesnivel(null, 1248)).toBe("↓ 1248 m")
  })

  it("devuelve un guion cuando no se sabe nada", () => {
    expect(formatearDesnivel(null, null)).toBe("—")
  })

  it("un positivo de cero tambien es un dato", () => {
    expect(formatearDesnivel(0, 0)).toBe("↑ 0 m · ↓ 0 m")
  })
})
