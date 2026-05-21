"use client"

import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import type { Metrics } from "@/lib/types"

const config = { latency: { label: "Median turn latency (ms)", color: "var(--chart-1)" } } satisfies ChartConfig

export function LatencyTrendChart({ trend }: { trend: Metrics["latencyTrend"] }) {
  const data = trend.filter((point) => point.p50TurnLatencyMs != null).map((point) => ({ call: `#${point.callId}`, latency: point.p50TurnLatencyMs }))

  if (data.length < 2) {
    return <p className="flex h-56 items-center justify-center text-sm text-muted-foreground">Latency trend appears after a few calls.</p>
  }

  return (
    <ChartContainer config={config} className="h-56 w-full">
      <AreaChart data={data} accessibilityLayer margin={{ top: 8, left: 4, right: 8 }}>
        <defs>
          <linearGradient id="latency-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-latency)" stopOpacity={0.3} />
            <stop offset="100%" stopColor="var(--color-latency)" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} />
        <XAxis dataKey="call" tickLine={false} axisLine={false} minTickGap={16} />
        <YAxis tickLine={false} axisLine={false} width={56} tickFormatter={(value: number) => `${value}\u00a0ms`} />
        <ChartTooltip cursor={false} content={<ChartTooltipContent />} />
        <Area dataKey="latency" type="monotone" stroke="var(--color-latency)" strokeWidth={2} fill="url(#latency-fill)" isAnimationActive={false} />
      </AreaChart>
    </ChartContainer>
  )
}
