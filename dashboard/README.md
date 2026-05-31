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

## Configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `NEXT_PUBLIC_API_URL` | Backend URL used by the browser | `http://localhost:8080` |
| `CALLDESK_API_INTERNAL_URL` | Backend URL for server-side rendering, if different (Docker Compose sets `http://backend:8080`) | same as above |
| `NEXT_PUBLIC_DEMO_MODE` | `true` runs on built-in sample data, read-only | `false` |
| `NEXT_PUBLIC_TIME_ZONE` | IANA time zone for displaying call times | `Asia/Kolkata` |

`NEXT_PUBLIC_*` values are inlined at build time, so rebuild after changing them.

## Deployment

The public demo is deployed on Vercel from this repository with **Root Directory** set to `dashboard` and `NEXT_PUBLIC_DEMO_MODE=true`; every push to `main` redeploys it. To point a deployment at a real backend instead, set `NEXT_PUBLIC_API_URL` to the backend's public URL and add the dashboard's URL to the backend's `CORS_ALLOWED_ORIGINS`.

## Stack

Next.js 16 (App Router, Server Components), React 19, Tailwind CSS 4, shadcn/ui on Base UI, recharts, NumberFlow, Sonner, next-themes.

The stats strip and the filterable calls table are adapted from [blocks.so](https://blocks.so) (MIT).

## Pages

- `/`: key metrics (p50/p95 turn latency, handoff rate, barge-ins), live call feed over Server-Sent Events, call outcome chart, and a button to start a simulated call.
- `/calls`: paginated call history, filterable by outcome (`?outcome=HANDED_OFF`).
- `/calls/[id]`: transcript with interrupted turns marked and a stacked latency bar per agent turn.
