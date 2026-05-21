import Link from "next/link"
import { BackendOffline } from "@/components/backend-offline"
import { OutcomeBadge } from "@/components/outcome-badge"
import { OutcomeFilter } from "@/components/outcome-filter"
import { PageHeader } from "@/components/page-header"
import { buttonVariants } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { BackendUnavailableError, getCalls } from "@/lib/api"
import { formatDuration, formatTime, OUTCOME_LABELS } from "@/lib/format"
import type { Outcome } from "@/lib/types"

const PAGE_SIZE = 20

export default async function CallsPage(props: PageProps<"/calls">) {
  const params = await props.searchParams
  const first = (value: string | string[] | undefined) => (Array.isArray(value) ? value[0] : value)
  const page = Math.max(0, Number(first(params.page) ?? 0) || 0)
  const outcomeParam = first(params.outcome)
  const outcome = outcomeParam && outcomeParam in OUTCOME_LABELS ? (outcomeParam as Outcome) : null
  const pageHref = (target: number) => `/calls?page=${target}${outcome ? `&outcome=${outcome}` : ""}`

  let calls
  try {
    calls = await getCalls(page, PAGE_SIZE, outcome)
  } catch (error) {
    if (error instanceof BackendUnavailableError) return <BackendOffline />
    throw error
  }

  return (
    <>
      <PageHeader
        title="Calls"
        description={`${calls.totalElements} ${outcome ? OUTCOME_LABELS[outcome].toLowerCase() + " " : ""}calls, newest first.`}
        actions={<OutcomeFilter value={outcome} />}
      />

      <div className="rounded-xl bg-card ring-1 ring-foreground/10">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-16">Call</TableHead>
              <TableHead>From</TableHead>
              <TableHead>Started</TableHead>
              <TableHead>Duration</TableHead>
              <TableHead className="text-right">Turns</TableHead>
              <TableHead className="text-right">Barge-ins</TableHead>
              <TableHead>Outcome</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {calls.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                  {outcome ? "No calls with this outcome." : "No calls yet."}
                </TableCell>
              </TableRow>
            )}
            {calls.content.map((call) => (
              <TableRow key={call.id} className="hover:bg-muted/50">
                <TableCell>
                  <Link href={`/calls/${call.id}`} className="font-mono text-xs underline-offset-4 hover:underline">
                    #{call.id}
                  </Link>
                </TableCell>
                <TableCell>{call.fromNumber}</TableCell>
                <TableCell className="text-muted-foreground">{formatTime(call.startedAt)}</TableCell>
                <TableCell className="tabular-nums">{formatDuration(call.startedAt, call.endedAt)}</TableCell>
                <TableCell className="text-right tabular-nums">{call.turnCount}</TableCell>
                <TableCell className="text-right tabular-nums">{call.bargeInCount}</TableCell>
                <TableCell>
                  <OutcomeBadge outcome={call.outcome} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {calls.totalPages > 1 && (
        <nav aria-label="Pagination" className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">
            Page {page + 1} of {calls.totalPages}
          </span>
          <div className="flex gap-2">
            {page > 0 && (
              <Link href={pageHref(page - 1)} className={buttonVariants({ variant: "outline", size: "sm" })}>
                Previous
              </Link>
            )}
            {page + 1 < calls.totalPages && (
              <Link href={pageHref(page + 1)} className={buttonVariants({ variant: "outline", size: "sm" })}>
                Next
              </Link>
            )}
          </div>
        </nav>
      )}
    </>
  )
}
