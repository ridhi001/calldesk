"use client"

import { useState, useTransition } from "react"
import { SearchIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { searchKnowledge } from "@/lib/api"
import type { SearchResult } from "@/lib/types"

const EXAMPLES = ["Are you open on Saturday?", "Do you offer braces for adults?", "How much is a cleaning?"]

export function TestQuestion() {
  const [question, setQuestion] = useState("")
  const [result, setResult] = useState<SearchResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  function run(text: string) {
    if (!text.trim()) return
    setQuestion(text)
    startTransition(async () => {
      try {
        setResult(await searchKnowledge(text))
        setError(null)
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Search failed")
      }
    })
  }

  const topScore = result?.matches[0]?.score ?? 1

  return (
    <Card>
      <CardHeader>
        <CardTitle>Test a question</CardTitle>
        <CardDescription>See which entries the agent would answer from, before a caller asks.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <form
          role="search"
          className="flex gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            run(question)
          }}
        >
          <Input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="What would a caller ask?" aria-label="Caller question" />
          <Button type="submit" variant="secondary" disabled={pending || !question.trim()} aria-label="Test question">
            <SearchIcon aria-hidden />
          </Button>
        </form>

        {!result && !error && (
          <div className="flex flex-wrap gap-2">
            {EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => run(example)}
                className="rounded-full border px-2.5 py-1 text-xs text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
              >
                {example}
              </button>
            ))}
          </div>
        )}

        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

        {result && (
          <div aria-live="polite" className="flex flex-col gap-3">
            <p className="text-sm">
              {result.confident ? (
                <Badge variant="outline" className="border-0 bg-green-500/15 text-green-700 dark:bg-green-500/10 dark:text-green-400">Would answer</Badge>
              ) : (
                <Badge variant="outline" className="border-0 bg-amber-500/15 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300">No confident match: would log a gap</Badge>
              )}
            </p>
            {result.matches.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nothing in the knowledge base is related to this question.</p>
            ) : (
              <ol className="flex flex-col gap-3">
                {result.matches.map((match) => (
                  <li key={match.entry.id} className="enter-item flex flex-col gap-1.5">
                    <div className="flex items-baseline justify-between gap-3 text-sm">
                      <span className={match.confident ? "font-medium" : "text-muted-foreground"}>{match.entry.question}</span>
                      <span className="shrink-0 font-mono text-xs tabular-nums text-muted-foreground">{match.score.toFixed(1)}</span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden>
                      <div
                        className={`h-full rounded-full ${match.confident ? "bg-primary" : "bg-muted-foreground/40"}`}
                        style={{ width: `${Math.max(4, (match.score / topScore) * 100)}%` }}
                      />
                    </div>
                    {match.confident && <p className="line-clamp-2 text-xs text-muted-foreground">{match.entry.answer}</p>}
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
