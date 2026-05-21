import Link from "next/link"
import { notFound } from "next/navigation"
import { ArrowLeftIcon, BookOpenTextIcon } from "lucide-react"
import { BackendOffline } from "@/components/backend-offline"
import { hasLatency, LatencyBreakdown, LatencyLegend } from "@/components/latency-breakdown"
import { OutcomeBadge } from "@/components/outcome-badge"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import { BackendUnavailableError, getCall } from "@/lib/api"
import { formatDuration, formatMs, formatTime } from "@/lib/format"

export default async function CallPage(props: PageProps<"/calls/[id]">) {
  const { id } = await props.params
  const callId = Number(id)
  if (!Number.isInteger(callId)) notFound()

  let call
  try {
    call = await getCall(callId)
  } catch (error) {
    if (error instanceof BackendUnavailableError) return <BackendOffline />
    throw error
  }
  if (!call) notFound()

  const measured = call.turns.filter(hasLatency)
  const avgLatency = measured.length ? measured.reduce((sum, turn) => sum + turn.turnLatencyMs, 0) / measured.length : null

  return (
    <>
      <div className="flex flex-col gap-3">
        <Link href="/calls" className={`${buttonVariants({ variant: "ghost", size: "sm" })} self-start`}>
          <ArrowLeftIcon data-icon="inline-start" aria-hidden />
          All calls
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="font-heading text-2xl font-semibold tracking-tight">Call #{call.id}</h1>
          <OutcomeBadge outcome={call.outcome} />
        </div>
        <p className="text-sm text-muted-foreground">
          {call.fromNumber} · {formatTime(call.startedAt)} · {formatDuration(call.startedAt, call.endedAt)} · average turn latency{" "}
          {formatMs(avgLatency)}
        </p>
      </div>

      <section aria-labelledby="transcript-heading" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="transcript-heading" className="font-heading text-base font-medium">
            Transcript
          </h2>
          <LatencyLegend />
        </div>
        <ol className="flex flex-col divide-y rounded-xl ring-1 ring-foreground/10">
          {call.turns.map((turn) => (
            <li key={turn.index} className="enter-item grid gap-2 px-4 py-3 sm:grid-cols-[6rem_1fr]">
              <span className="text-xs font-medium text-muted-foreground">{turn.role === "AGENT" ? "Agent" : "Caller"}</span>
              <div className="flex flex-col gap-2">
                <p className="text-sm">
                  {turn.text}
                  {turn.interrupted && (
                    <Badge variant="outline" className="ml-2 align-middle">
                      Interrupted
                    </Badge>
                  )}
                </p>
                {turn.sources.length > 0 && (
                  <p className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                    <BookOpenTextIcon className="size-3.5" aria-hidden />
                    Answered from
                    {turn.sources.map((source) => (
                      <Link
                        key={source.entryId}
                        href={`/knowledge#entry-${source.entryId}`}
                        className="rounded-md bg-muted px-1.5 py-0.5 text-foreground transition-colors duration-150 hover:bg-muted/70"
                      >
                        {source.question}
                      </Link>
                    ))}
                  </p>
                )}
                {hasLatency(turn) && <LatencyBreakdown turn={turn} />}
              </div>
            </li>
          ))}
        </ol>
      </section>
    </>
  )
}
