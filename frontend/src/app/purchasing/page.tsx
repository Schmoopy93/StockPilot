import type { Metadata } from "next";
import PurchasingPage from "@/components/purchasing-page";

export const metadata: Metadata = {
  title: "Preporuke za nabavku | StockPilot",
  description: "Planirajte nabavku prema potrošnji i stanju zaliha.",
};

export default function PurchasingRoute() {
  return <PurchasingPage />;
}
