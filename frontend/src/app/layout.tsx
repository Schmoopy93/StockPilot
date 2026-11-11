import type { Metadata } from "next";
import { CurrencyProvider } from "@/components/currency-context";
import "./globals.css";

export const metadata: Metadata = {
  title: "StockPilot | Pametna nabavka",
  description: "Pregled zaliha i preporuke nabavke za StockPilot.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="sr">
      <body><CurrencyProvider>{children}</CurrencyProvider></body>
    </html>
  );
}
