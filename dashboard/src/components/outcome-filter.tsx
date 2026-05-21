"use client"

import { usePathname, useRouter } from "next/navigation"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { OUTCOME_LABELS } from "@/lib/format"
import type { Outcome } from "@/lib/types"

// Filter control adapted from blocks.so table-03 (MIT); the choice lives in the URL so it survives reloads and pagination.
const ITEMS = [
  { value: "all", label: "All outcomes" },
  ...(Object.keys(OUTCOME_LABELS) as Outcome[]).map((outcome) => ({ value: outcome, label: OUTCOME_LABELS[outcome] })),
]

export function OutcomeFilter({ value }: { value: Outcome | null }) {
  const router = useRouter()
  const pathname = usePathname()

  return (
    <Select
      items={ITEMS}
      value={value ?? "all"}
      onValueChange={(next) => {
        if (!next) return
        router.push(next === "all" ? pathname : `${pathname}?outcome=${next}`)
      }}
    >
      <SelectTrigger className="w-full sm:w-44" aria-label="Filter by outcome">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {ITEMS.map((item) => (
          <SelectItem key={item.value} value={item.value}>
            {item.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
