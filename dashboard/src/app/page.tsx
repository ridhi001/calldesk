import { BackendOffline } from "@/components/backend-offline"
import { GapsCard } from "@/components/gaps-card"
import { LatencyTrendChart } from "@/components/latency-trend-chart"
import { LiveCalls } from "@/components/live-calls"
import { MetricCards } from "@/components/metric-cards"
import { OutcomeChart } from "@/components/outcome-chart"
import { PageHeader } from "@/components/page-header"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { BackendUnavailableError, getBusiness, getCalls, getGaps, getMetrics } from "@/lib/api"

export default async function OverviewPage() {
  let data
  try {
    const [metrics, calls, gaps, business] = await Promise.all([getMetrics(), getCalls(0, 6), getGaps("OPEN"), getBusiness()])
    data = { metrics, calls, gaps, business }
  } catch (error) {
    if (error instanceof BackendUnavailableError) return <BackendOffline />
    throw error
  }

  return (
    <>
      <PageHeader title="Overview" description={`How the AI receptionist is handling calls for ${data.business.name}.`} />

      <MetricCards metrics={data.metrics} />

      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Turn latency</CardTitle>
            <CardDescription>Median time from the caller finishing to the agent speaking, per call</CardDescription>
          </CardHeader>
          <CardContent>
            <LatencyTrendChart trend={data.metrics.latencyTrend ?? []} />
          </CardContent>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Call outcomes</CardTitle>
            <CardDescription>How each call ended</CardDescription>
          </CardHeader>
          <CardContent>
            <OutcomeChart outcomes={data.metrics.outcomeBreakdown} />
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <LiveCalls initial={data.calls.content} />
        </div>
        <div className="lg:col-span-2">
          <GapsCard gaps={data.gaps} />
        </div>
      </div>
    </>
  )
}
