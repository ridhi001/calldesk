"use client"

import { Bar, BarChart, CartesianGrid, Cell, XAxis, YAxis } from "recharts"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { OUTCOME_LABELS } from "@/lib/format"
import type { Metrics, Outcome } from "@/lib/types"

const config = { calls: { label: "Calls", color: "var(--chart-1)" } } satisfies ChartConfig

const COLORS: Record<Outcome, string> = {
  COMPLETED: "var(--outcome-completed)",
  HANDED_OFF: "var(--outcome-handed-off)",
  VOICEMAIL: "var(--outcome-voicemail)",
  ABANDONED: "var(--outcome-abandoned)",
  FAILED: "var(--outcome-failed)",
}

export function OutcomeChart({ outcomes }: { outcomes: Metrics["outcomeBreakdown"] }) {
  const data = (Object.keys(OUTCOME_LABELS) as Outcome[]).map((outcome) => ({
    outcome: OUTCOME_LABELS[outcome],
    calls: outcomes[outcome] ?? 0,
    fill: COLORS[outcome],
  }))

  return (
    <ChartContainer config={config} className="h-56 w-full">
      <BarChart data={data} accessibilityLayer>
        <CartesianGrid vertical={false} />
        <XAxis dataKey="outcome" tickLine={false} axisLine={false} />
        <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={28} />
        <ChartTooltip cursor={false} content={<ChartTooltipContent />} />
        <Bar dataKey="calls" radius={4} isAnimationActive={false}>
          {data.map((point) => (
            <Cell key={point.outcome} fill={point.fill} />
          ))}
        </Bar>
      </BarChart>
    </ChartContainer>
  )
}
