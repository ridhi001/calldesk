"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useTransition } from "react"
import { CircleCheckIcon, MessageCircleQuestionIcon } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { EntryDialog } from "@/components/knowledge/entry-dialog"
import { DEMO_MODE, dismissGap, resolveGap } from "@/lib/api"
import { formatTime } from "@/lib/format"
import type { KnowledgeGap } from "@/lib/types"

export function GapsList({ gaps }: { gaps: KnowledgeGap[] }) {
  if (gaps.length === 0) {
    return (
      <p className="flex items-center gap-2 rounded-xl border border-dashed px-4 py-6 text-sm text-muted-foreground">
        <CircleCheckIcon className="size-4 text-emerald-500" aria-hidden />
        No open gaps. Every question callers asked had an answer in the knowledge base.
      </p>
    )
  }

  return (
    <ul className="flex flex-col divide-y rounded-xl bg-card ring-1 ring-foreground/10">
      {gaps.map((gap) => (
        <GapRow key={gap.id} gap={gap} />
      ))}
    </ul>
  )
}

function GapRow({ gap }: { gap: KnowledgeGap }) {
  const router = useRouter()
  const [pending, startTransition] = useTransition()

  function dismiss() {
    startTransition(async () => {
      try {
        await dismissGap(gap.id)
        toast("Gap dismissed")
        router.refresh()
      } catch (cause) {
        toast.error(cause instanceof Error ? cause.message : "Could not dismiss the gap")
      }
    })
  }

  return (
    <li className="enter-item flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
      <MessageCircleQuestionIcon className="hidden size-4 shrink-0 text-amber-500 sm:block" aria-hidden />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <p className="text-sm font-medium">&ldquo;{gap.question}&rdquo;</p>
        <p className="text-xs text-muted-foreground">
          Asked {gap.timesAsked} {gap.timesAsked === 1 ? "time" : "times"} · last {formatTime(gap.lastAskedAt)} ·{" "}
          <Link href={`/calls/${gap.lastCallId}`} className="underline-offset-4 hover:underline">
            call #{gap.lastCallId}
          </Link>
        </p>
      </div>
      {!DEMO_MODE && <div className="flex gap-2">
        <Button variant="ghost" size="sm" onClick={dismiss} disabled={pending}>
          Dismiss
        </Button>
        <EntryDialog
          trigger={<Button size="sm">Add answer</Button>}
          title="Answer this question"
          description="Adds an entry to the knowledge base and closes the gap. The agent uses it from the next call."
          submitLabel="Add to knowledge base"
          initial={{ question: gap.question }}
          onSubmit={(input) => resolveGap(gap.id, input)}
          successMessage="Answer added and gap closed"
        />
      </div>}
    </li>
  )
}
