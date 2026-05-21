"use client"

import { useRouter } from "next/navigation"
import { useState, useTransition, type ReactElement } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import type { KnowledgeInput } from "@/lib/types"

type Props = {
  trigger: ReactElement
  title: string
  description: string
  submitLabel: string
  initial?: Partial<KnowledgeInput>
  onSubmit: (input: KnowledgeInput) => Promise<unknown>
  successMessage: string
}

export function EntryDialog({ trigger, title, description, submitLabel, initial, onSubmit, successMessage }: Props) {
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  function submit(form: FormData) {
    const input: KnowledgeInput = {
      question: String(form.get("question") ?? "").trim(),
      answer: String(form.get("answer") ?? "").trim(),
      tags: String(form.get("tags") ?? "").split(",").map((tag) => tag.trim().toLowerCase()).filter(Boolean),
    }
    startTransition(async () => {
      try {
        await onSubmit(input)
        setOpen(false)
        setError(null)
        toast.success(successMessage)
        router.refresh()
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Something went wrong")
      }
    })
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { setOpen(next); if (!next) setError(null) }}>
      <DialogTrigger render={trigger} />
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <form action={submit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="entry-question">Question</Label>
            <Input id="entry-question" name="question" defaultValue={initial?.question} placeholder="Do you offer braces for adults?" required maxLength={300} />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="entry-answer">Answer</Label>
            <Textarea
              id="entry-answer"
              name="answer"
              defaultValue={initial?.answer}
              placeholder="Write it the way the agent should say it on the phone: short, spoken sentences."
              required
              maxLength={2000}
              rows={5}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="entry-tags">Tags</Label>
            <Input id="entry-tags" name="tags" defaultValue={initial?.tags?.join(", ")} placeholder="treatments, orthodontics" />
            <p className="text-xs text-muted-foreground">Comma separated. Tags are searchable and help the agent match related questions.</p>
          </div>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="submit" disabled={pending}>
              {pending ? "Saving…" : submitLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
