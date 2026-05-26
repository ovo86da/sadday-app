import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import type { Components } from "react-markdown"
import {
  Tabs, TabsContent, TabsList, TabsTrigger,
} from "@/components/ui/tabs"
import { ScrollText } from "lucide-react"
import {
  REGLAMENTO_SOCIOS,
  REGLAMENTO_ORGANIZACION,
  REGLAMENTO_MONTANA,
  REGLAMENTO_FINANZAS,
} from "./reglamento-content"

// ─── Markdown renderer ────────────────────────────────────────────────────────

const mdComponents: Components = {
  h1: ({ children }) => (
    <h1 className="text-base font-bold text-foreground mt-6 mb-1 uppercase tracking-wide">
      {children}
    </h1>
  ),
  h2: ({ children }) => (
    <h2 className="text-sm font-semibold text-primary mt-4 mb-1">
      {children}
    </h2>
  ),
  h3: ({ children }) => (
    <h3 className="text-xs font-semibold text-foreground mt-3 mb-1 uppercase tracking-wide">
      {children}
    </h3>
  ),
  h4: ({ children }) => (
    <h4 className="text-xs font-semibold text-muted-foreground mt-2 mb-0.5">
      {children}
    </h4>
  ),
  p: ({ children }) => (
    <p className="text-sm text-muted-foreground leading-relaxed mb-2">{children}</p>
  ),
  ul: ({ children }) => (
    <ul className="list-disc list-outside pl-5 mb-2 space-y-0.5">{children}</ul>
  ),
  ol: ({ children }) => (
    <ol className="list-decimal list-outside pl-5 mb-2 space-y-0.5">{children}</ol>
  ),
  li: ({ children }) => (
    <li className="text-sm text-muted-foreground leading-relaxed">{children}</li>
  ),
  strong: ({ children }) => (
    <strong className="font-semibold text-foreground">{children}</strong>
  ),
  hr: () => <hr className="my-4 border-border" />,
  blockquote: ({ children }) => (
    <blockquote className="border-l-2 border-primary pl-3 my-2 text-sm text-muted-foreground">
      {children}
    </blockquote>
  ),
}

function ReglamentoSection({ content }: { content: string }) {
  return (
    <div className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
      <div className="px-6 py-5">
        <ReactMarkdown remarkPlugins={[remarkGfm]} components={mdComponents}>
          {content}
        </ReactMarkdown>
      </div>
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function ReglamentoPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <ScrollText className="h-7 w-7 text-primary shrink-0" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Reglamento</h1>
          <p className="text-muted-foreground">
            Reglamento General del Club Deportivo Especializado Formativo El Sadday
          </p>
        </div>
      </div>

      <Tabs defaultValue="socios">
        <TabsList>
          <TabsTrigger value="socios">Socios</TabsTrigger>
          <TabsTrigger value="organizacion">Organización</TabsTrigger>
          <TabsTrigger value="montana">Actividades de Montaña</TabsTrigger>
          <TabsTrigger value="finanzas">Finanzas y Normas</TabsTrigger>
        </TabsList>

        <TabsContent value="socios" className="mt-6">
          <ReglamentoSection content={REGLAMENTO_SOCIOS} />
        </TabsContent>

        <TabsContent value="organizacion" className="mt-6">
          <ReglamentoSection content={REGLAMENTO_ORGANIZACION} />
        </TabsContent>

        <TabsContent value="montana" className="mt-6">
          <ReglamentoSection content={REGLAMENTO_MONTANA} />
        </TabsContent>

        <TabsContent value="finanzas" className="mt-6">
          <ReglamentoSection content={REGLAMENTO_FINANZAS} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
