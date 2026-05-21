import type { Business, CallDetail, KnowledgeEntry, KnowledgeGap, Metrics, Outcome, SearchResult, Turn, TurnSource } from "./types"

// Sample data for DEMO MODE only (NEXT_PUBLIC_DEMO_MODE=true). The UI labels it as demo data.

const DAY = Date.parse("2026-09-26T03:30:00Z") // 09:00 IST
const at = (minutes: number) => new Date(DAY + minutes * 60_000).toISOString()

export const demoBusiness: Business = {
  name: "Brightsmile Dental",
  greeting: "Thanks for calling Brightsmile Dental, this is the virtual receptionist. How can I help?",
  hours: "Mon–Fri 9 am–7 pm, Sat 9 am–1 pm, closed Sunday",
  address: "14 Park Street, Bhubaneswar 751001",
  handoffNumber: "+91 674 555 0142",
}

const entry = (id: number, question: string, answer: string, tags: string[], timesUsed: number, day: number): KnowledgeEntry => ({
  id, question, answer, tags, timesUsed,
  createdAt: at(-60 * 24 * 14 + id), updatedAt: at(-60 * 24 * day), lastUsedAt: timesUsed > 0 ? at(300 - id * 7) : null,
})

export const demoKnowledge: KnowledgeEntry[] = [
  entry(1, "What are your opening hours?", "We're open Monday to Friday from 9 am to 7 pm and Saturday from 9 am to 1 pm. We're closed on Sundays.", ["hours"], 14, 3),
  entry(2, "Where is the clinic located?", "We're at 14 Park Street, Bhubaneswar, next to the Park Street metro exit. There's free parking behind the building.", ["location", "parking"], 9, 10),
  entry(3, "How do I book an appointment?", "I can book you in right now. Tell me the day and time that suits you and I'll check what's free.", ["booking"], 21, 2),
  entry(4, "Do you offer teeth whitening?", "Yes. We offer in-clinic whitening in about an hour, and take-home kits. A dentist checks your teeth first to pick the right option.", ["treatments", "whitening"], 6, 6),
  entry(5, "How much does a cleaning cost?", "A regular cleaning and check-up is ₹1,200. Deep cleaning starts at ₹2,500 and depends on what the dentist finds.", ["pricing", "cleaning"], 8, 5),
  entry(6, "Do you take insurance?", "We accept most major health insurers, including Star Health and HDFC ERGO. Bring your policy card and we'll handle the claim.", ["insurance", "billing"], 5, 8),
  entry(7, "What should I do in a dental emergency?", "Call us straight away. We keep same-day emergency slots. Outside opening hours, go to AMRI Hospital's emergency ward.", ["emergency"], 3, 12),
  entry(8, "Can I reschedule or cancel?", "Yes, just tell me your name and appointment time. Please give us 24 hours' notice so we can offer the slot to someone else.", ["booking"], 7, 4),
  entry(9, "Do you treat children?", "Yes, we see children from age three. Our paediatric slots are on weekday afternoons.", ["children"], 4, 9),
  entry(10, "What payment methods do you accept?", "We accept UPI, all major cards and cash. Treatment plans over ₹10,000 can be split into two payments.", ["billing", "pricing"], 2, 7),
  entry(11, "Do you do root canals?", "Yes. Most root canal treatments take two visits, and we use local anaesthetic so it's comfortable.", ["treatments"], 3, 11),
  entry(12, "How long is a first visit?", "Plan for about 45 minutes. That covers a full check-up, X-rays if needed, and a treatment plan.", ["booking"], 5, 6),
]

export const demoGaps: KnowledgeGap[] = [
  { id: 1, question: "Do you offer braces for adults?", timesAsked: 4, firstAskedAt: at(40), lastAskedAt: at(410), lastCallId: 11, bestScore: 0.9, status: "OPEN", resolvedEntryId: null },
  { id: 2, question: "Can I pay in monthly installments?", timesAsked: 3, firstAskedAt: at(95), lastAskedAt: at(380), lastCallId: 10, bestScore: 1.2, status: "OPEN", resolvedEntryId: null },
  { id: 3, question: "Is there a female dentist available?", timesAsked: 1, firstAskedAt: at(260), lastAskedAt: at(260), lastCallId: 8, bestScore: null, status: "OPEN", resolvedEntryId: null },
]

const src = (id: number, score: number): TurnSource => ({ entryId: id, question: demoKnowledge[id - 1].question, score })

const base = { sttMs: null, llmFirstTokenMs: null, ttsFirstAudioMs: null, turnLatencyMs: null, interrupted: false, sources: [] }
const greeting = (minutes: number): Turn => ({ ...base, index: 0, role: "AGENT", text: demoBusiness.greeting, startedAt: at(minutes) })
const caller = (index: number, text: string, minutes: number): Turn => ({ ...base, index, role: "CALLER", text, startedAt: at(minutes), sttMs: 140 })
function agent(index: number, text: string, minutes: number, latency: [number, number, number, number], sources: TurnSource[] = [], interrupted = false): Turn {
  const [stt, llm, tts, sent] = latency
  return { index, role: "AGENT", text, startedAt: at(minutes), sttMs: stt, llmFirstTokenMs: llm, ttsFirstAudioMs: tts, turnLatencyMs: sent, interrupted, sources }
}

