import type { Metadata } from "next";
import ProductsPage from "@/components/products-page";

export const metadata: Metadata = {
  title: "Proizvodi | StockPilot",
  description: "Pregledajte katalog proizvoda i stanje zaliha.",
};

export default function ProductsRoute() {
  return <ProductsPage />;
}
