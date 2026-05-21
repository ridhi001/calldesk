import type { Metadata } from "next"
import { Geist, Geist_Mono } from "next/font/google"
import { ThemeProvider } from "next-themes"
import { AppSidebar } from "@/components/app-sidebar"
import { ThemeToggle } from "@/components/theme-toggle"
import { Badge } from "@/components/ui/badge"
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar"
import { Toaster } from "@/components/ui/sonner"
import { TooltipProvider } from "@/components/ui/tooltip"
import { DEMO_MODE, getBusiness, getMetrics } from "@/lib/api"
import "./globals.css"

const geistSans = Geist({ variable: "--font-sans", subsets: ["latin"] })
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] })

export const metadata: Metadata = {
  title: { default: "CallDesk", template: "%s · CallDesk" },
  description: "AI phone receptionist: live calls, transcripts, turn latency and the knowledge base behind every answer.",
}

export default async function RootLayout({ children }: LayoutProps<"/">) {
  // The shell still renders when the backend is down; pages show their own offline state.
  const [business, metrics] = await Promise.all([getBusiness().catch(() => null), getMetrics().catch(() => null)])

  return (
    <html lang="en" suppressHydrationWarning className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}>
      <body className="min-h-full bg-background text-foreground">
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <TooltipProvider>
            <SidebarProvider>
              <AppSidebar businessName={business?.name ?? null} openGaps={metrics?.knowledge?.openGaps ?? 0} demo={DEMO_MODE} />
              <SidebarInset>
                <header className="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
                  <SidebarTrigger className="-ml-1" />
                  {DEMO_MODE && <Badge variant="secondary">Demo data</Badge>}
                  <div className="ml-auto">
                    <ThemeToggle />
                  </div>
                </header>
                <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-8 px-4 py-8 md:px-8">{children}</main>
              </SidebarInset>
            </SidebarProvider>
            <Toaster />
          </TooltipProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
