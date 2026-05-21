# CallDesk Dashboard

Next.js 16 dashboard for CallDesk: live calls, call transcripts, and a per-turn latency breakdown (speech-to-text → LLM first token → TTS first audio → audio sent to the caller).

## Run

```bash
cp .env.example .env.local
npm install
npm run dev          # http://localhost:3000, expects the backend on NEXT_PUBLIC_API_URL
```

No backend yet? Run on sample data:

```bash
NEXT_PUBLIC_DEMO_MODE=true npm run dev
```

## Stack

Next.js 16 (App Router, Server Components), React 19, Tailwind CSS 4, shadcn/ui on Base UI, recharts, NumberFlow, Sonner, next-themes.

The stats strip and the filterable calls table are adapted from [blocks.so](https://blocks.so) (MIT).

## Pages

- `/`: key metrics (p50/p95 turn latency, handoff rate, barge-ins), live call feed over Server-Sent Events, call outcome chart, and a button to start a simulated call.
- `/calls`: paginated call history, filterable by outcome (`?outcome=HANDED_OFF`).
- `/calls/[id]`: transcript with interrupted turns marked and a stacked latency bar per agent turn.
