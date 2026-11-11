import type { Metadata } from "next";
import PurchaseOrdersPage from "@/components/purchase-orders-page";

export const metadata: Metadata = {
  title: "Porudžbine | StockPilot",
  description: "Kreirajte porudžbine i evidentirajte prijem robe.",
};

export default function PurchaseOrdersRoute() {
  return <PurchaseOrdersPage />;
}
