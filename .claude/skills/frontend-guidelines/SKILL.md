---
name: frontend-guidelines
description: House rules for writing HTML, CSS and TypeScript in the CallDesk dashboard (Next.js + Tailwind + shadcn/ui). Load before writing or reviewing any dashboard component, page or style.
---

# Frontend guidelines (CallDesk dashboard)

Adapted from bendc/frontend-guidelines, keeping the rules that still hold in a 2026 Next.js + Tailwind codebase and dropping the ones modern tooling handles (vendor prefixes, semicolon style, ES5 idioms).

## HTML and accessibility
- Use semantic elements for what they are: `<main>`, `<nav>`, `<header>`, `<section>` with a heading, `<table>` for tabular data (call lists and transcripts are tables or lists, not div grids), `<button>` for actions, `<a>` for navigation.
- Every interactive element is reachable and operable by keyboard, with a visible focus ring. Never remove outlines without a replacement.
- Images and icons that carry meaning get text alternatives; decorative icons get `aria-hidden`.
- Status that changes live (a call starting or ending, a new turn) goes in a polite `aria-live` region.
- Set `lang` on `<html>`. Don't block rendering with scripts.

## CSS / Tailwind
- Lay out with Flexbox and Grid. Keep the normal document flow; use absolute positioning only for overlays and decorations.
- Keep specificity low: utility classes and component variants, no `!important`, no deep descendant selectors.
- Use design tokens (CSS variables from the shadcn theme) for color, radius and spacing, never one-off hex values in components.
- Support light and dark mode from the start. Test both.
- Respect `prefers-reduced-motion`: every animation has a reduced or no-motion path.

## TypeScript / React
- Favor readability and correctness over cleverness. Small, pure components; derive values instead of syncing state.
- `const` by default. No `any`. Type API responses at the boundary (one `api.ts` with typed fetchers).
- Use array methods (`map`, `filter`, `reduce`) over manual loops when they read better.
- Compose small functions instead of nesting deeply; return early.
- Keep dependencies minimal. Before adding a library, check whether the platform, React, or an existing dependency already does it (see the `pick-ui-library` skill for when a library is the right call).
- Server Components by default; add `"use client"` only for components that need state, effects or browser APIs (live SSE feed, charts, interactive filters).

## Motion
Follow the `emil-design-eng`, `animate` and `review-animations` skills: animate only to clarify a change (a new call sliding into the live list, a turn appearing in a transcript), keep durations short, use proper easing, never animate layout on every render.
