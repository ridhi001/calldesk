import Link from "next/link"
import { ArrowRightIcon, CircleCheckIcon } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import type { KnowledgeGap } from "@/lib/types"

export function GapsCard({ gaps }: { gaps: KnowledgeGap[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Knowledge gaps</CardTitle>
        <CardDescription>Questions callers asked that the agent couldn&apos;t answer</CardDescription>
        <CardAction>
          <Link href="/knowledge#gaps" className={buttonVariants({ variant: "ghost", size: "sm" })}>
            Review
            <ArrowRightIcon data-icon="inline-end" aria-hidden />
          </Link>
        </CardAction>
      </CardHeader>
      <CardContent>
        {gaps.length === 0 ? (
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <CircleCheckIcon className="size-4 text-emerald-500" aria-hidden />
            Every recent question had an answer.
          </p>
        ) : (
          <ul className="flex flex-col gap-3">
            {gaps.slice(0, 4).map((gap) => (
              <li key={gap.id} className="flex items-start justify-between gap-3 text-sm">
                <span className="min-w-0">&ldquo;{gap.question}&rdquo;</span>
                <span className="shrink-0 rounded-md bg-amber-500/15 px-1.5 py-0.5 text-xs font-medium tabular-nums text-amber-700 dark:text-amber-300">
                  {gap.timesAsked}× asked
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
