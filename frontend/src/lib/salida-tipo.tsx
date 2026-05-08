import { Mountain, Bike, Users, CalendarDays, TrendingUp, Footprints } from "lucide-react"
import type { LucideIcon } from "lucide-react"

export interface TipoSalidaDisplay {
  /** Etiqueta corta para chips/badges */
  label: string
  Icon: LucideIcon
  /** Clases para badge inline con borde */
  badgeClasses: string
}

/** Deriva la visualización del tipo de salida a partir del nombre descriptivo. */
export function getTipoSalidaDisplay(nombre: string): TipoSalidaDisplay {
  const n = nombre.toLowerCase()

  if (n.includes("ciclismo")) {
    return {
      label: "Ciclismo",
      Icon: Bike,
      badgeClasses:
        "bg-blue-100 text-blue-700 border-blue-300/70 dark:bg-blue-900/30 dark:text-blue-400",
    }
  }
  if (n.includes("alta montaña") || n.includes("5000")) {
    return {
      label: "Alpinismo",
      Icon: Mountain,
      badgeClasses:
        "bg-orange-100 text-orange-700 border-orange-300/70 dark:bg-orange-900/30 dark:text-orange-400",
    }
  }
  if (n.includes("montaña") || n.includes("cumbre")) {
    return {
      label: "Montaña",
      Icon: Mountain,
      badgeClasses:
        "bg-amber-100 text-amber-700 border-amber-300/70 dark:bg-amber-900/30 dark:text-amber-400",
    }
  }
  if (n.includes("escalada") || n.includes("roca") || n.includes("boulder")) {
    return {
      label: "Escalada",
      Icon: TrendingUp,
      badgeClasses:
        "bg-red-100 text-red-700 border-red-300/70 dark:bg-red-900/30 dark:text-red-400",
    }
  }
  if (
    n.includes("reunión") ||
    n.includes("reunion") ||
    n.includes("semanal") ||
    n.includes("extraordinaria")
  ) {
    return {
      label: "Reunión",
      Icon: Users,
      badgeClasses:
        "bg-slate-100 text-slate-600 border-slate-300/70 dark:bg-slate-800/50 dark:text-slate-400",
    }
  }
  if (
    n.includes("evento") ||
    n.includes("celebración") ||
    n.includes("celebracion") ||
    n.includes("especial")
  ) {
    return {
      label: "Evento",
      Icon: CalendarDays,
      badgeClasses:
        "bg-emerald-100 text-emerald-700 border-emerald-300/70 dark:bg-emerald-900/30 dark:text-emerald-400",
    }
  }

  // T1 (Adultos Mayores), T2 (Juveniles) → trekking liviano
  return {
    label: "Trekking",
    Icon: Mountain,
    badgeClasses:
      "bg-green-100 text-green-700 border-green-300/70 dark:bg-green-900/30 dark:text-green-400",
  }
}

/** Chip compacto reutilizable: icono + etiqueta con color según tipo. */
export function TipoSalidaChip({ nombre }: { nombre: string }) {
  const { label, Icon, badgeClasses } = getTipoSalidaDisplay(nombre)
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${badgeClasses}`}
    >
      <Icon className="h-3 w-3 shrink-0" />
      {label}
    </span>
  )
}

const CATEGORIA_DISPLAY: Record<string, { label: string; Icon: LucideIcon; badgeClasses: string }> = {
  ALPINISMO: {
    label: "Alpinismo",
    Icon: Mountain,
    badgeClasses: "bg-orange-100 text-orange-700 border-orange-300/70 dark:bg-orange-900/30 dark:text-orange-400",
  },
  TREKKING: {
    label: "Trekking",
    Icon: Footprints,
    badgeClasses: "bg-green-100 text-green-700 border-green-300/70 dark:bg-green-900/30 dark:text-green-400",
  },
  ESCALADA: {
    label: "Escalada",
    Icon: TrendingUp,
    badgeClasses: "bg-red-100 text-red-700 border-red-300/70 dark:bg-red-900/30 dark:text-red-400",
  },
  CICLISMO: {
    label: "Ciclismo",
    Icon: Bike,
    badgeClasses: "bg-blue-100 text-blue-700 border-blue-300/70 dark:bg-blue-900/30 dark:text-blue-400",
  },
}

/** Chip de categoría deportiva basado en el campo tipoActividad (ALPINISMO/TREKKING/ESCALADA/CICLISMO). */
export function CategoriaChip({ tipoActividad }: { tipoActividad: string }) {
  const display = CATEGORIA_DISPLAY[tipoActividad]
  if (!display) return null
  const { label, Icon, badgeClasses } = display
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${badgeClasses}`}
    >
      <Icon className="h-3 w-3 shrink-0" />
      {label}
    </span>
  )
}
