import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { formatMs } from "@/lib/format"
import type { Turn } from "@/lib/types"

// Stacked bar of one agent turn: speech-to-text, LLM first token, TTS first audio, send to caller.
// The turn fields are cumulative from end of speech, so each segment is the difference between stages.
const STAGES = [
  { key: "stt", label: "Speech to text", color: "var(--chart-1)" },
  { key: "llm", label: "LLM first token", color: "var(--chart-2)" },
  { key: "tts", label: "TTS first audio", color: "var(--chart-3)" },
  { key: "send", label: "Sent to caller", color: "var(--chart-4)" },
] as const

export const LATENCY_SCALE_MS = 1200

export function hasLatency(turn: Turn): turn is Turn & { turnLatencyMs: number } {
  return turn.role === "AGENT" && turn.turnLatencyMs != null && turn.turnLatencyMs > 0
}

export function LatencyBreakdown({ turn }: { turn: Turn & { turnLatencyMs: number } }) {
  const stt = turn.sttMs ?? 0
  const llm = turn.llmFirstTokenMs ?? stt
  const tts = turn.ttsFirstAudioMs ?? llm
  const segments = [stt, llm - stt, tts - llm, turn.turnLatencyMs - tts].map((ms) => Math.max(0, ms))

  return (
    <div className="flex items-center gap-3">
      <div
        role="img"
        aria-label={`Turn latency ${formatMs(turn.turnLatencyMs)}: ${STAGES.map((stage, i) => `${stage.label} ${formatMs(segments[i])}`).join(", ")}`}
        className="flex h-2 flex-1 overflow-hidden rounded-full bg-muted"
      >
        {STAGES.map((stage, i) => (
          <Tooltip key={stage.key}>
            <TooltipTrigger
              render={<span />}
              className="h-full"
              style={{ width: `${(segments[i] / LATENCY_SCALE_MS) * 100}%`, background: stage.color }}
            />
            <TooltipContent>
              {stage.label}: {formatMs(segments[i])}
            </TooltipContent>
          </Tooltip>
        ))}
      </div>
      <span className="w-16 text-right font-mono text-xs tabular-nums">{formatMs(turn.turnLatencyMs)}</span>
    </div>
  )
}

export function LatencyLegend() {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
      {STAGES.map((stage) => (
        <li key={stage.key} className="flex items-center gap-1.5">
          <span aria-hidden className="size-2 rounded-sm" style={{ background: stage.color }} />
          {stage.label}
        </li>
      ))}
    </ul>
  )
}
