"use client"

import Link from "next/link"
import { ArrowRightIcon } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { useEffect, useState } from "react"
import { toast } from "sonner"
import { OutcomeBadge } from "@/components/outcome-badge"
import { API_URL, DEMO_MODE } from "@/lib/api"
import { formatClock, formatDuration } from "@/lib/format"
import type { CallSummary, Turn } from "@/lib/types"

type LiveCall = CallSummary & { lastTurn?: Turn }

const MAX_ITEMS = 8

function upsert(calls: LiveCall[], call: LiveCall): LiveCall[] {
  const rest = calls.filter((existing) => existing.id !== call.id)
  return [call, ...rest].slice(0, MAX_ITEMS)
}

export function LiveCalls({ initial }: { initial: CallSummary[] }) {
  const [calls, setCalls] = useState<LiveCall[]>(initial.slice(0, MAX_ITEMS))
  // null until the stream either opens or fails, so the UI never flashes a "connecting" state.
  const [connected, setConnected] = useState<boolean | null>(null)
  const router = useRouter()

  useEffect(() => {
    if (DEMO_MODE) return
    const source = new EventSource(`${API_URL}/api/calls/live`)
    source.onopen = () => setConnected(true)
    source.onerror = () => setConnected(false)

    source.addEventListener("call-started", (event) => {
      const call = JSON.parse(event.data) as CallSummary
      setCalls((current) => upsert(current, call))
      toast(`Incoming call from ${call.fromNumber}`)
    })
    source.addEventListener("turn-added", (event) => {
      const { callId, turn } = JSON.parse(event.data) as { callId: number; turn: Turn }
      setCalls((current) => current.map((call) => (call.id === callId ? { ...call, lastTurn: turn, turnCount: call.turnCount + 1 } : call)))
    })
    source.addEventListener("call-ended", (event) => {
      const call = JSON.parse(event.data) as CallSummary
      setCalls((current) => current.map((existing) => (existing.id === call.id ? { ...existing, ...call } : existing)))
      if (call.outcome === "HANDED_OFF") toast.info("Call transferred to the front desk")
    })
    source.addEventListener("gap-added", (event) => {
      const gap = JSON.parse(event.data) as { question: string }
      toast.warning(`The agent couldn't answer: "${gap.question}"`, { action: { label: "Review", onClick: () => router.push("/knowledge#gaps") } })
    })

    return () => source.close()
  }, [router])

  return (
    <section aria-labelledby="live-heading" className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <h2 id="live-heading" className="font-heading text-base font-medium">
          Recent calls
        </h2>
        {!DEMO_MODE && connected !== null && (
          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <span aria-hidden className={`size-1.5 rounded-full ${connected ? "bg-emerald-500" : "bg-destructive"}`} />
            {connected ? "Live" : "Offline"}
          </span>
        )}
        <Link href="/calls" className={`${buttonVariants({ variant: "ghost", size: "sm" })} ml-auto`}>
          All calls
          <ArrowRightIcon data-icon="inline-end" aria-hidden />
        </Link>
      </div>
      {calls.length === 0 ? (
        <p className="rounded-xl border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
          No calls yet. They appear here live as soon as the phone rings.
        </p>
      ) : (
        <ul aria-live="polite" className="flex flex-col divide-y rounded-xl ring-1 ring-foreground/10">
          {calls.map((call) => (
            <li key={call.id} className="enter-item">
              <Link
                href={`/calls/${call.id}`}
                className="flex items-center gap-3 px-4 py-3 text-sm transition-colors duration-150 hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none"
              >
                <span className="font-mono text-xs tabular-nums text-muted-foreground">#{call.id}</span>
                <span className="min-w-0 flex-1 truncate">
                  {call.lastTurn ? (
                    <>
                      <span className="text-muted-foreground">{call.lastTurn.role === "AGENT" ? "Agent: " : "Caller: "}</span>
                      {call.lastTurn.text}
                    </>
                  ) : (
                    call.fromNumber
                  )}
                </span>
                <span className="hidden text-xs tabular-nums text-muted-foreground sm:inline">{formatClock(call.startedAt)}</span>
                <span className="w-14 text-right text-xs tabular-nums text-muted-foreground">{formatDuration(call.startedAt, call.endedAt)}</span>
                <OutcomeBadge outcome={call.outcome} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
