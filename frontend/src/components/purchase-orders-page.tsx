"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertTriangle, ClipboardList, Plus, TrendingUp, X } from "lucide-react";
import { ApiError, cancelPurchaseOrder, createPurchaseOrder, dispatchPurchaseOrder, getCurrentUser, getProducts, getPurchaseOrderSummary, getPurchaseOrders, getSupplierProducts, getSuppliers, receivePurchaseOrder, type AuthUser, type PageResponse, type Product, type PurchaseOrder, type PurchaseOrderStatus, type PurchaseOrderSummary, type Supplier, type SupplierProduct } from "@/lib/api";
import { useCurrency } from "@/components/currency-context";
import WorkspaceShell from "@/components/workspace-shell";

const quantityFormat = new Intl.NumberFormat("sr-RS");

const statusLabels: Record<PurchaseOrderStatus, string> = {
  DRAFT: "Nacrt",
  ORDERED: "Poručeno",
  PARTIALLY_RECEIVED: "Delimično primljeno",
  RECEIVED: "Primljeno",
  CANCELLED: "Otkazano",
};

function localDateString(date: Date) {
  const parts = new Intl.DateTimeFormat("en", {
    timeZone: "Europe/Belgrade",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  return year && month && day ? `${year}-${month}-${day}` : "";
}

function isOverdue(order: PurchaseOrder, today: string) {
  return Boolean(today && order.expectedDeliveryDate && order.expectedDeliveryDate < today
    && (order.status === "ORDERED" || order.status === "PARTIALLY_RECEIVED"));
}

function overdueDays(order: PurchaseOrder, today: string) {
  if (!order.expectedDeliveryDate || !today) return 0;
  const due = Date.parse(`${order.expectedDeliveryDate}T00:00:00Z`);
  const current = Date.parse(`${today}T00:00:00Z`);
  return Math.floor((current - due) / 86_400_000);
}

type DraftLine = {
  product: Product;
  quantity: number;
  unitCost: number | null;
  supplierSku: string | null;
  minimumOrderQuantity: number;
};

export default function PurchaseOrdersPage() {
  const router = useRouter();
  const { displayCurrency, formatMoney, pricesReady } = useCurrency();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [supplierProducts, setSupplierProducts] = useState<SupplierProduct[]>([]);
  const [orders, setOrders] = useState<PageResponse<PurchaseOrder> | null>(null);
  const [orderSummary, setOrderSummary] = useState<PurchaseOrderSummary | null>(null);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "OVERDUE" | PurchaseOrderStatus>("ALL");
  const [supplierFilter, setSupplierFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [page, setPage] = useState(0);
  const [today, setToday] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState("");
  const [orderNotes, setOrderNotes] = useState("");
  const [selectedProductId, setSelectedProductId] = useState("");
  const [selectedQuantity, setSelectedQuantity] = useState(1);
  const [lines, setLines] = useState<DraftLine[]>([]);
  const [creating, setCreating] = useState(false);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const canManageOrders = !user || user.role === "OWNER" || user.role === "ADMIN";
  const canReceiveOrders = !user || user.role !== "READ_ONLY";
  const recommendationDraftKey = "stockpilot-recommended-order";
  const orderFilters = useMemo(() => ({
    supplierId: supplierFilter || undefined,
    status: statusFilter !== "ALL" && statusFilter !== "OVERDUE" ? statusFilter : undefined,
    overdue: statusFilter === "OVERDUE" || undefined,
    from: dateFrom || undefined,
    to: dateTo || undefined,
    page,
    size: 20,
  }), [dateFrom, dateTo, page, statusFilter, supplierFilter]);

  useEffect(() => {
    const updateToday = () => setToday(localDateString(new Date()));
    updateToday();
    const timer = window.setInterval(updateToday, 60_000);
    return () => window.clearInterval(timer);
  }, []);

  async function load() {
    setError(null);
    try {
      const [currentUser, supplierList, productList, orderList, supplierProductList, summary] = await Promise.all([
        getCurrentUser(), getSuppliers(), getProducts(), getPurchaseOrders(orderFilters),
        getSupplierProducts(), getPurchaseOrderSummary(),
      ]);
      setUser(currentUser);
      setSuppliers(supplierList);
      setProducts(productList);
      setOrders(orderList);
      setOrderSummary(summary);
      setSupplierProducts(supplierProductList);
      if (!supplierId) setSupplierId(supplierList.find((supplier) => supplier.active)?.id ?? "");
    } catch (loadError) {
      if (loadError instanceof ApiError && loadError.status === 401) {
        router.replace("/login?next=%2Forders&status=login-required");
      } else {
        setError("Porudžbine trenutno nisu dostupne. Pokušajte ponovo.");
      }
    }
  }

  useEffect(() => {
    let active = true;
    getPurchaseOrders(orderFilters)
      .then((result) => {
        if (active) {
          setOrders(result);
          setError(null);
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2Forders&status=login-required");
        } else {
          setError("Porudžbine trenutno nisu dostupne. Pokušajte ponovo.");
        }
      });
    return () => { active = false; };
  }, [orderFilters, router]);

  useEffect(() => {
    let active = true;
    Promise.all([getCurrentUser(), getSuppliers(), getProducts(), getSupplierProducts(), getPurchaseOrderSummary()])
      .then(([currentUser, supplierList, productList, supplierProductList, summary]) => {
        if (active) {
          setUser(currentUser);
          setSuppliers(supplierList);
          setProducts(productList);
          setOrderSummary(summary);
          setSupplierProducts(supplierProductList);
          setSupplierId((current) => current || supplierList.find((supplier) => supplier.active)?.id || "");
          try {
            const stored = window.sessionStorage.getItem(recommendationDraftKey);
            if (stored) {
              const parsed: unknown = JSON.parse(stored);
              if (!Array.isArray(parsed) || !parsed.every((item) =>
                item && typeof item.productId === "string" && Number.isInteger(item.quantity) && item.quantity > 0
                && (item.preferredSupplierId === undefined || item.preferredSupplierId === null
                  || typeof item.preferredSupplierId === "string"))) {
                setError("Izabrane preporuke nisu ispravne. Izaberite ih ponovo sa stranice nabavke.");
              } else {
                const commonPreferredIds = [...new Set(parsed
                  .map((item: { preferredSupplierId?: string | null }) => item.preferredSupplierId)
                  .filter((id: string | null | undefined): id is string => Boolean(id)))];
                const commonPreferredSupplierId = commonPreferredIds.length === 1
                  && parsed.every((item: { preferredSupplierId?: string | null }) =>
                    item.preferredSupplierId === commonPreferredIds[0])
                  && supplierList.some((supplier) =>
                    supplier.id === commonPreferredIds[0] && supplier.active)
                  ? commonPreferredIds[0] : undefined;
                const chosenSupplierId = commonPreferredSupplierId
                  ?? supplierList.find((supplier) => supplier.active)?.id;
                if (chosenSupplierId) setSupplierId(chosenSupplierId);
                const importedLines: DraftLine[] = parsed.flatMap((item: {
                  productId: string;
                  quantity: number;
                  preferredSupplierId?: string | null;
                }) => {
                  const product = productList.find((candidate) => candidate.id === item.productId);
                  if (!product) return [];
                  const catalogItem = supplierProductList.find((entry) =>
                    entry.supplierId === chosenSupplierId && entry.productId === product.id);
                  return [{
                    product,
                    quantity: Math.max(item.quantity, catalogItem?.minimumOrderQuantity ?? 1),
                    unitCost: catalogItem?.unitCost ?? null,
                    supplierSku: catalogItem?.supplierSku ?? null,
                    minimumOrderQuantity: catalogItem?.minimumOrderQuantity ?? 1,
                  }];
                });
                if (importedLines.length !== parsed.length) {
                  setError("Neke izabrane preporuke više nisu u katalogu. Proverite nacrt pre slanja.");
                }
                setLines(importedLines);
              }
              window.sessionStorage.removeItem(recommendationDraftKey);
            }
          } catch {
            setError("Nije moguće učitati izabrane preporuke. Pokušajte ponovo sa stranice nabavke.");
          }
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2Forders&status=login-required");
        } else {
          setError("Porudžbine trenutno nisu dostupne. Pokušajte ponovo.");
        }
      });
    return () => { active = false; };
  }, [router]);

  const activeSuppliers = useMemo(() => suppliers.filter((supplier) => supplier.active), [suppliers]);
  const visibleOrders = orders?.content ?? [];
  const openCount = orderSummary?.openCount ?? 0;
  const openValue = orderSummary?.openValue ?? 0;

  function addLine() {
    const product = products.find((item) => item.id === selectedProductId);
    if (!product || selectedQuantity < 1) return;
    const catalogItem = supplierProducts.find((item) =>
      item.supplierId === supplierId && item.productId === product.id);
    if (catalogItem && selectedQuantity < catalogItem.minimumOrderQuantity) {
      setError(`Minimalna količina za ${product.name} kod ovog dobavljača je ${catalogItem.minimumOrderQuantity}.`);
      return;
    }
    setError(null);
    setLines((current) => {
      const existing = current.find((line) => line.product.id === product.id);
      if (existing) {
        return current.map((line) => line.product.id === product.id
          ? { ...line, quantity: line.quantity + selectedQuantity }
          : line);
      }
      return [...current, {
        product,
        quantity: selectedQuantity,
        unitCost: catalogItem?.unitCost ?? null,
        supplierSku: catalogItem?.supplierSku ?? null,
        minimumOrderQuantity: catalogItem?.minimumOrderQuantity ?? 1,
      }];
    });
    setSelectedProductId("");
    setSelectedQuantity(1);
  }

  function updateLine(productId: string, patch: Partial<Pick<DraftLine, "quantity" | "unitCost">>) {
    setLines((current) => current.map((line) => line.product.id === productId ? { ...line, ...patch } : line));
  }

  async function createOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!supplierId || lines.length === 0) {
      setError("Izaberite aktivnog dobavljača i dodajte bar jedan proizvod.");
      return;
    }
    const belowMinimum = lines.find((line) => line.quantity < line.minimumOrderQuantity);
    if (belowMinimum) {
      setError(`Minimalna količina za ${belowMinimum.product.name} je ${belowMinimum.minimumOrderQuantity}.`);
      return;
    }
    setCreating(true);
    setError(null);
    try {
      const created = await createPurchaseOrder({
        supplierId,
        expectedDeliveryDate: expectedDeliveryDate || null,
        notes: orderNotes.trim(),
        lines: lines.map((line) => ({
          productId: line.product.id,
          quantity: line.quantity,
          unitCost: line.unitCost,
        })),
      });
      setLines([]);
      setOrderNotes("");
      setExpectedDeliveryDate("");
      setNotice(`Nacrt ${created.orderNumber} je sačuvan. Pregledajte ga pa označite kao poručen.`);
      await load();
    } catch (createError) {
      if (createError instanceof ApiError && createError.status === 401) {
        router.replace("/login?next=%2Forders&status=session-expired");
      } else if (createError instanceof ApiError && createError.status === 403) {
        setError("Vaša uloga nema dozvolu za kreiranje porudžbine.");
      } else {
        setError(createError instanceof Error ? createError.message : "Porudžbina nije sačuvana.");
      }
    } finally {
      setCreating(false);
    }
  }

  async function runOrderAction(orderId: string, action: "dispatch" | "cancel") {
    setBusyOrderId(orderId);
    setError(null);
    try {
      const result = action === "dispatch"
        ? await dispatchPurchaseOrder(orderId)
        : await cancelPurchaseOrder(orderId);
      setNotice(action === "dispatch" ? `${result.orderNumber} je označena kao poručena.` : `${result.orderNumber} je otkazana.`);
      await load();
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : "Promena porudžbine nije uspela.");
    } finally {
      setBusyOrderId(null);
    }
  }

  async function receiveOrder(event: FormEvent<HTMLFormElement>, order: PurchaseOrder) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const received = order.lines.map((line) => ({
      productId: line.productId,
      quantity: Number(values.get(line.productId) || 0),
    })).filter((line) => line.quantity > 0);
    if (received.length === 0) {
      setError("Unesite količinu za prijem bar jednog artikla.");
      return;
    }
    setBusyOrderId(order.id);
    setError(null);
    try {
      const updated = await receivePurchaseOrder(order.id, received);
      setNotice(`${updated.orderNumber}: prijem je evidentiran i stanje zaliha ažurirano.`);
      await load();
    } catch (receiveError) {
      setError(receiveError instanceof Error ? receiveError.message : "Prijem robe nije uspeo.");
    } finally {
      setBusyOrderId(null);
    }
  }

  return (
    <WorkspaceShell user={user} active="orders">
      <div className="content workspace-content">
        <div className="page-heading">
          <div>
            <div className="eyebrow">NABAVKA I PRIJEM</div>
            <h1>Porudžbine</h1>
            <p>Kreirajte nacrt, evidentirajte slanje dobavljaču i knjižite delimičan ili potpun prijem.</p>
          </div>
        </div>

        {notice && <div className="success-banner" role="status">{notice}<button type="button" aria-label="Zatvori poruku" onClick={() => setNotice(null)}><X size={16} /></button></div>}
        {error && <div className="error-banner" role="alert">{error}<button type="button" onClick={() => void load()}>Osveži podatke</button></div>}

        <section className="purchasing-summary order-summary" aria-label="Pregled porudžbina">
          <article className="purchasing-summary-card"><span className="purchasing-summary-icon"><ClipboardList size={20} /></span><span><small>Otvorene porudžbine</small><strong>{orderSummary ? quantityFormat.format(openCount) : "—"}</strong></span></article>
          <article className="purchasing-summary-card"><span className="purchasing-summary-icon purchasing-summary-cost"><TrendingUp size={20} /></span><span><small>Preostala vrednost robe</small><strong>{orderSummary ? formatMoney(openValue) : "—"}</strong></span></article>
          <p>Prijem robe automatski uvećava stanje na lageru. Vrednosti su bez poreza i dostave.</p>
        </section>

        {canManageOrders && activeSuppliers.length > 0 && (
          <section className="section-card order-create-card" aria-labelledby="create-order-title">
            <div className="compact-heading"><div><h2 id="create-order-title">Nova porudžbina</h2><p>Počinje kao nacrt — stanje se menja tek kada evidentirate prijem.</p></div></div>
            <form className="order-create-form" onSubmit={createOrder}>
              {!pricesReady && <p className="currency-hint">Kreiranje porudžbina biće dostupno nakon konverzije postojećih cena po dostupnom kursu EUR/RSD.</p>}
              <fieldset className="currency-gated-fields" disabled={!pricesReady}>
              <div className="form-grid">
                <label>Dobavljač<select value={supplierId} onChange={(event) => {
                  const nextSupplierId = event.target.value;
                  if (nextSupplierId !== supplierId && lines.length > 0) {
                    if (!window.confirm("Promena dobavljača će ukloniti stavke iz nacrta. Nastaviti?")) return;
                    setLines([]);
                  }
                  setSupplierId(nextSupplierId);
                  setError(null);
                }} required><option value="" disabled>Izaberite dobavljača</option>{activeSuppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label>
                <label>Očekivani datum isporuke<input type="date" value={expectedDeliveryDate} onChange={(event) => setExpectedDeliveryDate(event.target.value)} /></label>
                <label className="full-field">Napomene za dobavljača<input value={orderNotes} onChange={(event) => setOrderNotes(event.target.value)} maxLength={1000} placeholder="Opcionalna napomena" /></label>
              </div>
              <div className="order-add-line">
                <label>Proizvod<select value={selectedProductId} onChange={(event) => {
                  const productId = event.target.value;
                  setSelectedProductId(productId);
                  const catalogItem = supplierProducts.find((item) => item.supplierId === supplierId && item.productId === productId);
                  if (catalogItem) setSelectedQuantity((current) => Math.max(current, catalogItem.minimumOrderQuantity));
                }}><option value="">Izaberite proizvod</option>{products.map((product) => {
                  const catalogItem = supplierProducts.find((item) => item.supplierId === supplierId && item.productId === product.id);
                  return <option key={product.id} value={product.id}>{product.name} · {product.sku} · stanje {product.onHand}{catalogItem ? ` · cena ${formatMoney(catalogItem.unitCost)}` : ""}</option>;
                })}</select></label>
                <label>Količina<input type="number" min={Math.max(1, supplierProducts.find((item) => item.supplierId === supplierId && item.productId === selectedProductId)?.minimumOrderQuantity ?? 1)} step="1" value={selectedQuantity} onChange={(event) => setSelectedQuantity(Number(event.target.value))} /></label>
                <button className="secondary-action" type="button" onClick={addLine} disabled={!selectedProductId || selectedQuantity < 1}><Plus size={16} /> Dodaj stavku</button>
              </div>
              {lines.length > 0 && (
                <div className="table-wrap order-lines-wrap">
                  <table className="catalog-table order-lines-table">
                    <thead><tr><th>PROIZVOD</th><th>KOLIČINA</th><th>NABAVNA CENA / JED. (RSD)</th><th>UKUPNO ({displayCurrency})</th><th></th></tr></thead>
                    <tbody>{lines.map((line) => (
                      <tr key={line.product.id}>
                        <td><div className="product-cell"><span className="product-thumb">{line.product.name.charAt(0)}</span><span><strong>{line.product.name}</strong><small>{line.product.sku}{line.supplierSku ? ` · SKU dobavljača: ${line.supplierSku}` : ""}</small></span></div></td>
                        <td><input className="table-number-input" type="number" min={line.minimumOrderQuantity} value={line.quantity} onChange={(event) => updateLine(line.product.id, { quantity: Number(event.target.value) })} aria-label={`Količina za ${line.product.name}`} />{line.minimumOrderQuantity > 1 && <small className="line-minimum">Minimum {line.minimumOrderQuantity}</small>}</td>
                        <td><input className="table-number-input cost-input" type="number" min="0" step="0.01" placeholder={String(line.product.unitCost)} value={line.unitCost ?? ""} onChange={(event) => updateLine(line.product.id, { unitCost: event.target.value === "" ? null : Number(event.target.value) })} aria-label={`Cena za ${line.product.name}`} /></td>
                        <td className="cost-cell">{formatMoney(line.quantity * (line.unitCost ?? line.product.unitCost))}</td>
                        <td><button className="icon-action" type="button" onClick={() => setLines((current) => current.filter((entry) => entry.product.id !== line.product.id))} aria-label={`Ukloni ${line.product.name}`}><X size={16} /></button></td>
                      </tr>
                    ))}</tbody>
                  </table>
                </div>
              )}
              <div className="order-create-footer">
                <span>Procena nacrta: <strong>{formatMoney(lines.reduce((sum, line) => sum + line.quantity * (line.unitCost ?? line.product.unitCost), 0))}</strong></span>
                <button className="primary-action" type="submit" disabled={creating || lines.length === 0}>{creating ? "Čuvanje…" : "Sačuvaj nacrt"}</button>
              </div>
              </fieldset>
            </form>
          </section>
        )}

        {activeSuppliers.length === 0 && canManageOrders && (
          <div className="empty-callout"><strong>Prvo dodajte dobavljača</strong><span>Za kreiranje porudžbine potreban je aktivan dobavljač.</span><Link className="secondary-action" href="/suppliers">Upravljaj dobavljačima</Link></div>
        )}

        <section className="section-card orders-list-card" aria-labelledby="orders-list-title">
          <div className="catalog-toolbar">
            <div><h2 id="orders-list-title">Praćenje porudžbina</h2><p>{orders ? `${quantityFormat.format(orders.totalElements)} ukupno · prikazano ${visibleOrders.length}` : "Učitavanje porudžbina"}</p></div>
            <div className="catalog-filters order-filters">
              <label className="stock-filter order-status-filter">Status
                <select value={statusFilter} onChange={(event) => { setPage(0); setStatusFilter(event.target.value as "ALL" | "OVERDUE" | PurchaseOrderStatus); }}>
                  <option value="ALL">Sve</option><option value="OVERDUE">Kasne</option><option value="DRAFT">Nacrti</option><option value="ORDERED">Poručene</option><option value="PARTIALLY_RECEIVED">Delimično primljene</option><option value="RECEIVED">Primljene</option><option value="CANCELLED">Otkazane</option>
                </select>
              </label>
              <label className="stock-filter order-status-filter">Dobavljač
                <select value={supplierFilter} onChange={(event) => { setPage(0); setSupplierFilter(event.target.value); }}>
                  <option value="">Svi dobavljači</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
                </select>
              </label>
              <label className="stock-filter order-date-filter">Od<input type="date" value={dateFrom} max={dateTo || undefined} onChange={(event) => { setPage(0); setDateFrom(event.target.value); }} /></label>
              <label className="stock-filter order-date-filter">Do<input type="date" value={dateTo} min={dateFrom || undefined} onChange={(event) => { setPage(0); setDateTo(event.target.value); }} /></label>
            </div>
          </div>
          {orders && visibleOrders.length === 0 ? (
            <div className="supplier-empty"><ClipboardList size={27} /><strong>{statusFilter === "OVERDUE" ? "Nema porudžbina kojima je rok istekao" : orders.totalElements ? "Nema porudžbina za izabrane filtere" : "Još nema porudžbina"}</strong><p>Predloge za količine možete pregledati na stranici nabavke.</p><Link className="secondary-action" href="/purchasing">Otvori preporuke</Link></div>
          ) : (
            <div className="purchase-order-list">
              {visibleOrders.map((order) => (
                <article className={`purchase-order-card${isOverdue(order, today) ? " purchase-order-overdue" : ""}`} key={order.id}>
                  <div className="purchase-order-heading">
                    <div><span className="order-number">{order.orderNumber}</span><h3>{order.supplierName}</h3><small>Kreirana {new Intl.DateTimeFormat("sr-RS", { dateStyle: "medium" }).format(new Date(order.createdAt))}{order.expectedDeliveryDate ? ` · isporuka do ${new Intl.DateTimeFormat("sr-RS", { dateStyle: "medium" }).format(new Date(`${order.expectedDeliveryDate}T00:00:00`))}` : ""}</small></div>
                    <div className="order-badges">
                      <span className={`status-badge status-${order.status.toLocaleLowerCase("en-US").replaceAll("_", "-")}`}>{statusLabels[order.status]}</span>
                      {isOverdue(order, today) && <span className="status-badge status-overdue"><AlertTriangle size={13} /> Kasni {overdueDays(order, today)} {overdueDays(order, today) === 1 ? "dan" : "dana"}</span>}
                    </div>
                  </div>
                  {order.notes && <p className="order-notes">{order.notes}</p>}
                  <div className="table-wrap">
                    <table className="catalog-table order-lines-table">
                      <thead><tr><th>STAVKA</th><th>PORUČENO</th><th>PRIMLJENO</th><th>PREOSTALO</th><th>VREDNOST</th></tr></thead>
                      <tbody>{order.lines.map((line) => (
                        <tr key={line.productId}><td><div className="product-cell"><span><strong>{line.productName}</strong><small>{line.sku}{line.supplierSku ? ` · SKU dobavljača: ${line.supplierSku}` : ""}</small></span></div></td><td>{quantityFormat.format(line.orderedQuantity)} {line.unit}</td><td>{quantityFormat.format(line.receivedQuantity)} {line.unit}</td><td>{quantityFormat.format(line.orderedQuantity - line.receivedQuantity)} {line.unit}</td><td className="cost-cell">{formatMoney(line.lineTotal)}</td></tr>
                      ))}</tbody>
                    </table>
                  </div>
                  <div className="purchase-order-footer">
                    <span>Ukupno <strong>{formatMoney(order.total)}</strong></span>
                    <div className="order-actions">
                      {order.status === "DRAFT" && canManageOrders && <button className="primary-action" type="button" disabled={busyOrderId === order.id} onClick={() => void runOrderAction(order.id, "dispatch")}>Označi kao poručeno</button>}
                      {(order.status === "ORDERED" || order.status === "PARTIALLY_RECEIVED") && canReceiveOrders && (
                        <form className="receive-form" onSubmit={(event) => void receiveOrder(event, order)}>
                          <div>{order.lines.filter((line) => line.receivedQuantity < line.orderedQuantity).map((line) => <label key={line.productId}>{line.productName}<small>Preostalo: {quantityFormat.format(line.orderedQuantity - line.receivedQuantity)} {line.unit}</small><input name={line.productId} type="number" min="0" max={line.orderedQuantity - line.receivedQuantity} defaultValue="0" aria-label={`Stvarno primljena količina za ${line.productName}`} /></label>)}</div>
                          <button className="primary-action" type="submit" disabled={busyOrderId === order.id}>{busyOrderId === order.id ? "Evidentiranje…" : "Evidentiraj prijem"}</button>
                        </form>
                      )}
                      {["DRAFT", "ORDERED", "PARTIALLY_RECEIVED"].includes(order.status) && canManageOrders && <button className="secondary-action danger-action" type="button" disabled={busyOrderId === order.id} onClick={() => void runOrderAction(order.id, "cancel")}>Otkaži</button>}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
          {orders && orders.totalPages > 1 && <div className="movement-pagination">
            <span>Strana {orders.number + 1} od {orders.totalPages}</span>
            <div>
              <button className="secondary-action" type="button" disabled={orders.first} onClick={() => setPage((current) => Math.max(0, current - 1))}>Prethodna</button>
              <button className="secondary-action" type="button" disabled={orders.last} onClick={() => setPage((current) => current + 1)}>Sledeća</button>
            </div>
          </div>}
          {!orders && <div className="supplier-empty" role="status">Učitavanje porudžbina…</div>}
        </section>
        <footer className="footer-note"><span>Prijem se knjiži kao ulaz robe sa referencom na broj porudžbine.</span><Link href="/purchasing">Preporuke za nabavku</Link></footer>
      </div>
    </WorkspaceShell>
  );
}
