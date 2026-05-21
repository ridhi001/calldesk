import { demoBusiness, demoCalls, demoGaps, demoKnowledge, demoMetrics, demoSearch } from "./demo-data"
import type {
  Business, CallDetail, CallSummary, GapStatus, KnowledgeEntry, KnowledgeGap, KnowledgeInput, Metrics, Outcome, Page, SearchResult,
} from "./types"

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
export const DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_MODE === "true"

// Server Components may reach the backend at a different address than the browser does (in Docker Compose the
// browser uses localhost:8080 while the dashboard container uses http://backend:8080).
const baseUrl = () => (typeof window === "undefined" ? process.env.CALLDESK_API_INTERNAL_URL || API_URL : API_URL)

export class BackendUnavailableError extends Error {}
export class DemoReadOnlyError extends Error {
  constructor() { super("The public demo is read-only. Run CallDesk locally to edit the knowledge base.") }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${baseUrl()}${path}`, { cache: "no-store", ...init, headers: { "Content-Type": "application/json", ...init?.headers } })
  } catch {
    throw new BackendUnavailableError(`Cannot reach the CallDesk backend at ${API_URL}`)
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(body?.message ?? `${path} failed with ${response.status}`)
  }
  return (response.status === 204 ? undefined : await response.json()) as T
}

const query = (params: Record<string, string | number | null | undefined>) => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) if (value != null && value !== "") search.set(key, String(value))
  const text = search.toString()
  return text ? `?${text}` : ""
}

function toSummary(call: CallDetail): CallSummary {
  const summary: CallSummary & { turns?: unknown } = { ...call }
  delete summary.turns
  return summary
}

// ---- Calls and metrics ----

export async function getMetrics(): Promise<Metrics> {
  return DEMO_MODE ? demoMetrics : request<Metrics>("/api/metrics")
}

export async function getCalls(page = 0, size = 20, outcome: Outcome | null = null): Promise<Page<CallSummary>> {
  if (DEMO_MODE) {
    const matching = outcome ? demoCalls.filter((call) => call.outcome === outcome) : demoCalls
    const content = matching.slice(page * size, (page + 1) * size).map(toSummary)
    return { content, totalElements: matching.length, totalPages: Math.ceil(matching.length / size), page, size }
  }
  return request<Page<CallSummary>>(`/api/calls${query({ page, size, outcome })}`)
}

export async function getCall(id: number): Promise<CallDetail | null> {
  if (DEMO_MODE) return demoCalls.find((call) => call.id === id) ?? null
  try {
    // The backend nests the summary: { call: CallSummary, turns: Turn[] }
    const { call, turns } = await request<{ call: CallSummary; turns: CallDetail["turns"] }>(`/api/calls/${id}`)
    return { ...call, turns: turns.map((turn) => ({ ...turn, sources: turn.sources ?? [] })) }
  } catch (error) {
    if (error instanceof Error && /not found|404/i.test(error.message)) return null
    throw error
  }
}

// ---- Knowledge base ----

export async function getBusiness(): Promise<Business> {
  return DEMO_MODE ? demoBusiness : request<Business>("/api/business")
}

export async function getKnowledge(q = ""): Promise<KnowledgeEntry[]> {
  if (DEMO_MODE) {
    const needle = q.trim().toLowerCase()
    return demoKnowledge.filter((entry) => !needle || `${entry.question} ${entry.answer} ${entry.tags.join(" ")}`.toLowerCase().includes(needle))
  }
  return request<KnowledgeEntry[]>(`/api/knowledge${query({ q })}`)
}

export async function searchKnowledge(q: string): Promise<SearchResult> {
  return DEMO_MODE ? demoSearch(q) : request<SearchResult>(`/api/knowledge/search${query({ q })}`)
}

export async function getGaps(status: GapStatus | "ALL" = "OPEN"): Promise<KnowledgeGap[]> {
  if (DEMO_MODE) return status === "ALL" ? demoGaps : demoGaps.filter((gap) => gap.status === status)
  return request<KnowledgeGap[]>(`/api/knowledge/gaps${query({ status })}`)
}

export async function createEntry(input: KnowledgeInput): Promise<KnowledgeEntry> {
  if (DEMO_MODE) throw new DemoReadOnlyError()
  return request<KnowledgeEntry>("/api/knowledge", { method: "POST", body: JSON.stringify(input) })
}

export async function updateEntry(id: number, input: KnowledgeInput): Promise<KnowledgeEntry> {
  if (DEMO_MODE) throw new DemoReadOnlyError()
  return request<KnowledgeEntry>(`/api/knowledge/${id}`, { method: "PUT", body: JSON.stringify(input) })
}

export async function deleteEntry(id: number): Promise<void> {
  if (DEMO_MODE) throw new DemoReadOnlyError()
  return request<void>(`/api/knowledge/${id}`, { method: "DELETE" })
}

export async function resolveGap(id: number, input: KnowledgeInput): Promise<{ gap: KnowledgeGap; entry: KnowledgeEntry }> {
  if (DEMO_MODE) throw new DemoReadOnlyError()
  return request(`/api/knowledge/gaps/${id}/resolve`, { method: "POST", body: JSON.stringify(input) })
}

export async function dismissGap(id: number): Promise<KnowledgeGap> {
  if (DEMO_MODE) throw new DemoReadOnlyError()
  return request<KnowledgeGap>(`/api/knowledge/gaps/${id}/dismiss`, { method: "POST" })
}
