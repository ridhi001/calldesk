export type Outcome = "COMPLETED" | "HANDED_OFF" | "VOICEMAIL" | "ABANDONED" | "FAILED"

export type Role = "CALLER" | "AGENT"

export type CallSummary = {
  id: number
  callSid: string
  fromNumber: string
  toNumber: string
  startedAt: string
  endedAt: string | null
  outcome: Outcome | null
  turnCount: number
  bargeInCount: number
}

export type TurnSource = { entryId: number; question: string; score: number }

// Latency fields are cumulative milliseconds measured from the caller's end of speech.
export type Turn = {
  index: number
  role: Role
  text: string
  startedAt: string
  sttMs: number | null
  llmFirstTokenMs: number | null
  ttsFirstAudioMs: number | null
  turnLatencyMs: number | null
  interrupted: boolean
  sources: TurnSource[]
}

export type CallDetail = CallSummary & { turns: Turn[] }

export type Metrics = {
  totalCalls: number
  outcomeBreakdown: Partial<Record<Outcome, number>>
  handoffRate: number
  bargeInCount: number
  turnLatencyMs: { p50: number | null; p95: number | null; average: number | null }
  knowledge: { entries: number; openGaps: number; coverageRate: number | null }
  latencyTrend: { callId: number; startedAt: string; p50TurnLatencyMs: number | null }[]
}

export type Page<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export type Business = {
  name: string
  greeting: string
  hours: string
  address: string
  handoffNumber: string
}

export type KnowledgeEntry = {
  id: number
  question: string
  answer: string
  tags: string[]
  createdAt: string
  updatedAt: string
  timesUsed: number
  lastUsedAt: string | null
}

export type KnowledgeInput = Pick<KnowledgeEntry, "question" | "answer" | "tags">

export type SearchResult = {
  query: string
  confident: boolean
  matches: { entry: KnowledgeEntry; score: number; confident: boolean }[]
}

export type GapStatus = "OPEN" | "RESOLVED" | "DISMISSED"

export type KnowledgeGap = {
  id: number
  question: string
  timesAsked: number
  firstAskedAt: string
  lastAskedAt: string
  lastCallId: number
  bestScore: number | null
  status: GapStatus
  resolvedEntryId: number | null
}

export type LiveEvent =
  | { type: "call-started"; call: CallSummary }
  | { type: "turn-added"; callId: number; turn: Turn }
  | { type: "call-ended"; call: CallSummary }
