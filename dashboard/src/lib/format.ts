import type { Outcome } from "./types"

export const OUTCOME_LABELS: Record<Outcome, string> = {
  COMPLETED: "Completed",
  HANDED_OFF: "Handed off",
  VOICEMAIL: "Voicemail",
  ABANDONED: "Abandoned",
  FAILED: "Failed",
}

export function formatMs(value: number | null | undefined): string {
  if (value == null) return "—"
  return value >= 1000 ? `${(value / 1000).toFixed(2)} s` : `${Math.round(value)} ms`
}

export function formatDuration(startedAt: string, endedAt: string | null): string {
  if (!endedAt) return "Live"
  const seconds = Math.max(0, Math.round((Date.parse(endedAt) - Date.parse(startedAt)) / 1000))
  const minutes = Math.floor(seconds / 60)
  return minutes > 0 ? `${minutes}m ${seconds % 60}s` : `${seconds}s`
}

// Times are shown in the business's time zone, not the server's (Vercel renders in UTC).
const TIME_ZONE = process.env.NEXT_PUBLIC_TIME_ZONE || "Asia/Kolkata"

const timeFormat = new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: TIME_ZONE })

export function formatTime(iso: string): string {
  return timeFormat.format(new Date(iso))
}

const clockFormat = new Intl.DateTimeFormat("en-IN", { timeStyle: "short", timeZone: TIME_ZONE })

export function formatClock(iso: string): string {
  return clockFormat.format(new Date(iso))
}
