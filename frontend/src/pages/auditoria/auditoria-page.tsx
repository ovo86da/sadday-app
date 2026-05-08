import { useState } from "react"
import { useAuditoria, useSecurityEvents } from "@/hooks/use-admin"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import {
  Search, ChevronLeft, ChevronRight, RefreshCw, X,
} from "lucide-react"
import type { AuditoriaEntry, SecurityEventEntry } from "@/types/admin"

// ─── Security event helpers ───────────────────────────────────────────────────

const EVENT_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  LOGIN_SUCCESS:                "default",
  PASSWORD_CHANGED:             "default",
  MFA_ENABLED:                  "default",
  MFA_DISABLED:                 "default",
  SESSION_REVOKED:              "secondary",
  SESSION_REVOKED_ALL:          "secondary",
  NEW_DEVICE_LOGIN:             "secondary",
  NEW_COUNTRY_LOGIN:            "secondary",
  REFRESH_TOKEN_REUSED:         "secondary",
  LOGIN_FAILED:                 "destructive",
  LOGIN_BLOCKED:                "destructive",
  SUSPICIOUS_ACTIVITY_REPORTED: "destructive",
}

const EVENT_LABEL: Record<string, string> = {
  LOGIN_SUCCESS:                "Login exitoso",
  LOGIN_FAILED:                 "Login fallido",
  LOGIN_BLOCKED:                "Login bloqueado",
  NEW_DEVICE_LOGIN:             "Nuevo dispositivo",
  NEW_COUNTRY_LOGIN:            "Nuevo país",
  PASSWORD_CHANGED:             "Contraseña cambiada",
  REFRESH_TOKEN_REUSED:         "Token reutilizado",
  SESSION_REVOKED:              "Sesión cerrada",
  SESSION_REVOKED_ALL:          "Todas las sesiones",
  SUSPICIOUS_ACTIVITY_REPORTED: "Act. sospechosa",
  MFA_ENABLED:                  "2FA activado",
  MFA_DISABLED:                 "2FA desactivado",
}

const SECURITY_EVENT_TYPES = Object.keys(EVENT_LABEL)

function EventTypeBadge({ eventType }: { eventType: string }) {
  return (
    <Badge variant={EVENT_VARIANT[eventType] ?? "outline"} className="text-xs font-mono whitespace-nowrap">
      {EVENT_LABEL[eventType] ?? eventType}
    </Badge>
  )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString("es-EC", {
    day: "2-digit", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  })
}

const RESULTADO_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  SUCCESS: "default",
  FAILED:  "destructive",
  BLOCKED: "secondary",
}

const RESULTADO_LABEL: Record<string, string> = {
  SUCCESS: "OK",
  FAILED:  "Fallido",
  BLOCKED: "Bloqueado",
}

const ACCIONES_FRECUENTES = [
  "APROBAR_RUTA",
  "CREATE_RUTA",
  "UPDATE_RUTA",
  "DELETE_RUTA",
  "HABILITAR_SOCIO",
  "INHABILITAR_SOCIO",
  "CAMBIAR_ROL_SOCIO",
  "UPDATE_NIVEL_TECNICO",
  "INSCRIBIR_SOCIO",
  "DECIDIR_RIESGO_INSCRIPCION",
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "LOGIN_BLOCKED",
  "PASSWORD_CHANGED",
]

function ResultadoBadge({ resultado }: { resultado: AuditoriaEntry["resultado"] }) {
  return (
    <Badge variant={RESULTADO_VARIANT[resultado] ?? "outline"} className="text-xs">
      {RESULTADO_LABEL[resultado] ?? resultado}
    </Badge>
  )
}

// ─── Main page ────────────────────────────────────────────────────────────────

interface Filters {
  actorUsername?: string
  accion?: string
  omitirAccion?: string[]
  resultado?: string
  entidadAfectada?: string
  entidadId?: string
  fechaDesde?: string
  fechaHasta?: string
}

