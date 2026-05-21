"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { BookOpenTextIcon, LayoutDashboardIcon, PhoneCallIcon, PhoneIcon } from "lucide-react"
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarHeader,
  SidebarMenu, SidebarMenuBadge, SidebarMenuButton, SidebarMenuItem,
} from "@/components/ui/sidebar"

const NAV = [
  { href: "/", label: "Overview", icon: LayoutDashboardIcon },
  { href: "/calls", label: "Calls", icon: PhoneCallIcon },
  { href: "/knowledge", label: "Knowledge base", icon: BookOpenTextIcon },
] as const

export function AppSidebar({ businessName, openGaps, demo }: { businessName: string | null; openGaps: number; demo: boolean }) {
  const pathname = usePathname()
  const isActive = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href))

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" render={<Link href="/" />} tooltip="CallDesk">
              <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-(--brand) text-white">
                <PhoneIcon className="size-4" aria-hidden />
              </span>
              <span className="flex flex-col leading-tight">
                <span className="font-heading font-medium">CallDesk</span>
                <span className="text-xs text-muted-foreground">AI phone receptionist</span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              {NAV.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton render={<Link href={item.href} />} isActive={isActive(item.href)} tooltip={item.label}>
                    <item.icon aria-hidden />
                    <span>{item.label}</span>
                  </SidebarMenuButton>
                  {item.href === "/knowledge" && openGaps > 0 && (
                    <SidebarMenuBadge aria-label={`${openGaps} open knowledge gaps`}>{openGaps}</SidebarMenuBadge>
                  )}
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <div className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-xs group-data-[collapsible=icon]:hidden">
          <span aria-hidden className={`size-2 shrink-0 rounded-full ${demo ? "bg-amber-500" : "bg-emerald-500"}`} />
          <span className="min-w-0">
            <span className="block truncate font-medium">{businessName ?? "No business loaded"}</span>
            <span className="text-muted-foreground">{demo ? "Demo data" : "Agent online"}</span>
          </span>
        </div>
      </SidebarFooter>
    </Sidebar>
  )
}
