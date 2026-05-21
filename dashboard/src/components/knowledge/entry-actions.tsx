"use client"

import { useRouter } from "next/navigation"
import { useTransition } from "react"
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { toast } from "sonner"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { EntryDialog } from "@/components/knowledge/entry-dialog"
import { createEntry, deleteEntry, updateEntry } from "@/lib/api"
import type { KnowledgeEntry } from "@/lib/types"

export function AddEntryButton() {
  return (
    <EntryDialog
      trigger={
        <Button>
          <PlusIcon data-icon="inline-start" aria-hidden />
          Add entry
        </Button>
      }
      title="Add knowledge"
      description="The agent answers callers from these entries. It never makes up facts that aren't here."
      submitLabel="Add entry"
      onSubmit={createEntry}
      successMessage="Entry added. The agent can use it on the next call."
    />
  )
}

export function EntryRowActions({ entry }: { entry: KnowledgeEntry }) {
  const router = useRouter()
  const [pending, startTransition] = useTransition()

  function remove() {
    startTransition(async () => {
      try {
        await deleteEntry(entry.id)
        toast.success("Entry deleted")
        router.refresh()
      } catch (cause) {
        toast.error(cause instanceof Error ? cause.message : "Could not delete the entry")
      }
    })
  }

  return (
    <div className="flex justify-end gap-1">
      <EntryDialog
        trigger={
          <Button variant="ghost" size="icon-sm" aria-label={`Edit "${entry.question}"`}>
            <PencilIcon aria-hidden />
          </Button>
        }
        title="Edit knowledge"
        description="Changes apply to the next caller who asks."
        submitLabel="Save changes"
        initial={entry}
        onSubmit={(input) => updateEntry(entry.id, input)}
        successMessage="Entry updated"
      />
      <AlertDialog>
        <AlertDialogTrigger
          render={
            <Button variant="ghost" size="icon-sm" aria-label={`Delete "${entry.question}"`} disabled={pending}>
              <Trash2Icon aria-hidden />
            </Button>
          }
        />
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this entry?</AlertDialogTitle>
            <AlertDialogDescription>
              &ldquo;{entry.question}&rdquo; will no longer be available to the agent. Past transcripts keep their record of it.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={remove}>
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
