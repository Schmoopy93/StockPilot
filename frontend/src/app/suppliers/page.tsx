import type { Metadata } from "next";
import SuppliersPage from "@/components/suppliers-page";

export const metadata: Metadata = {
  title: "Dobavljači | StockPilot",
  description: "Upravljajte dobavljačima svoje organizacije.",
};

export default function SuppliersRoute() {
  return <SuppliersPage />;
}
