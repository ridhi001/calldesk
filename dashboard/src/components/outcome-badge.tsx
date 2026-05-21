import { Badge } from "@/components/ui/badge"
import { OUTCOME_LABELS } from "@/lib/format"
import type { Outcome } from "@/lib/types"

// Tinted status colors adapted from blocks.so table-03 (MIT).
const TINTS: Record<Outcome | "LIVE", string> = {
  COMPLETED: "bg-green-500/15 text-green-700 dark:bg-green-500/10 dark:text-green-400",
  HANDED_OFF: "bg-blue-500/15 text-blue-700 dark:bg-blue-500/10 dark:text-blue-400",
  VOICEMAIL: "bg-amber-500/15 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300",
  ABANDONED: "bg-muted text-muted-foreground",
  FAILED: "bg-rose-500/15 text-rose-700 dark:bg-rose-500/10 dark:text-rose-400",
  LIVE: "bg-emerald-500/15 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400",
}

export function OutcomeBadge({ outcome }: { outcome: Outcome | null }) {
  return (
    <Badge variant="outline" className={`border-0 ${TINTS[outcome ?? "LIVE"]}`}>
      {outcome ? OUTCOME_LABELS[outcome] : "Live"}
    </Badge>
  )
}
