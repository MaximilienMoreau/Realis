import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import ThemeToggle from "@/components/ThemeToggle";

// Auto-hébergée par Next.js au build (aucune requête réseau au runtime) :
// compatible avec la CSP "default-src 'self'" qui bloquerait fonts.googleapis.com.
const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });

export const metadata: Metadata = {
  title: "Realis : Certification du réel",
  description:
    "Certifiez l'authenticité de vos captures par horodatage cryptographique RFC 3161. Preuve d'intégrité et d'antériorité.",
  manifest: "/manifest.json",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "Realis",
  },
};

export const viewport: Viewport = {
  themeColor: "#2d52c4",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr" suppressHydrationWarning className={inter.variable}>
      <head>
        <link rel="apple-touch-icon" href="/icons/icon-192.png" />
        {/* Anti-flash : applique le thème avant le premier rendu */}
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{if(localStorage.getItem('realis-theme')==='dark'){document.documentElement.classList.add('dark')}}catch(e){}})()`,
          }}
        />
      </head>
      <body>
        <ThemeToggle />
        {children}
      </body>
    </html>
  );
}
