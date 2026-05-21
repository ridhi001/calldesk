"use client"

import NumberFlow from "@number-flow/react"
import type { ComponentProps } from "react"
import { Card, CardContent, CardTitle } from "@/components/ui/card"
import type { Metrics } from "@/lib/types"

// Layout adapted from blocks.so stats-02 (MIT): one bordered strip of joined stat cells.
type Stat = { label: string; value: number | null; suffix?: string; format?: ComponentProps<typeof NumberFlow>["format"]; hint: string }

const percent: Stat["format"] = { style: "percent", maximumFractionDigits: 0 }

export function MetricCards({ metrics }: { metrics: Metrics }) {
  const stats: Stat[] = [
    { label: "Calls handled", value: metrics.totalCalls, hint: `${metrics.bargeInCount} with a barge-in` },
    { label: "Turn latency p50", value: metrics.turnLatencyMs.p50, suffix: " ms", hint: "Caller stops → agent speaks" },
    { label: "Turn latency p95", value: metrics.turnLatencyMs.p95, suffix: " ms", hint: "Slowest 5% of turns" },
    { label: "Knowledge coverage", value: metrics.knowledge?.coverageRate ?? null, format: percent, hint: "Questions answered from the KB" },
    { label: "Handoff rate", value: metrics.handoffRate, format: percent, hint: "Transferred to a human" },
  ]

  return (
    <ul
      aria-label="Key metrics"
      className="grid grid-cols-2 gap-px overflow-hidden rounded-xl bg-border ring-1 ring-foreground/10 md:grid-cols-3 lg:grid-cols-5"
    >
      {stats.map((stat) => (
        <li key={stat.label} className="flex">
          <Card className="flex-1 rounded-none py-0 ring-0">
            <CardContent className="flex flex-col gap-1 p-4">
              <CardTitle className="text-sm font-normal text-muted-foreground">{stat.label}</CardTitle>
              <p className="text-2xl font-semibold tabular-nums">
                {stat.value == null ? "—" : <NumberFlow value={stat.value} suffix={stat.suffix} format={stat.format} />}
              </p>
              <p className="text-xs text-muted-foreground">{stat.hint}</p>
            </CardContent>
          </Card>
        </li>
      ))}
    </ul>
  )
}