type Script = { id: number; minute: number; length: number; outcome: Outcome; from: string; turns: (m: number) => Turn[] }

const scripts: Script[] = [
  { id: 1, minute: 5, length: 1.6, outcome: "COMPLETED", from: "+91 98610 10421", turns: (m) => [
    greeting(m), caller(1, "Hi, what time do you open on Saturday?", m + 0.1),
    agent(2, "On Saturdays we're open from 9 am to 1 pm.", m + 0.2, [150, 330, 450, 486], [src(1, 6.8)]),
    caller(3, "Great, thanks.", m + 0.4) ] },
  { id: 2, minute: 22, length: 3.1, outcome: "COMPLETED", from: "+91 97760 55310", turns: (m) => [
    greeting(m), caller(1, "I'd like to book a cleaning for next week.", m + 0.1),
    agent(2, "Sure. We have Tuesday at 10 am or Thursday at 2 pm. Which works better?", m + 0.2, [160, 350, 470, 512], [src(3, 5.9), src(5, 2.1)]),
    caller(3, "Thursday please. How much is it?", m + 0.5),
    agent(4, "A regular cleaning and check-up is ₹1,200. I've booked you for Thursday at 2 pm.", m + 0.6, [140, 320, 430, 468], [src(5, 7.4)]),
    caller(5, "Perfect.", m + 0.9) ] },
  { id: 3, minute: 40, length: 1.9, outcome: "COMPLETED", from: "+91 90400 66012", turns: (m) => [
    greeting(m), caller(1, "Do you offer braces for adults?", m + 0.1),
    agent(2, "I'm not sure about that one. I can take a message or connect you with our receptionist.", m + 0.2, [170, 380, 500, 541]),
    caller(3, "I'll call back later.", m + 0.4) ] },
  { id: 4, minute: 71, length: 0.5, outcome: "VOICEMAIL", from: "+91 88950 24590", turns: (m) => [
    greeting(m), caller(1, "Please leave a message after the tone.", m + 0.1) ] },
  { id: 5, minute: 95, length: 2.4, outcome: "COMPLETED", from: "+91 99370 01877", turns: (m) => [
    greeting(m), caller(1, "Can you tell me about teeth whitening?", m + 0.1),
    agent(2, "Yes. We offer in-clinic whitening in about an hour.", m + 0.2, [150, 340, 460, 497], [src(4, 7.1)], true),
    caller(3, "Actually, can I pay in monthly installments?", m + 0.4),
    agent(4, "I'm not sure about that one. I can take a message or connect you with our receptionist.", m + 0.5, [160, 360, 480, 520]),
    caller(5, "Okay, take a message please.", m + 0.8) ] },
  { id: 6, minute: 130, length: 1.4, outcome: "HANDED_OFF", from: "+91 94370 88213", turns: (m) => [
    greeting(m), caller(1, "I have a billing question, can I talk to a person?", m + 0.1),
    agent(2, "Of course. I'm connecting you to our front desk now.", m + 0.2, [150, 310, 430, 470]),
    caller(3, "Thank you.", m + 0.3) ] },
  { id: 7, minute: 188, length: 2.2, outcome: "COMPLETED", from: "+91 70080 31954", turns: (m) => [
    greeting(m), caller(1, "Where exactly is the clinic? Is there parking?", m + 0.1),
    agent(2, "We're at 14 Park Street, next to the metro exit. There's free parking behind the building.", m + 0.2, [140, 300, 410, 447], [src(2, 8.3)]),
    caller(3, "Do you take Star Health insurance?", m + 0.5),
    agent(4, "Yes, we accept Star Health. Bring your policy card and we'll handle the claim.", m + 0.6, [150, 320, 440, 476], [src(6, 6.2)]) ] },
  { id: 8, minute: 260, length: 1.7, outcome: "COMPLETED", from: "+91 91780 45120", turns: (m) => [
    greeting(m), caller(1, "Is there a female dentist available?", m + 0.1),
    agent(2, "I'm not sure about that one. I can take a message or connect you with our receptionist.", m + 0.2, [180, 390, 510, 553]),
    caller(3, "A message is fine.", m + 0.4) ] },
  { id: 9, minute: 300, length: 0.7, outcome: "ABANDONED", from: "+91 86580 77302", turns: (m) => [
    greeting(m), { ...base, index: 1, role: "AGENT", text: "Are you still there?", startedAt: at(m + 0.2) } ] },
  { id: 10, minute: 380, length: 2.0, outcome: "COMPLETED", from: "+91 98530 62781", turns: (m) => [
    greeting(m), caller(1, "Can I pay in monthly installments?", m + 0.1),
    agent(2, "Treatment plans over ₹10,000 can be split into two payments. For other options I can take a message.", m + 0.2, [150, 330, 450, 489], [src(10, 3.1)]),
    caller(3, "Okay.", m + 0.5) ] },
  { id: 11, minute: 410, length: 2.6, outcome: "COMPLETED", from: "+91 79780 11065", turns: (m) => [
    greeting(m), caller(1, "My tooth is really hurting, what should I do?", m + 0.1),
    agent(2, "Please come in today. We keep same-day emergency slots. Shall I book one for you?", m + 0.2, [140, 310, 420, 455], [src(7, 7.8), src(3, 1.9)]),
    caller(3, "Yes please, as soon as possible.", m + 0.4),
    agent(4, "You're booked for 5 pm today. Please arrive ten minutes early so we can see you straight away.", m + 0.5, [150, 320, 440, 481], [src(3, 4.4)]) ] },
  { id: 12, minute: 445, length: 1.2, outcome: "COMPLETED", from: "+91 93370 42288", turns: (m) => [
    greeting(m), caller(1, "Do you see kids? My son is five.", m + 0.1),
    agent(2, "Yes, we see children from age three. Our paediatric slots are on weekday afternoons.", m + 0.2, [150, 320, 430, 462], [src(9, 6.9)]) ] },
]

