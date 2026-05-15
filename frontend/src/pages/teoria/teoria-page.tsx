import {
  Tabs, TabsContent, TabsList, TabsTrigger,
} from "@/components/ui/tabs"
import { Badge } from "@/components/ui/badge"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import {
  GraduationCap, Mountain, Bike, Footprints, TrendingUp,
  Layers, Users, Package, ChevronRight,
} from "lucide-react"

// ─── Shared primitives ────────────────────────────────────────────────────────

interface ScaleRow {
  badge: string
  badgeClass: string
  label?: string
  description: string
}

interface ScaleHeaders {
  col1: string
  col2?: string
  col3: string
}

function ScaleTable({ rows, headers }: { rows: ScaleRow[]; headers: ScaleHeaders }) {
  return (
    <div className="rounded-lg border border-border overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow className="bg-muted/50 hover:bg-muted/50">
            <TableHead className="w-28 text-xs font-semibold text-foreground">{headers.col1}</TableHead>
            {headers.col2 && (
              <TableHead className="w-40 text-xs font-semibold text-foreground">{headers.col2}</TableHead>
            )}
            <TableHead className="text-xs font-semibold text-foreground">{headers.col3}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.badge} className="hover:bg-muted/40">
              <TableCell className="w-28 py-3 align-top">
                <Badge className={`${row.badgeClass} font-mono text-xs`}>{row.badge}</Badge>
              </TableCell>
              {row.label !== undefined && (
                <TableCell className="py-3 font-medium text-sm w-40 align-top text-foreground">
                  {row.label}
                </TableCell>
              )}
              <TableCell className="py-3 text-sm text-muted-foreground align-top">
                {row.description}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

function SectionCard({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: string
  children: React.ReactNode
}) {
  return (
    <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
      <div className="border-b border-border px-5 py-3.5">
        <p className="font-semibold text-foreground">{title}</p>
        {subtitle && (
          <p className="text-xs text-muted-foreground mt-0.5">{subtitle}</p>
        )}
      </div>
      <div className="p-5">{children}</div>
    </div>
  )
}

// ─── Scale data ───────────────────────────────────────────────────────────────

const IFAS: ScaleRow[] = [
  { badge: "F",  badgeClass: "bg-green-500/15 text-green-700",  label: "Fácil",                   description: "Caminata técnica. Terreno fácil, glaciar poco complejo. Manos para equilibrio ocasional." },
  { badge: "PD", badgeClass: "bg-lime-500/15 text-lime-700",    label: "Poco difícil",            description: "Pendientes de nieve/hielo hasta ~45°. Manejo básico de cuerda y crampones." },
  { badge: "AD", badgeClass: "bg-yellow-500/15 text-yellow-700",label: "Algo difícil",            description: "Pasos de roca aprox. III, pendientes 45°–65°. Requiere experiencia." },
  { badge: "D",  badgeClass: "bg-orange-500/15 text-orange-700",label: "Difícil",                 description: "Escalada sostenida (IV/V) y hielo hasta ~70°. Nivel avanzado." },
  { badge: "TD", badgeClass: "bg-red-500/15 text-red-700",      label: "Muy difícil",             description: "Rutas largas, técnicas y con peligros objetivos (avalanchas, desprendimientos)." },
  { badge: "ED", badgeClass: "bg-purple-500/15 text-purple-700",label: "Extremadamente difícil",  description: "Élite. Terreno muy vertical, mucha exposición, gran exigencia." },
]

const UIAA: ScaleRow[] = [
  { badge: "NA",    badgeClass: "bg-muted text-muted-foreground",     label: "—",      description: "Sin partes de escalada en roca." },
  { badge: "I",     badgeClass: "bg-green-500/15 text-green-700",     label: "1",      description: "Trepada fácil. Manos para el equilibrio. Normalmente sin cuerda." },
  { badge: "II",    badgeClass: "bg-green-500/15 text-green-700",     label: "2",      description: "Trepada/paso de bloque. Manos necesarias. Exposición baja." },
  { badge: "III",   badgeClass: "bg-lime-500/15 text-lime-700",       label: "3",      description: "Escalada simple. Los principiantes suelen necesitar cuerda." },
  { badge: "IV",    badgeClass: "bg-yellow-500/15 text-yellow-700",   label: "4a–4c",  description: "Técnica clásica. Cuerda y arnés obligatorios." },
  { badge: "V",     badgeClass: "bg-orange-500/15 text-orange-700",   label: "5a–5c",  description: "Dificultad media. Vertical con buenas presas." },
  { badge: "VI",    badgeClass: "bg-red-500/15 text-red-700",         label: "6a–6b",  description: "Dificultad alta. Presas pequeñas, fuerza y técnica." },
  { badge: "VII+",  badgeClass: "bg-red-500/15 text-red-700",         label: "6c–7a+", description: "Movimientos técnicos continuos, presas pequeñas, secciones verticales o desplomadas." },
  { badge: "VIII",  badgeClass: "bg-purple-500/15 text-purple-700",   label: "7b–8a",  description: "Nivel experto: escalada atlética con movimientos dinámicos y alta exigencia física." },
  { badge: "IX",    badgeClass: "bg-purple-500/15 text-purple-700",   label: "8b–9a",  description: "Élite mundial: movimientos extremadamente técnicos. Reservado para escaladores de alto rendimiento." },
]

const HIELO: ScaleRow[] = [
  { badge: "NA",  badgeClass: "bg-muted text-muted-foreground",     description: "Sin escalada en hielo." },
  { badge: "WI1", badgeClass: "bg-green-500/15 text-green-700",     description: "~45°. Se progresa casi caminando con crampones." },
  { badge: "WI2", badgeClass: "bg-green-500/15 text-green-700",     description: "~60°. Hielo sólido, técnica básica (1 piolet posible)." },
  { badge: "WI3", badgeClass: "bg-yellow-500/15 text-yellow-700",   description: "70°–80°. Largo y consistente. Normalmente dos piolets." },
  { badge: "WI4", badgeClass: "bg-orange-500/15 text-orange-700",   description: "90° vertical, buena calidad, tramos verticales. Muy físico." },
  { badge: "WI5", badgeClass: "bg-red-500/15 text-red-700",         description: "Verticalidad sostenida, hielo más frágil (columnas/coliflor)." },
  { badge: "WI6", badgeClass: "bg-red-500/15 text-red-700",         description: "Muy técnico, vertical/desplomado, hielo delgado o inestable." },
  { badge: "WI7", badgeClass: "bg-purple-500/15 text-purple-700",   description: "Límite. Hielo extremadamente fino, estructuras colgantes frágiles. Solo expertos top." },
]

const COMPROMISO: ScaleRow[] = [
  { badge: "I",   badgeClass: "bg-green-500/15 text-green-700",     description: "Ruta corta, retirada evidente, cerca de civilización." },
  { badge: "II",  badgeClass: "bg-green-500/15 text-green-700",     description: "Media jornada, retirada sencilla, acceso a rescate rápido." },
  { badge: "III", badgeClass: "bg-yellow-500/15 text-yellow-700",   description: "Jornada completa. El descenso puede requerir varios rápeles." },
  { badge: "IV",  badgeClass: "bg-orange-500/15 text-orange-700",   description: "Jornada larga e intensa. Retirada difícil; mal tiempo = problemas serios." },
  { badge: "V",   badgeClass: "bg-red-500/15 text-red-700",         description: "Varios días. Vivac. Retirada extremadamente compleja una vez adentro." },
  { badge: "VI",  badgeClass: "bg-red-500/15 text-red-700",         description: "Gran pared/expedición remota. Autosuficiencia total." },
  { badge: "VII", badgeClass: "bg-purple-500/15 text-purple-700",   description: "Máximo. Himalaya/Antártida, gran altitud, rescate casi imposible." },
]

const YOSEMITE: ScaleRow[] = [
  { badge: "Clase 1", badgeClass: "bg-green-500/15 text-green-700",    description: "Senderismo por camino." },
  { badge: "Clase 2", badgeClass: "bg-lime-500/15 text-lime-700",      description: "Terreno irregular, manos para equilibrio." },
  { badge: "Clase 3", badgeClass: "bg-yellow-500/15 text-yellow-700",  description: "Trepada (scrambling). Riesgo de caída; algunos usan cuerda." },
  { badge: "Clase 4", badgeClass: "bg-orange-500/15 text-orange-700",  description: "Muy expuesto. Cuerda casi siempre." },
  { badge: "Clase 5", badgeClass: "bg-red-500/15 text-red-700",        description: "Escalada técnica (5.5, 5.10…)." },
]

const SADDAY: ScaleRow[] = [
  { badge: "1",  badgeClass: "bg-green-500/15 text-green-700",     label: "Mínimo",   description: "El riesgo que se asume es mínimo y no supondrá un imprevisto de gran importancia si se desencadena." },
  { badge: "2",  badgeClass: "bg-lime-500/15 text-lime-700",       label: "Bajo",     description: "El riesgo es bajo. Si el peligro se desencadena puede suponer un retraso importante en el horario." },
  { badge: "3",  badgeClass: "bg-yellow-500/15 text-yellow-700",   label: "Moderado", description: "El riesgo es moderado. Si el peligro se materializa probablemente la actividad no se finalizará según lo previsto." },
  { badge: "4",  badgeClass: "bg-orange-500/15 text-orange-700",   label: "Alto",     description: "El riesgo es alto. Si el peligro se materializa puede haber daños personales importantes." },
  { badge: "5",  badgeClass: "bg-red-500/15 text-red-700",         label: "Extremo",  description: "El riesgo es extremo. Si el peligro se desencadena la integridad física corre grave peligro." },
]

const SENDERISMO: ScaleRow[] = [
  { badge: "Fácil",        badgeClass: "bg-green-500/15 text-green-700",    description: "Camino bien señalizado, terreno llano o con pendientes suaves. Apto para todos." },
  { badge: "Moderado",     badgeClass: "bg-yellow-500/15 text-yellow-700",  description: "Terreno variado con algunas pendientes. Requiere buena condición física básica." },
  { badge: "Exigente",     badgeClass: "bg-orange-500/15 text-orange-700",  description: "Pendientes pronunciadas, terreno irregular. Requiere experiencia y buena condición." },
  { badge: "Muy exigente", badgeClass: "bg-red-500/15 text-red-700",        description: "Alta exigencia física y técnica. Solo para personas con experiencia consolidada." },
]

const CICLISMO_S: ScaleRow[] = [
  { badge: "S0", badgeClass: "bg-green-500/15 text-green-700",    description: "Sin obstáculos técnicos. Terreno liso o compacto. Apto para cualquier ciclista." },
  { badge: "S1", badgeClass: "bg-lime-500/15 text-lime-700",      description: "Raíces y piedras pequeñas, inclinaciones leves. Velocidad moderada." },
  { badge: "S2", badgeClass: "bg-yellow-500/15 text-yellow-700",  description: "Obstáculos naturales hasta 20 cm, terreno irregular. Requiere técnica básica de MTB." },
  { badge: "S3", badgeClass: "bg-orange-500/15 text-orange-700",  description: "Grandes raíces, drops hasta 60 cm, alta velocidad. Requiere experiencia y buena técnica." },
  { badge: "S4", badgeClass: "bg-red-500/15 text-red-700",        description: "Drops grandes, características técnicas complejas. Solo para ciclistas expertos." },
]

// ─── Tab: Escalas ─────────────────────────────────────────────────────────────

function EscalasTab() {
  return (
    <div className="space-y-6">
      <SectionCard
        title="Escala Alpina IFAS"
        subtitle="Sistema Francés de calificación alpina — usado en rutas de Alpinismo"
      >
        <ScaleTable rows={IFAS} headers={{ col1: "Grado", col2: "Nombre", col3: "Descripción" }} />
      </SectionCard>

      <SectionCard
        title="Dificultad en Roca — UIAA / Francesa"
        subtitle="Sistema internacional de dificultad en roca — usado en Escalada y Alpinismo"
      >
        <ScaleTable rows={UIAA} headers={{ col1: "UIAA", col2: "Francesa", col3: "Descripción" }} />
      </SectionCard>

      <SectionCard
        title="Dificultad en Hielo — WI (Water Ice)"
        subtitle="Escala de hielo cascada — usado en Alpinismo con escalada en hielo"
      >
        <ScaleTable rows={HIELO} headers={{ col1: "Grado", col3: "Descripción" }} />
      </SectionCard>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <SectionCard
          title="Compromiso"
          subtitle="Valora el riesgo objetivo y la complejidad de la retirada"
        >
          <ScaleTable rows={COMPROMISO} headers={{ col1: "Grado", col3: "Descripción" }} />
        </SectionCard>

        <SectionCard
          title="Sistema de Clases Yosemite"
          subtitle="Clasifica la naturaleza del terreno según la forma de progresar"
        >
          <ScaleTable rows={YOSEMITE} headers={{ col1: "Clase", col3: "Descripción" }} />
        </SectionCard>
      </div>

      <SectionCard
        title="Escala Sadday — Nivel de riesgo y exigencia"
        subtitle="Escala propia del club para el nivel técnico y físico requerido"
      >
        <ScaleTable rows={SADDAY} headers={{ col1: "Nivel", col2: "Nombre", col3: "Descripción" }} />
      </SectionCard>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <SectionCard
          title="Dificultad Senderismo"
          subtitle="Clasifica la exigencia de rutas de Trekking"
        >
          <ScaleTable rows={SENDERISMO} headers={{ col1: "Dificultad", col3: "Descripción" }} />
        </SectionCard>

        <SectionCard
          title="Escala Técnica Ciclismo — S"
          subtitle="IMBA trail difficulty rating — usado en rutas de Ciclismo de montaña"
        >
          <ScaleTable rows={CICLISMO_S} headers={{ col1: "Escala", col3: "Descripción" }} />
        </SectionCard>
      </div>
    </div>
  )
}

// ─── Tab: Actividades ─────────────────────────────────────────────────────────

interface ActivityCardProps {
  icon: React.ReactNode
  title: string
  badgeClass: string
  description: string
  details: { label: string; value: string }[]
}

function ActivityCard({ icon, title, badgeClass, description, details }: ActivityCardProps) {
  return (
    <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
      <div className={`px-5 py-4 border-b border-border flex items-center gap-3`}>
        <div className={`flex items-center justify-center h-9 w-9 rounded-lg ${badgeClass}`}>
          {icon}
        </div>
        <p className="font-semibold text-foreground text-lg">{title}</p>
      </div>
      <div className="p-5 space-y-4">
        <p className="text-sm text-muted-foreground leading-relaxed">{description}</p>
        <div className="space-y-2">
          {details.map((d) => (
            <div key={d.label} className="flex gap-2 text-sm">
              <ChevronRight className="h-4 w-4 text-primary shrink-0 mt-0.5" />
              <span>
                <span className="font-medium text-foreground">{d.label}:</span>{" "}
                <span className="text-muted-foreground">{d.value}</span>
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function ActividadesTab() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <ActivityCard
        icon={<Mountain className="h-5 w-5 text-orange-600" />}
        title="Alpinismo"
        badgeClass="bg-orange-500/15"
        description="Actividad que combina técnicas de escalada en roca, nieve y hielo para ascender cumbres de alta montaña. Requiere manejo de crampones, piolet y cuerda en ambientes de glaciar."
        details={[
          { label: "Dificultad técnica", value: "Escala IFAS, UIAA, WI, Compromiso" },
          { label: "Dificultad riesgo", value: "Escala Sadday (nivel técnico y físico)" },
          { label: "Equipo", value: "Crampones, piolet, cuerda, arnés, casco" },
          { label: "Nivel mínimo", value: "Generalmente Semi-senior a Advanced" },
        ]}
      />

      <ActivityCard
        icon={<TrendingUp className="h-5 w-5 text-red-600" />}
        title="Escalada"
        badgeClass="bg-red-500/15"
        description="Ascenso por paredes de roca usando técnicas de progresión vertical con cuerda y equipamiento específico. Se divide en modalidades según el estilo de ascenso y el tipo de protecciones usadas."
        details={[
          { label: "Deportiva", value: "Chapas fijas en la pared. Énfasis en la dificultad técnica de los movimientos." },
          { label: "Tradicional", value: "El líder coloca y retira sus propias protecciones. Mayor compromiso." },
          { label: "Mixta", value: "Combina escalada en roca y en hielo/nieve en la misma vía." },
          { label: "Boulder", value: "Bloques de baja altura sin cuerda. Máxima dificultad en movimientos cortos." },
        ]}
      />

      <ActivityCard
        icon={<Footprints className="h-5 w-5 text-green-600" />}
        title="Trekking"
        badgeClass="bg-green-500/15"
        description="Senderismo de montaña por caminos y senderos en terreno variado. No requiere técnicas de escalada, pero sí buena condición física y equipo adecuado según la altitud y duración."
        details={[
          { label: "Dificultad", value: "Escala propia de senderismo (Fácil → Muy exigente)" },
          { label: "Ruta circular", value: "Salida y llegada al mismo punto. Más versátil logísticamente." },
          { label: "Fuentes de agua", value: "Indica si hay disponibilidad de agua en ruta. Clave para la planificación." },
          { label: "Tipo de terreno", value: "Sendero, quebrada, páramo, glaciar, bosque, etc." },
        ]}
      />

      <ActivityCard
        icon={<Bike className="h-5 w-5 text-blue-600" />}
        title="Ciclismo de Montaña"
        badgeClass="bg-blue-500/15"
        description="Recorridos en bicicleta por caminos y senderos de montaña. Las características de la bicicleta y la dificultad técnica del terreno definen la exigencia de la salida."
        details={[
          { label: "Rígida", value: "Sin suspensión trasera. Liviana y eficiente en terreno suave." },
          { label: "Doble suspensión", value: "Suspensión delantera y trasera. Mayor comodidad y agarre en terreno técnico." },
          { label: "Enduro", value: "Diseñada para descensos técnicos con subidas largas." },
          { label: "Gravel / Ruta", value: "Aptas para caminos de tierra o asfalto. Menos técnicas pero más velocidad." },
        ]}
      />
    </div>
  )
}

// ─── Tab: Niveles de Socio ────────────────────────────────────────────────────

interface NivelSocioProps {
  nivel: number
  id: string
  nombre: string
  items: string[]
  badgeClass: string
  bgClass: string
}

const NIVELES: NivelSocioProps[] = [
  {
    nivel: 0, id: "SO001", nombre: "Externo",
    items: [
      "No es miembro del club.",
      "No se conocen sus capacidades técnicas ni físicas.",
    ],
    badgeClass: "bg-muted text-muted-foreground",
    bgClass: "border-border",
  },
  {
    nivel: 1, id: "SO002", nombre: "Principiante",
    items: [
      "Acaba de ingresar al club.",
      "Poca o nula experiencia comprobada en montaña.",
      "No puede guiar una salida.",
    ],
    badgeClass: "bg-green-500/15 text-green-700",
    bgClass: "border-green-200",
  },
  {
    nivel: 2, id: "SO003", nombre: "Semi-senior",
    items: [
      "Varias participaciones en el club.",
      "Alguna experiencia en media montaña y alta montaña.",
      "Sabe hacer los nudos básicos y conoce las maniobras básicas de escalada.",
      "Puede guiar rutas de nivel Moderado.",
      "Puede ser puntero de cordada, pero no guia en alta montaña",
      "No puede guiar rutas de nivel Alto o superior.",
    ],
    badgeClass: "bg-yellow-500/15 text-yellow-700",
    bgClass: "border-yellow-200",
  },
  {
    nivel: 3, id: "SO004", nombre: "Senior",
    items: [
      "Salidas a varias montañas, incluyendo alta montaña y escalada en roca.",
      "Puede ser puntero de cordada y guia",
      "Tiene mucha experiencia en nudos y maniobras de escalada y alta montaña.",
      "Puede guiar rutas de nivel Alto.",
    ],
    badgeClass: "bg-orange-500/15 text-orange-700",
    bgClass: "border-orange-200",
  },
  {
    nivel: 4, id: "SO005", nombre: "Advanced",
    items: [
      "Alta capacidad técnica y física.",
      "Puede guiar a otros en salidas de alta dificultad.",
    ],
    badgeClass: "bg-red-500/15 text-red-700",
    bgClass: "border-red-200",
  },
  {
    nivel: 5, id: "SO006", nombre: "Expert",
    items: [
      "Muchos años de experiencia y gran capacidad física.",
      "Puede guiar en rutas desconocidas.",
      "Puede asumir cualquier rol de liderazgo en las salidas.",
    ],
    badgeClass: "bg-purple-500/15 text-purple-700",
    bgClass: "border-purple-200",
  },
]

function NivelesSocioTab() {
  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm p-5">
        <p className="text-sm text-muted-foreground leading-relaxed">
          El club clasifica a sus socios en seis niveles de acuerdo a su experiencia y capacidad técnica. Este
          nivel determina qué rutas puede acceder cada socio y si puede asumir roles de liderazgo en las salidas.
          Los niveles los asigna la Secretaría o un Directivo desde el perfil del socio.
        </p>
      </div>

      <div className="space-y-3">
        {NIVELES.map((n) => (
          <div
            key={n.id}
            className={`rounded-xl border bg-card shadow-sm overflow-hidden ${n.bgClass}`}
          >
            <div className="flex items-start gap-4 p-5">
              <div className="shrink-0">
                <Badge className={`${n.badgeClass} text-xs font-mono w-8 justify-center`}>
                  {n.nivel}
                </Badge>
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-semibold text-foreground mb-2">{n.nombre}</p>
                <ul className="space-y-1">
                  {n.items.map((item) => (
                    <li key={item} className="flex items-start gap-2 text-sm">
                      <ChevronRight className="h-3.5 w-3.5 text-muted-foreground shrink-0 mt-0.5" />
                      <span className="text-muted-foreground">{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

// ─── Tab: Equipamiento ────────────────────────────────────────────────────────

interface EquipoItem {
  nombre: string
  descripcion: string
  badgeClass: string
  items: string[]
}

const EQUIPOS: EquipoItem[] = [
  {
    nombre: "Equipo Alta Montaña",
    descripcion: "Para cumbres con nieve, hielo y glaciar. Implica técnicas de alpinismo.",
    badgeClass: "bg-orange-500/15 text-orange-700",
    items: [
      "Crampones de 12 puntas", "Piolet de montaña", "Cuerda dinámica (40–60 m)",
      "Arnés de escalada", "Casco de montaña", "Gafas glaciar (UV)", "Ropa térmica y cortaviento",
      "Polainas altas", "Botas de alta montaña (doble bota o similar)",
    ],
  },
  {
    nombre: "Equipo Media Montaña",
    descripcion: "Para salidas de trekking o cumbres sin glaciar. Sin técnicas de escalada.",
    badgeClass: "bg-yellow-500/15 text-yellow-700",
    items: [
      "Bastones de trekking", "Botas de trekking impermeables", "Ropa en capas (base, intermedia, cortaviento)",
      "Mochila 30–50 L", "Polainas bajas", "Gafas de sol", "Protector solar y labial",
    ],
  },
  {
    nombre: "Equipo Escalada en Roca",
    descripcion: "Para vías de escalada deportiva, tradicional o boulder.",
    badgeClass: "bg-red-500/15 text-red-700",
    items: [
      "Arnés de escalada", "Pies de gato (zapatos de escalada)", "Cuerda dinámica",
      "Cintas exprés y reuniones", "Asegurador (Grigri, tubo, etc.)", "Casco",
      "Magnesia y bolsa de magnesio",
    ],
  },
  {
    nombre: "Equipo Escalada en Hielo",
    descripcion: "Para vías de hielo cascada o mixtas.",
    badgeClass: "bg-blue-500/15 text-blue-700",
    items: [
      "Crampones técnicos (puntas delanteras)", "Dos piolets técnicos", "Arnés",
      "Cuerda dinámica (50–60 m)", "Casco", "Guantes de escalada en hielo",
      "Tornillos de hielo y asegurador",
    ],
  },
  {
    nombre: "Equipo Selva / Cascadas",
    descripcion: "Para salidas en ambientes selváticos, ríos y cascadas.",
    badgeClass: "bg-green-500/15 text-green-700",
    items: [
      "Ropa secado rápido", "Botas de caucho o anfibias", "Repelente de insectos",
      "Impermeable tipo poncho", "Botiquín de primeros auxilios ampliado",
      "Purificador o pastillas de agua",
    ],
  },
  {
    nombre: "Sin Equipo Obligatorio",
    descripcion: "Salidas que no requieren equipamiento técnico especializado.",
    badgeClass: "bg-muted text-muted-foreground",
    items: [
      "Calzado cómodo (según terreno)", "Ropa adecuada al clima", "Agua y alimentación",
      "Protector solar", "Mochila de día",
    ],
  },
]

function EquipamientoTab() {
  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm p-5">
        <p className="text-sm text-muted-foreground leading-relaxed">
          Cada ruta del club tiene asignado un tipo de equipamiento requerido. Esta clasificación orienta
          a los socios sobre qué deben llevar en función de las características técnicas de la salida.
          El detalle exacto puede variar según las condiciones del día y el criterio del Jefe de Salida.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {EQUIPOS.map((eq) => (
          <div key={eq.nombre} className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
            <div className="border-b border-border px-5 py-3.5">
              <div className="flex items-center gap-2">
                <Badge className={`${eq.badgeClass} text-xs`}>{eq.nombre}</Badge>
              </div>
              <p className="text-xs text-muted-foreground mt-1">{eq.descripcion}</p>
            </div>
            <div className="p-5">
              <ul className="space-y-1.5">
                {eq.items.map((item) => (
                  <li key={item} className="flex items-start gap-2 text-sm">
                    <ChevronRight className="h-3.5 w-3.5 text-primary shrink-0 mt-0.5" />
                    <span className="text-muted-foreground">{item}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function TeoriaPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <GraduationCap className="h-7 w-7 text-primary shrink-0" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Teoría</h1>
          <p className="text-muted-foreground">
            Escalas de dificultad, actividades, niveles de socio y equipamiento del club
          </p>
        </div>
      </div>

      <Tabs defaultValue="escalas">
        <TabsList>
          <TabsTrigger value="escalas" className="gap-1.5">
            <Layers className="h-4 w-4" /> Escalas de dificultad
          </TabsTrigger>
          <TabsTrigger value="actividades" className="gap-1.5">
            <Mountain className="h-4 w-4" /> Actividades
          </TabsTrigger>
          <TabsTrigger value="niveles" className="gap-1.5">
            <Users className="h-4 w-4" /> Niveles de socio
          </TabsTrigger>
          <TabsTrigger value="equipamiento" className="gap-1.5">
            <Package className="h-4 w-4" /> Equipamiento
          </TabsTrigger>
        </TabsList>

        <TabsContent value="escalas" className="mt-6">
          <EscalasTab />
        </TabsContent>

        <TabsContent value="actividades" className="mt-6">
          <ActividadesTab />
        </TabsContent>

        <TabsContent value="niveles" className="mt-6">
          <NivelesSocioTab />
        </TabsContent>

        <TabsContent value="equipamiento" className="mt-6">
          <EquipamientoTab />
        </TabsContent>
      </Tabs>
    </div>
  )
}
