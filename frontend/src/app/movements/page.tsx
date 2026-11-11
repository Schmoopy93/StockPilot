import type { Metadata } from "next";
import MovementHistoryPage from "@/components/movement-history-page";

export const metadata: Metadata = {
  title: "Kretanja robe | StockPilot",
  description: "Pretražite istoriju kretanja zaliha.",
};

export default function MovementsRoute() {
  return <MovementHistoryPage />;
}
