import { ServerOffIcon } from "lucide-react"
import { API_URL } from "@/lib/api"

export function BackendOffline() {
  return (
    <section className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-16 text-center">
      <ServerOffIcon className="size-6 text-muted-foreground" aria-hidden />
      <h2 className="font-heading text-base font-medium">Backend not reachable</h2>
      <p className="max-w-md text-sm text-muted-foreground">
        Start the Spring Boot backend on <code className="font-mono">{API_URL}</code>, or run the dashboard with{" "}
        <code className="font-mono">NEXT_PUBLIC_DEMO_MODE=true</code> to explore it with sample data.
      </p>
    </section>
  )
}
