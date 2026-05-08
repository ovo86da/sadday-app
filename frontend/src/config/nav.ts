import {
  LayoutDashboard,
  Users,
  Mountain,
  Route,
  Calendar,
  FileText,
  BookOpen,
  Settings,
  User,
  ShieldCheck,
  BarChart2,
  Map,
  Contact,
  ClipboardCheck,
  type LucideIcon,
} from "lucide-react"

export interface NavItem {
  title: string
  href: string
  icon: LucideIcon
  roles?: string[] // Si no se especifica, visible para todos los autenticados
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

/**
 * Configuración de navegación del sidebar.
 * Los items se filtran según el rol del usuario logueado.
 */
export const navConfig: NavGroup[] = [
  {
    label: "General",
    items: [
      {
        title: "Dashboard",
        href: "/dashboard",
        icon: LayoutDashboard,
      },
    ],
  },
  {
    label: "Club",
    items: [
      {
        title: "Socios",
        href: "/socios",
        icon: Users,
        roles: ["ADMIN", "SECRETARIA", "DIRECTIVO"],
      },
      {
        title: "Montañas",
        href: "/montanas",
        icon: Mountain,
      },
      {
        title: "Rutas",
        href: "/rutas",
        icon: Route,
      },
      {
        title: "Salidas",
        href: "/salidas",
        icon: Calendar,
      },
      {
        title: "Notificaciones",
        href: "/notificaciones",
        icon: ClipboardCheck,
        roles: ["ADMIN", "SECRETARIA", "DIRECTIVO"],
      },
      {
        title: "Niveles de acceso",
        href: "/acceso-nivel",
        icon: ShieldCheck,
      },
    ],
  },
  {
    label: "Análisis",
    items: [
      {
        title: "Planificador",
        href: "/planificador",
        icon: Map,
      },
      {
        title: "Estadísticas",
        href: "/estadisticas",
        icon: BarChart2,
      },
    ],
  },
  {
    label: "Documentos",
    items: [
      {
        title: "Informes",
        href: "/informes",
        icon: FileText,
      },
      {
        title: "Actas",
        href: "/actas",
        icon: BookOpen,
      },
    ],
  },
  {
    label: "Sistema",
    items: [
      {
        title: "Mi Perfil",
        href: "/perfil",
        icon: User,
      },
      {
        title: "Contactos",
        href: "/contactos",
        icon: Contact,
        roles: ["ADMIN", "SECRETARIA"],
      },
      {
        title: "Administración",
        href: "/admin",
        icon: Settings,
        roles: ["ADMIN", "SECRETARIA"],
      },
    ],
  },
]