export function AuditoriaTab() {
  const [page, setPage] = useState(0)

  // Campos del formulario (pendientes de aplicar)
  const [actorUsername, setActorUsername] = useState("")
  const [accion, setAccion] = useState("")
  const [omitirAcciones, setOmitirAcciones] = useState<string[]>([])
  const [resultado, setResultado] = useState("")
  const [entidadAfectada, setEntidadAfectada] = useState("")
  const [entidadId, setEntidadId] = useState("")
  const [fechaDesde, setFechaDesde] = useState("")
  const [fechaHasta, setFechaHasta] = useState("")

  // Filtros aplicados (los que realmente se envían)
  const [applied, setApplied] = useState<Filters>({})

  const { data: auditPage, isLoading } = useAuditoria({ page, size: 30, ...applied })

  const applyFilters = () => {
    setPage(0)
    setApplied({
      actorUsername:   actorUsername            || undefined,
      accion:          accion                   || undefined,
      omitirAccion:    omitirAcciones.length > 0 ? omitirAcciones : undefined,
      resultado:       resultado                || undefined,
      entidadAfectada: entidadAfectada          || undefined,
      entidadId:       entidadId                || undefined,
      fechaDesde:      fechaDesde               || undefined,
      fechaHasta:      fechaHasta               || undefined,
    })
  }

  const clearFilters = () => {
    setActorUsername(""); setAccion(""); setOmitirAcciones([]); setResultado("")
    setEntidadAfectada(""); setEntidadId("")
    setFechaDesde(""); setFechaHasta("")
    setPage(0)
    setApplied({})
  }

  const hasActiveFilters = Object.values(applied).some(Boolean)

  return (
    <div className="space-y-6">
      {/* Filtros */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Search className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Filtros de auditoría</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Busca por usuario, acción, entidad o fecha</p>
            </div>
          </div>
        </div>
        <div className="p-5 space-y-3">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Usuario</label>
            <Input
              placeholder="username..."
              value={actorUsername}
              onChange={(e) => setActorUsername(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && applyFilters()}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Acción</label>
            <Select
              value={accion || "_all"}
              onValueChange={(v) => setAccion(v === "_all" ? "" : v)}
            >
              <SelectTrigger>
                <SelectValue placeholder="Todas las acciones" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="_all">Todas las acciones</SelectItem>
                {ACCIONES_FRECUENTES.map((a) => (
                  <SelectItem key={a} value={a}>{a}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="sm:col-span-2 lg:col-span-3 xl:col-span-4">
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Omitir acciones</label>
            <div className="flex flex-wrap items-center gap-2">
              <Select
                value="_add"
                onValueChange={(v) => {
                  if (v !== "_add" && !omitirAcciones.includes(v))
                    setOmitirAcciones((prev) => [...prev, v])
                }}
              >
                <SelectTrigger className="w-52">
                  <SelectValue placeholder="+ Agregar acción a omitir" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="_add" disabled>+ Agregar acción a omitir</SelectItem>
                  {ACCIONES_FRECUENTES.filter((a) => !omitirAcciones.includes(a)).map((a) => (
                    <SelectItem key={a} value={a}>{a}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {omitirAcciones.map((a) => (
                <span
                  key={a}
                  className="inline-flex items-center gap-1 rounded-full border border-destructive/40 bg-destructive/10 px-2.5 py-0.5 text-xs font-mono text-destructive"
                >
                  {a}
                  <button
                    type="button"
                    onClick={() => setOmitirAcciones((prev) => prev.filter((x) => x !== a))}
                    className="ml-0.5 hover:opacity-70"
                    aria-label={`Quitar ${a}`}
                  >
                    <X className="h-3 w-3" />
                  </button>
                </span>
              ))}
            </div>
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Resultado</label>
            <Select value={resultado} onValueChange={(v) => setResultado(v === "all" ? "" : v)}>
              <SelectTrigger><SelectValue placeholder="Todos" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos</SelectItem>
                <SelectItem value="SUCCESS">Exitoso</SelectItem>
                <SelectItem value="FAILED">Fallido</SelectItem>
                <SelectItem value="BLOCKED">Bloqueado</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Entidad</label>
            <Input
              placeholder="rutas, socios..."
              value={entidadAfectada}
              onChange={(e) => setEntidadAfectada(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && applyFilters()}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">ID de entidad</label>
            <Input
              placeholder="UUID o número..."
              value={entidadId}
              onChange={(e) => setEntidadId(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && applyFilters()}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Desde</label>
            <Input type="date" value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Hasta</label>
            <Input type="date" value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} />
          </div>
        </div>
        <div className="flex gap-2">
          <Button size="sm" onClick={applyFilters} className="gap-1.5">
            <Search className="h-3.5 w-3.5" /> Buscar
          </Button>
          {hasActiveFilters && (
            <Button size="sm" variant="outline" onClick={clearFilters} className="gap-1.5">
              <X className="h-3.5 w-3.5" /> Limpiar
            </Button>
          )}
        </div>
        </div>
      </div>

      {/* Tabla */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-36">Fecha</TableHead>
              <TableHead>Usuario</TableHead>
              <TableHead>Acción</TableHead>
              <TableHead className="hidden md:table-cell">Entidad</TableHead>
              <TableHead className="hidden lg:table-cell">IP</TableHead>
              <TableHead>Resultado</TableHead>
              <TableHead className="hidden xl:table-cell">Detalle</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 12 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 7 }).map((_, j) => (
                    <TableCell key={j}>
                      <div className="h-4 w-full max-w-[120px] animate-pulse rounded bg-muted" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : auditPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-12 text-center text-muted-foreground">
                  <RefreshCw className="mx-auto mb-2 h-6 w-6 opacity-40" />
                  No se encontraron registros con los filtros aplicados
                </TableCell>
              </TableRow>
            ) : (
              auditPage?.content.map((entry) => (
                <TableRow
                  key={entry.id}
                  className={entry.resultado !== "SUCCESS" ? "bg-destructive/5" : ""}
                >
                  <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                    {formatDateTime(entry.createdAt)}
                  </TableCell>
                  <TableCell className="text-xs">
                    {entry.actorNombre ? (
                      <div>
                        <p className="font-medium text-foreground">{entry.actorNombre}</p>
                        <p className="font-mono text-muted-foreground">{entry.actorUsername}</p>
                      </div>
                    ) : entry.actorUsername ? (
                      <span className="font-mono">{entry.actorUsername}</span>
                    ) : (
                      <span className="text-muted-foreground italic">SYSTEM</span>
                    )}
                  </TableCell>
                  <TableCell className="font-mono text-xs font-semibold">
                    {entry.accion}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-xs text-muted-foreground">
                    <span className="font-mono">{entry.entidadAfectada ?? "—"}</span>
                    {entry.entidadNombre ? (
                      <span className="ml-1.5 font-medium text-foreground">{entry.entidadNombre}</span>
                    ) : entry.entidadId ? (
                      <span
                        className="ml-1.5 rounded bg-muted px-1 py-0.5 font-mono text-[10px]"
                        title={entry.entidadId}
                      >
                        #{entry.entidadId.length > 8 ? entry.entidadId.slice(0, 8) + "…" : entry.entidadId}
                      </span>
                    ) : null}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell font-mono text-xs text-muted-foreground">
                    {entry.ipAddress ?? "—"}
                  </TableCell>
                  <TableCell>
                    <ResultadoBadge resultado={entry.resultado} />
                  </TableCell>
                  <TableCell className="hidden xl:table-cell text-xs text-muted-foreground max-w-[300px]">
                    <span title={entry.detalle ?? undefined} className="line-clamp-2">
                      {entry.detalle ?? "—"}
                    </span>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Paginación */}
      {auditPage && auditPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            {auditPage.page.totalElements.toLocaleString()} registros · Página{" "}
            {auditPage.page.number + 1} de {auditPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline" size="sm"
              disabled={auditPage.page.number === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button
              variant="outline" size="sm"
              disabled={auditPage.page.number >= auditPage.page.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Security events tab ─────────────────────────────────────────────────────

interface SecurityFilters {
  username?: string
  eventType?: string
  ipAddress?: string
  fechaDesde?: string
  fechaHasta?: string
}

export function SecurityTab() {
  const [page, setPage] = useState(0)

  const [username, setUsername]     = useState("")
  const [eventType, setEventType]   = useState("")
  const [ipAddress, setIpAddress]   = useState("")
  const [fechaDesde, setFechaDesde] = useState("")
  const [fechaHasta, setFechaHasta] = useState("")

  const [applied, setApplied] = useState<SecurityFilters>({})

  const { data: eventsPage, isLoading } = useSecurityEvents({ page, size: 30, ...applied })

  const applyFilters = () => {
    setPage(0)
    setApplied({
      username:   username   || undefined,
      eventType:  eventType  || undefined,
      ipAddress:  ipAddress  || undefined,
      fechaDesde: fechaDesde || undefined,
      fechaHasta: fechaHasta || undefined,
    })
  }

  const clearFilters = () => {
    setUsername(""); setEventType(""); setIpAddress("")
    setFechaDesde(""); setFechaHasta("")
    setPage(0)
    setApplied({})
  }

  const hasActiveFilters = Object.values(applied).some(Boolean)

  return (
    <div className="space-y-6">
      {/* Filtros */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Search className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Filtros de seguridad</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Busca por usuario, tipo de evento, IP o fecha</p>
            </div>
          </div>
        </div>
        <div className="p-5 space-y-3">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Usuario</label>
            <Input
              placeholder="username..."
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && applyFilters()}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Tipo de evento</label>
            <Select value={eventType || "_all"} onValueChange={(v) => setEventType(v === "_all" ? "" : v)}>
              <SelectTrigger><SelectValue placeholder="Todos" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="_all">Todos</SelectItem>
                {SECURITY_EVENT_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>{EVENT_LABEL[t]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">IP</label>
            <Input
              placeholder="192.168..."
              value={ipAddress}
              onChange={(e) => setIpAddress(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && applyFilters()}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Desde</label>
            <Input type="date" value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Hasta</label>
            <Input type="date" value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} />
          </div>
        </div>
        <div className="flex gap-2">
          <Button size="sm" onClick={applyFilters} className="gap-1.5">
            <Search className="h-3.5 w-3.5" /> Buscar
          </Button>
          {hasActiveFilters && (
            <Button size="sm" variant="outline" onClick={clearFilters} className="gap-1.5">
              <X className="h-3.5 w-3.5" /> Limpiar
            </Button>
          )}
        </div>
        </div>
      </div>

      {/* Tabla */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-36">Fecha</TableHead>
              <TableHead>Usuario</TableHead>
              <TableHead>Evento</TableHead>
              <TableHead className="hidden lg:table-cell">IP</TableHead>
              <TableHead className="hidden md:table-cell">Ubicación</TableHead>
              <TableHead className="hidden xl:table-cell">Dispositivo</TableHead>
              <TableHead className="hidden xl:table-cell">Detalle</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 12 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 7 }).map((_, j) => (
                    <TableCell key={j}>
                      <div className="h-4 w-full max-w-[120px] animate-pulse rounded bg-muted" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : eventsPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-12 text-center text-muted-foreground">
                  <RefreshCw className="mx-auto mb-2 h-6 w-6 opacity-40" />
                  No se encontraron eventos con los filtros aplicados
                </TableCell>
              </TableRow>
            ) : (
              eventsPage?.content.map((entry: SecurityEventEntry) => (
                <TableRow
                  key={entry.id}
                  className={
                    ["LOGIN_FAILED", "LOGIN_BLOCKED", "SUSPICIOUS_ACTIVITY_REPORTED"].includes(entry.eventType)
                      ? "bg-destructive/5"
                      : ["NEW_DEVICE_LOGIN", "NEW_COUNTRY_LOGIN"].includes(entry.eventType)
                      ? "bg-yellow-500/5"
                      : ""
                  }
                >
                  <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                    {formatDateTime(entry.createdAt)}
                  </TableCell>
                  <TableCell className="text-xs">
                    {entry.nombre ? (
                      <div>
                        <p className="font-medium text-foreground">{entry.nombre}</p>
                        <p className="font-mono text-muted-foreground">{entry.username}</p>
                      </div>
                    ) : entry.username ? (
                      <span className="font-mono">{entry.username}</span>
                    ) : (
                      <span className="text-muted-foreground italic">—</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <EventTypeBadge eventType={entry.eventType} />
                  </TableCell>
                  <TableCell className="hidden lg:table-cell font-mono text-xs text-muted-foreground">
                    {entry.ipAddress ?? "—"}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-xs text-muted-foreground">
                    {entry.city || entry.countryCode ? (
                      <span>{[entry.city, entry.countryCode].filter(Boolean).join(", ")}</span>
                    ) : "—"}
                  </TableCell>
                  <TableCell className="hidden xl:table-cell text-xs text-muted-foreground">
                    {entry.browser || entry.os ? (
                      <span>{[entry.browser, entry.os].filter(Boolean).join(" · ")}</span>
                    ) : "—"}
                  </TableCell>
                  <TableCell className="hidden xl:table-cell text-xs text-muted-foreground max-w-[240px]">
                    {entry.metadata ? (
                      <span title={entry.metadata} className="line-clamp-2 font-mono">
                        {entry.metadata}
                      </span>
                    ) : "—"}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Paginación */}
      {eventsPage && eventsPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            {eventsPage.page.totalElements.toLocaleString()} registros · Página{" "}
            {eventsPage.page.number + 1} de {eventsPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline" size="sm"
              disabled={eventsPage.page.number === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button
              variant="outline" size="sm"
              disabled={eventsPage.page.number >= eventsPage.page.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
