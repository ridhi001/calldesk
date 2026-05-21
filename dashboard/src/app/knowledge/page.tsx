import type { Metadata } from "next"
import { ClockIcon, MapPinIcon, PhoneForwardedIcon } from "lucide-react"
import { BackendOffline } from "@/components/backend-offline"
import { AddEntryButton, EntryRowActions } from "@/components/knowledge/entry-actions"
import { GapsList } from "@/components/knowledge/gaps-list"
import { TestQuestion } from "@/components/knowledge/test-question"
import { PageHeader } from "@/components/page-header"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { BackendUnavailableError, DEMO_MODE, getBusiness, getGaps, getKnowledge, getMetrics } from "@/lib/api"
import { formatTime } from "@/lib/format"

export const metadata: Metadata = { title: "Knowledge base" }

export default async function KnowledgePage(props: PageProps<"/knowledge">) {
  const params = await props.searchParams
  const q = (Array.isArray(params.q) ? params.q[0] : params.q)?.trim() ?? ""

  let data
  try {
    const [business, entries, gaps, metrics] = await Promise.all([getBusiness(), getKnowledge(q), getGaps("OPEN"), getMetrics()])
    data = { business, entries, gaps, metrics }
  } catch (error) {
    if (error instanceof BackendUnavailableError) return <BackendOffline />
    throw error
  }
  const { business, entries, gaps, metrics } = data
  const coverage = metrics.knowledge?.coverageRate

  return (
    <>
      <PageHeader
        title="Knowledge base"
        description={
          <>
            Everything the agent knows about {business.name}. It answers only from these entries
            {coverage != null && <> and currently covers <strong className="font-medium text-foreground">{Math.round(coverage * 100)}%</strong> of caller questions</>}.
          </>
        }
        actions={DEMO_MODE ? <Badge variant="secondary">Read-only demo</Badge> : <AddEntryButton />}
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle>{business.name}</CardTitle>
            <CardDescription>Business facts the agent always knows</CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="flex flex-col gap-3 text-sm">
              <div className="flex gap-3">
                <dt><ClockIcon className="mt-0.5 size-4 text-muted-foreground" aria-label="Hours" /></dt>
                <dd>{business.hours}</dd>
              </div>
              <div className="flex gap-3">
                <dt><MapPinIcon className="mt-0.5 size-4 text-muted-foreground" aria-label="Address" /></dt>
                <dd>{business.address}</dd>
              </div>
              <div className="flex gap-3">
                <dt><PhoneForwardedIcon className="mt-0.5 size-4 text-muted-foreground" aria-label="Handoff number" /></dt>
                <dd>Transfers to {business.handoffNumber}</dd>
              </div>
            </dl>
            <p className="mt-4 rounded-lg bg-muted/60 px-3 py-2 text-xs text-muted-foreground">&ldquo;{business.greeting}&rdquo;</p>
          </CardContent>
        </Card>
        <div className="lg:col-span-2">
          <TestQuestion />
        </div>
      </div>

      <section id="gaps" aria-labelledby="gaps-heading" className="flex scroll-mt-20 flex-col gap-3">
        <div>
          <h2 id="gaps-heading" className="flex items-center gap-2 font-heading text-base font-medium">
            Knowledge gaps
            {gaps.length > 0 && <Badge variant="outline" className="border-0 bg-amber-500/15 text-amber-700 dark:text-amber-300">{gaps.length} open</Badge>}
          </h2>
          <p className="text-sm text-muted-foreground">Questions callers asked that had no confident answer, most asked first.</p>
        </div>
        <GapsList gaps={gaps} />
      </section>

      <section aria-labelledby="entries-heading" className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 id="entries-heading" className="font-heading text-base font-medium">Entries</h2>
            <p className="text-sm text-muted-foreground">
              {entries.length} {q ? `matching "${q}"` : "entries"} · most recently updated first
            </p>
          </div>
          <form role="search" className="w-full sm:w-72">
            <Input name="q" type="search" defaultValue={q} placeholder="Search questions, answers, tags" aria-label="Search entries" />
          </form>
        </div>

        <div className="rounded-xl bg-card ring-1 ring-foreground/10">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[45%]">Question and answer</TableHead>
                <TableHead>Tags</TableHead>
                <TableHead className="text-right">Used</TableHead>
                <TableHead>Updated</TableHead>
                {!DEMO_MODE && <TableHead className="w-20"><span className="sr-only">Actions</span></TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-10 text-center text-muted-foreground">
                    {q ? "No entries match your search." : "No entries yet. Add the first thing callers usually ask."}
                  </TableCell>
                </TableRow>
              )}
              {entries.map((entry) => (
                <TableRow key={entry.id} id={`entry-${entry.id}`} className="scroll-mt-20 align-top hover:bg-muted/50 target:bg-primary/5">
                  <TableCell className="whitespace-normal">
                    <p className="font-medium">{entry.question}</p>
                    <p className="mt-1 line-clamp-2 text-muted-foreground">{entry.answer}</p>
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    <div className="flex flex-wrap gap-1">
                      {entry.tags.map((tag) => (
                        <Badge key={tag} variant="secondary">{tag}</Badge>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{entry.timesUsed}×</TableCell>
                  <TableCell className="text-muted-foreground">{formatTime(entry.updatedAt)}</TableCell>
                  {!DEMO_MODE && (
                    <TableCell>
                      <EntryRowActions entry={entry} />
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>
    </>
  )
}
