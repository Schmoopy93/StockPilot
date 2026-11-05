export type Product = {
  id: string;
  sku: string;
  name: string;
  category: string | null;
  unit: string;
  minimumStock: number;
  leadTimeDays: number;
  unitCost: number;
  onHand: number;
};

export type Recommendation = {
  productId: string;
  sku: string;
  productName: string;
  unit: string;
  onHand: number;
  demandLast30Days: number;
  averageDailyDemand: number;
  leadTimeDays: number;
  safetyStockDays: number;
  recommendedQuantity: number;
  lowConfidence: boolean;
  estimatedCost: number;
};

export type Movement = {
  id: string;
  productId: string;
  productName: string;
  type: "INBOUND" | "OUTBOUND" | "ADJUSTMENT";
  quantity: number;
  occurredAt: string;
  note: string | null;
};

export type Dashboard = {
  activeProducts: number;
  unitsOnHand: number;
  productsBelowMinimum: number;
  productsToReorder: number;
  estimatedReorderCost: number;
  lowStock: Product[];
  recommendations: Recommendation[];
  recentMovements: Movement[];
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

export async function getDashboard(): Promise<Dashboard> {
  const response = await fetch(`${API_BASE_URL}/dashboard`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`StockPilot API returned ${response.status}`);
  }
  return response.json() as Promise<Dashboard>;
}

export async function getProducts(): Promise<Product[]> {
  const response = await fetch(`${API_BASE_URL}/products`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`StockPilot API returned ${response.status}`);
  }
  return response.json() as Promise<Product[]>;
}

export async function createProduct(product: Omit<Product, "id" | "onHand">): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/products`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(product),
  });
  if (!response.ok) {
    throw new Error(response.status === 409 ? "SKU već postoji." : `Greška pri čuvanju proizvoda (${response.status}).`);
  }
}

export async function recordMovement(movement: {
  productId: string;
  type: "INBOUND" | "OUTBOUND" | "ADJUSTMENT";
  quantity: number;
  note: string;
}): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/stock-movements`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(movement),
  });
  if (!response.ok) {
    const message = response.status === 409
      ? "Nema dovoljno zaliha za ovo kretanje."
      : `Greška pri evidentiranju kretanja (${response.status}).`;
    throw new Error(message);
  }
}
