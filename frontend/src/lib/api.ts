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
  onOrder: number;
};

export type Recommendation = {
  productId: string;
  sku: string;
  productName: string;
  unit: string;
  onHand: number;
  onOrder: number;
  demandLast30Days: number;
  averageDailyDemand: number;
  minimumStock: number;
  targetStock: number;
  leadTimeDays: number;
  safetyStockDays: number;
  preferredSupplierId: string | null;
  preferredSupplierName: string | null;
  supplierSku: string | null;
  minimumOrderQuantity: number;
  supplierUnitCost: number;
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
  purchaseOrderId: string | null;
  purchaseOrderNumber: string | null;
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

export type AuthUser = {
  id: string;
  email: string;
  name: string;
  organizationId: string;
  organizationName: string;
  role: "OWNER" | "ADMIN" | "STAFF" | "READ_ONLY";
  emailVerifiedAt: string;
};

export type Supplier = {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
};

export type SupplierPerformance = {
  totalOrders: number;
  openOrders: number;
  onTimeDeliveries: number;
  lateDeliveries: number;
  recentOrders: Array<{
    id: string;
    orderNumber: string;
    status: PurchaseOrderStatus;
    createdAt: string;
    expectedDeliveryDate: string | null;
    lastReceivedAt: string | null;
    total: number;
  }>;
  recentPrices: Array<{
    productId: string;
    productName: string;
    sku: string;
    unitCost: number;
    orderNumber: string;
    orderedAt: string;
  }>;
};

export type SupplierProduct = {
  id: string;
  supplierId: string;
  supplierName: string;
  productId: string;
  productName: string;
  productSku: string;
  supplierSku: string | null;
  unitCost: number;
  minimumOrderQuantity: number;
  leadTimeDays: number | null;
  preferredSupplier: boolean;
};

export type PurchaseOrderStatus =
  | "DRAFT"
  | "ORDERED"
  | "PARTIALLY_RECEIVED"
  | "RECEIVED"
  | "CANCELLED";

export type PurchaseOrderLine = {
  productId: string;
  productName: string;
  sku: string;
  supplierSku: string | null;
  unit: string;
  orderedQuantity: number;
  receivedQuantity: number;
  unitCost: number;
  lineTotal: number;
};

export type PurchaseOrder = {
  id: string;
  orderNumber: string;
  supplierId: string;
  supplierName: string;
  status: PurchaseOrderStatus;
  expectedDeliveryDate: string | null;
  notes: string | null;
  createdAt: string;
  orderedAt: string | null;
  lastReceivedAt: string | null;
  lines: PurchaseOrderLine[];
  total: number;
};

export type PurchaseOrderSummary = {
  openCount: number;
  openValue: number;
};

export type CurrencyStatus = {
  baseCurrency: "RSD";
  eurToRsd: number | null;
  rateDate: string | null;
  rateUpdatedAt: string | null;
  legacyPricesConverted: boolean;
  legacyPricesConvertedAt: string | null;
  conversionStartedDate: string;
  conversionRate: number | null;
  conversionRateDate: string | null;
  rateProvider: string | null;
  rateProviderUrl: string | null;
};

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type MovementHistoryFilters = {
  productId?: string;
  type?: Movement["type"];
  from?: string;
  to?: string;
  page?: number;
  size?: number;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api").replace(/\/+$/, "");

type CsrfResponse = {
  headerName: string;
  token: string;
};

type MessageResponse = {
  message: string;
};

async function csrfHeaders(): Promise<Record<string, string>> {
  const response = await fetch(`${API_BASE_URL}/auth/csrf`, {
    credentials: "include",
    cache: "no-store",
  });
  if (!response.ok) {
    throw new ApiError(`Nije moguće uspostaviti bezbednu sesiju (${response.status}).`, response.status);
  }
  const csrf = (await response.json()) as CsrfResponse;
  return { [csrf.headerName]: csrf.token };
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const csrf = await csrfHeaders();
    Object.entries(csrf).forEach(([name, value]) => headers.set(name, value));
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    method,
    headers,
    credentials: "include",
    cache: "no-store",
  });
  if (!response.ok) {
    let message = `Zahtev nije uspeo (${response.status}).`;
    try {
      const body = (await response.json()) as {
        detail?: string;
        message?: string;
        title?: string;
      };
      message = body.detail ?? body.message ?? body.title ?? message;
    } catch {
      if (response.status === 401) {
        message = "Sesija je istekla. Prijavite se ponovo.";
      }
    }
    const springMessage = message.match(/^\d{3}\s+[A-Z_]+\s+"([\s\S]*)"$/);
    if (springMessage) message = springMessage[1];
    const retryAfter = Number(response.headers.get("Retry-After"));
    throw new ApiError(
      message,
      response.status,
      Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function getDashboard(): Promise<Dashboard> {
  return request<Dashboard>("/dashboard");
}

export async function getProducts(): Promise<Product[]> {
  return request<Product[]>("/products");
}

export async function getCurrencyStatus(): Promise<CurrencyStatus> {
  return request<CurrencyStatus>("/currency");
}

export async function refreshCurrencyStatus(): Promise<CurrencyStatus> {
  return request<CurrencyStatus>("/currency/refresh", { method: "POST" });
}

export async function getMovementHistory(filters: MovementHistoryFilters = {}): Promise<PageResponse<Movement>> {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request<PageResponse<Movement>>(`/stock-movements?${query.toString()}`);
}

export async function exportMovementHistoryCsv(filters: MovementHistoryFilters = {}): Promise<void> {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  const response = await fetch(`${API_BASE_URL}/stock-movements/export?${query.toString()}`, {
    credentials: "include",
    cache: "no-store",
  });
  if (!response.ok) {
    let message = `Izvoz kretanja nije uspeo (${response.status}).`;
    try {
      const body = await response.json() as { detail?: string; message?: string; title?: string };
      message = body.detail ?? body.message ?? body.title ?? message;
    } catch {
      if (response.status === 401) message = "Sesija je istekla. Prijavite se ponovo.";
    }
    throw new ApiError(message, response.status);
  }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = `stockpilot-kretanja-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function getSuppliers(): Promise<Supplier[]> {
  return request<Supplier[]>("/suppliers");
}

export async function getSupplierPerformance(supplierId: string): Promise<SupplierPerformance> {
  return request<SupplierPerformance>(`/suppliers/${supplierId}/performance`);
}

export async function getSupplierProducts(): Promise<SupplierProduct[]> {
  return request<SupplierProduct[]>("/supplier-products");
}

export async function saveSupplierProduct(
  supplierId: string,
  productId: string,
  input: {
    supplierSku: string;
    unitCost: number;
    minimumOrderQuantity: number;
    leadTimeDays: number | null;
    preferredSupplier: boolean;
  },
): Promise<SupplierProduct> {
  return request<SupplierProduct>(`/suppliers/${supplierId}/products/${productId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export async function deleteSupplierProduct(supplierId: string, productId: string): Promise<void> {
  await request<void>(`/suppliers/${supplierId}/products/${productId}`, { method: "DELETE" });
}

export async function saveSupplier(input: {
  name: string;
  email: string;
  phone: string;
  notes: string;
  active: boolean;
}, supplierId?: string): Promise<Supplier> {
  return request<Supplier>(supplierId ? `/suppliers/${supplierId}` : "/suppliers", {
    method: supplierId ? "PATCH" : "POST",
    body: JSON.stringify(input),
  });
}

export async function getPurchaseOrders(filters: {
  supplierId?: string;
  status?: PurchaseOrderStatus;
  from?: string;
  to?: string;
  overdue?: boolean;
  page?: number;
  size?: number;
} = {}): Promise<PageResponse<PurchaseOrder>> {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request<PageResponse<PurchaseOrder>>(`/purchase-orders?${query.toString()}`);
}

export async function getPurchaseOrderSummary(): Promise<PurchaseOrderSummary> {
  return request<PurchaseOrderSummary>("/purchase-orders/summary");
}

export async function createPurchaseOrder(input: {
  supplierId: string;
  expectedDeliveryDate: string | null;
  notes: string;
  lines: Array<{ productId: string; quantity: number; unitCost: number | null }>;
}): Promise<PurchaseOrder> {
  return request<PurchaseOrder>("/purchase-orders", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function createPurchaseOrders(input: Array<{
  supplierId: string;
  expectedDeliveryDate: string | null;
  notes: string;
  lines: Array<{ productId: string; quantity: number; unitCost: number | null }>;
}>): Promise<PurchaseOrder[]> {
  return request<PurchaseOrder[]>("/purchase-orders/batch", {
    method: "POST",
    body: JSON.stringify({ orders: input }),
  });
}

export async function dispatchPurchaseOrder(orderId: string): Promise<PurchaseOrder> {
  return request<PurchaseOrder>(`/purchase-orders/${orderId}/dispatch`, { method: "POST" });
}

export async function receivePurchaseOrder(
  orderId: string,
  lines: Array<{ productId: string; quantity: number }>,
): Promise<PurchaseOrder> {
  return request<PurchaseOrder>(`/purchase-orders/${orderId}/receive`, {
    method: "POST",
    body: JSON.stringify({ lines }),
  });
}

export async function cancelPurchaseOrder(orderId: string): Promise<PurchaseOrder> {
  return request<PurchaseOrder>(`/purchase-orders/${orderId}/cancel`, { method: "POST" });
}

export async function createProduct(product: Omit<Product, "id" | "onHand" | "onOrder">): Promise<Product> {
  return request<Product>("/products", { method: "POST", body: JSON.stringify(product) });
}

export async function updateProduct(
  productId: string,
  product: Omit<Product, "id" | "onHand" | "onOrder">,
): Promise<Product> {
  return request<Product>(`/products/${productId}`, { method: "PUT", body: JSON.stringify(product) });
}

export async function recordMovement(movement: {
  productId: string;
  type: "INBOUND" | "OUTBOUND" | "ADJUSTMENT";
  quantity: number;
  note: string;
}): Promise<void> {
  try {
    await request<void>("/stock-movements", { method: "POST", body: JSON.stringify(movement) });
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      throw new ApiError("Nema dovoljno zaliha za ovo kretanje.", error.status);
    }
    throw error;
  }
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  try {
    return (await request<AuthUser | undefined>("/auth/me")) ?? null;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export async function login(email: string, password: string): Promise<AuthUser> {
  return request<AuthUser>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function register(input: {
  email: string;
  name: string;
  organizationName: string;
  password: string;
}): Promise<MessageResponse> {
  return request<MessageResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function requestPasswordReset(email: string): Promise<MessageResponse> {
  return request<MessageResponse>("/auth/forgot-password", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export async function verifyEmail(token: string): Promise<MessageResponse> {
  return request<MessageResponse>("/auth/verify-email", {
    method: "POST",
    body: JSON.stringify({ token }),
  });
}

export async function resendVerification(email: string): Promise<MessageResponse> {
  return request<MessageResponse>("/auth/resend-verification", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export async function resetPassword(token: string, newPassword: string): Promise<MessageResponse> {
  return request<MessageResponse>("/auth/reset-password", {
    method: "POST",
    body: JSON.stringify({ token, newPassword }),
  });
}

export async function logout(): Promise<void> {
  await request<void>("/auth/logout", { method: "POST" });
}