export const demoCalls: CallDetail[] = scripts
  .map(({ id, minute, length, outcome, from, turns }) => {
    const list = turns(minute)
    return {
      id, callSid: `CA-demo-${id}`, fromNumber: from, toNumber: "+91 674 555 0100",
      startedAt: at(minute), endedAt: at(minute + length), outcome,
      turnCount: list.length, bargeInCount: list.filter((turn) => turn.interrupted).length, turns: list,
    }
  })
  .reverse()

const agentLatencies = demoCalls.flatMap((call) => call.turns.filter((t) => t.role === "AGENT" && t.turnLatencyMs).map((t) => t.turnLatencyMs!))
const percentile = (values: number[], p: number) => [...values].sort((a, b) => a - b)[Math.min(values.length - 1, Math.floor(p * values.length))]
const median = (values: number[]) => (values.length ? percentile(values, 0.5) : null)

export const demoMetrics: Metrics = {
  totalCalls: demoCalls.length,
  outcomeBreakdown: demoCalls.reduce<Metrics["outcomeBreakdown"]>((acc, call) => ({ ...acc, [call.outcome!]: (acc[call.outcome!] ?? 0) + 1 }), {}),
  handoffRate: demoCalls.filter((call) => call.outcome === "HANDED_OFF").length / demoCalls.length,
  bargeInCount: demoCalls.reduce((sum, call) => sum + call.bargeInCount, 0),
  turnLatencyMs: { p50: percentile(agentLatencies, 0.5), p95: percentile(agentLatencies, 0.95), average: Math.round(agentLatencies.reduce((a, b) => a + b, 0) / agentLatencies.length) },
  knowledge: { entries: demoKnowledge.length, openGaps: demoGaps.length, coverageRate: 0.78 },
  latencyTrend: [...demoCalls].reverse().map((call) => ({
    callId: call.id, startedAt: call.startedAt,
    p50TurnLatencyMs: median(call.turns.filter((t) => t.role === "AGENT" && t.turnLatencyMs).map((t) => t.turnLatencyMs!)),
  })),
}

// A small stand-in for the backend's retrieval so "Test a question" works on the public demo: IDF-weighted term
// overlap plus a coverage rule, so one shared generic word ("offer") is not enough for a confident match.
const STOPWORDS = new Set(["a", "an", "the", "is", "are", "do", "does", "you", "your", "i", "my", "me", "can", "to", "of", "for", "in", "on", "what", "how", "there", "it", "and", "or", "we", "be", "have", "any", "about",
  // Generic request verbs say nothing about the topic, so they must not create a match on their own.
  "offer", "provide", "get", "need", "want", "know", "tell", "please"])
const stem = (word: string) => word.length > 4 ? word.replace(/(ing|es|s|ed)$/, "") : word
const tokens = (text: string) => text.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, " ").split(/\s+/).filter((t) => t && !STOPWORDS.has(t)).map(stem)

const documents = demoKnowledge.map((entry) => ({ entry, terms: new Set(tokens(`${entry.question} ${entry.answer} ${entry.tags.join(" ")}`)), questionTerms: new Set(tokens(entry.question)) }))
const idf = (term: string) => Math.log(1 + documents.length / Math.max(1, documents.filter((doc) => doc.terms.has(term)).length))

export function demoSearch(query: string): SearchResult {
  const words = [...new Set(tokens(query))]
  const matches = documents
    .map(({ entry, terms, questionTerms }) => {
      const matched = words.filter((word) => terms.has(word))
      const score = Math.round(matched.reduce((sum, word) => sum + idf(word) * (questionTerms.has(word) ? 2 : 1), 0) * 10) / 10
      const coverage = words.length ? matched.length / words.length : 0
      return { entry, score, confident: score >= 2.5 && coverage >= 1 / 3 }
    })
    .filter((match) => match.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 5)
  return { query, confident: matches.some((m) => m.confident), matches }
}
