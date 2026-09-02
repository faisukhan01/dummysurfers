import type { Metadata } from "next";
import { Geist, Geist_Mono, Fredoka } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

// Chunky rounded comic font for the Subway-Surfers-style dashboard (v1.2.0).
const fredoka = Fredoka({
  weight: ["600", "700"],
  subsets: ["latin"],
  variable: "--font-fredoka",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Dummy Surfers by Faisal Khan — Kotlin + LibGDX Endless Runner",
  description: "Premium Android endless runner built with Kotlin + LibGDX — Dummy Surfers by Faisal Khan v5.0.0 — true-3D Subway-Surfers-style endless runner, now fully playable on touch. Procedural art & audio, CI builds APK/AAB on GitHub push.",
  keywords: ["Dummy Surfers", "Faisal Khan", "FSK", "Kotlin", "LibGDX", "endless runner", "Android game"],
  authors: [{ name: "Faisal Khan" }],
  icons: {
    icon: "https://z-cdn.chatglm.cn/z-ai/static/logo.svg",
  },
  openGraph: {
    title: "Dummy Surfers by Faisal Khan — Dummy Surfers v5.0.0",
    description: "Bright daylight SS-style dashboard: cyan sky, periwinkle panels, gold chunky buttons. Kotlin + LibGDX endless runner.",
    url: "https://chat.z.ai",
    siteName: "Dummy Surfers",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "Dummy Surfers by Faisal Khan — Dummy Surfers v5.0.0",
    description: "Bright daylight SS-style dashboard for the Kotlin + LibGDX endless runner.",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} ${fredoka.variable} antialiased bg-background text-foreground`}
      >
        {children}
        <Toaster />
      </body>
    </html>
  );
}
