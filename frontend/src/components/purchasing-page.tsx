"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ClipboardList, Search, Star, TrendingUp } from "lucide-react";
import { ApiError, createPurchaseOrders, getCurrentUser, getDashboard, getSuppliers, type AuthUser, type Dashboard as DashboardData, type Supplier } from "@/lib/api";
import { useCurrency } from "@/components/currency-context";
import WorkspaceShell from "@/components/workspace-shell";

const quantity = new Intl.NumberFormat("sr-RS");

export default function PurchasingPage() {
  const router = useRouter();
  const { formatMoney } = useCurrency();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [showLowConfidence, setShowLowConfidence] = useState(false);
  const [selected, setSelected] = useState<Record<string, number>>({});
  const [orderError, setOrderError] = useState<string | null>(null);
  const [orderNotice, setOrderNotice] = useState<string | null>(null);
  const [creatingOrders, setCreatingOrders] = useState(false);

  async function loadData() {
    setError(null);
    try {
      const [currentUser, overview, supplierList] = await Promise.all([getCurrentUser(), getDashboard(), getSuppliers()]);
      setUser(currentUser);
      setDashboard(overview);
      setSuppliers(supplierList);
    } catch (loadError) {
      if (loadError instanceof ApiError && loadError.status === 401) {
        router.replace("/login?next=%2Fpurchasing&status=login-required");
      } else {
        setError("Preporuke trenutno nisu dostupne. Pokušajte ponovo.");
      }
    }
  }

  useEffect(() => {
    let active = true;
    Promise.all([getCurrentUser(), getDashboard(), getSuppliers()])
      .then(([currentUser, overview, supplierList]) => {
        if (active) {
          setUser(currentUser);
          setDashboard(overview);
          setSuppliers(supplierList);
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2Fpurchasing&status=login-required");
        } else {
          setError("Preporuke trenutno nisu dostupne. Pokušajte ponovo.");
        }
      });
    return () => { active = false; };
  }, [router]);

  const recommendations = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("sr-RS");
    return (dashboard?.recommendations ?? []).filter((item) => {
      const matchesSearch = !term || [item.productName, item.sku]
        .some((value) => value.toLocaleLowerCase("sr-RS").includes(term));
      return matchesSearch && (!showLowConfidence || item.lowConfidence);
    });
  }, [dashboard, search, showLowConfidence]);
  const totalCost = recommendations.reduce((sum, item) => sum + item.estimatedCost, 0);
  const selectedItems = (dashboard?.recommendations ?? [])
    .filter((item) => selected[item.productId] !== undefined);

  async function createOrderFromSelected() {
    if (selectedItems.some((item) => !Number.isInteger(selected[item.productId]) || selected[item.productId] < 1)) {
      setOrderError("Količina za svaku izabranu stavku mora biti bar 1.");
      return;
    }
    const activeSupplierIds = new Set(suppliers.filter((supplier) => supplier.active).map((supplier) => supplier.id));
    const missingSupplier = selectedItems.find((item) =>
      !item.preferredSupplierId || !activeSupplierIds.has(item.preferredSupplierId));
    if (missingSupplier) {
      setOrderError(`Za ${missingSupplier.productName} nema aktivnog primarnog dobavljača. Podesite ga u sekciji Dobavljači.`);
      return;
    }

    const groups = new Map<string, typeof selectedItems>();
    selectedItems.forEach((item) => {
      const supplierId = item.preferredSupplierId;
      if (!supplierId) return;
      groups.set(supplierId, [...(groups.get(supplierId) ?? []), item]);
    });
    setCreatingOrders(true);
    setOrderError(null);
    setOrderNotice(null);
    try {
      const created = await createPurchaseOrders([...groups].map(([supplierId, items]) => ({
        supplierId,
        expectedDeliveryDate: null,
        notes: "Nacrt kreiran iz preporuka za nabavku.",
        lines: items.map((item) => ({
          productId: item.productId,
          quantity: selected[item.productId],
          unitCost: null,
        })),
      })));
      setSelected({});
      setOrderNotice(`Kreirano nacrta: ${created.map((order) => order.orderNumber).join(", ")}.`);
    } catch (createError) {
      if (createError instanceof ApiError && createError.status === 401) {
        router.replace("/login?next=%2Fpurchasing&status=session-expired");
      } else if (createError instanceof ApiError && createError.status === 403) {
        setOrderError("Vaša uloga nema dozvolu za kreiranje porudžbina.");
      } else {
        setOrderError(createError instanceof Error ? createError.message : "Nacrti porudžbina nisu kreirani.");
      }
    } finally {
      setCreatingOrders(false);
    }
  }

  return (
    <WorkspaceShell user={user} active="purchasing">
      <div className="content workspace-content">
        <div className="page-heading">
          <div>
            <div className="eyebrow">PLANIRANJE ZALIHA</div>
            <h1>Preporuke za nabavku</h1>
            <p>Planirajte porudžbine na osnovu prodaje, stanja i rokova isporuke.</p>
          </div>
        </div>

        {error && <div className="error-banner" role="alert">{error}<button type="button" onClick={() => void loadData()}>Pokušaj ponovo</button></div>}
        {orderNotice && <div className="success-banner" role="status">{orderNotice} <Link href="/orders">Pregledaj porudžbine</Link></div>}

        <section className="purchasing-summary" aria-label="Sažetak nabavke">
          <article className="purchasing-summary-card">
            <span className="purchasing-summary-icon"><ClipboardList size={19} /></span>
            <span><small>Proizvoda za poručivanje</small><strong>{dashboard ? quantity.format(dashboard.productsToReorder) : "—"}</strong></span>
          </article>
          <article className="purchasing-summary-card">
            <span className="purchasing-summary-icon purchasing-summary-cost"><TrendingUp size={19} /></span>
            <span><small>Procena ukupne nabavke</small><strong>{dashboard ? formatMoney(dashboard.estimatedReorderCost) : "—"}</strong></span>
          </article>
          <p>Predlozi koriste potrošnju iz poslednjih 30 dana, rok isporuke i sigurnosnu zalihu.</p>
        </section>

        <section className="section-card catalog-card" aria-labelledby="recommendations-title">
          <div className="catalog-toolbar">
            <div><h2 id="recommendations-title">Predložene količine</h2><p>{dashboard ? `${recommendations.length} preporuka` : "Računamo preporuke"}</p></div>
            <div className="catalog-filters">
              <label className="catalog-search"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Pretraži proizvod ili SKU" aria-label="Pretraži preporuke" /></label>
              <label className="stock-filter"><input type="checkbox" checked={showLowConfidence} onChange={(event) => setShowLowConfidence(event.target.checked)} /> Malo prodajnih podataka</label>
            </div>
          </div>
          {orderError && <p className="form-error order-inline-error" role="alert">{orderError}</p>}
          {selectedItems.length > 0 && (
            <div className="recommendation-selection">
              <span>Izabrano: <strong>{selectedItems.length}</strong> · {new Set(selectedItems.map((item) => item.preferredSupplierId).filter(Boolean)).size} dobavljača</span>
              <button className="primary-action" type="button" disabled={creatingOrders} onClick={() => void createOrderFromSelected()}>{creatingOrders ? "Kreiranje nacrta…" : "Kreiraj nacrte po dobavljačima"}</button>
            </div>
          )}
          <div className="table-wrap">
            <table className="catalog-table purchasing-table">
              <thead><tr><th>IZBOR</th><th>PROIZVOD</th><th>STANJE</th><th>POTROŠNJA / 30 DANA</th><th>ROK ISPORUKE</th><th>PREDLOG KOLIČINE</th><th>PROCENJENI TROŠAK</th></tr></thead>
              <tbody>
                {recommendations.map((item) => (
                  <tr key={item.productId}>
                    <td><input className="recommendation-checkbox" type="checkbox" checked={selected[item.productId] !== undefined} onChange={(event) => setSelected((current) => { const next = { ...current }; if (event.target.checked) next[item.productId] = item.recommendedQuantity; else delete next[item.productId]; return next; })} aria-label={`Izaberi ${item.productName} za porudžbinu`} /></td>
                    <td><div className="product-cell"><span className="product-thumb">{item.productName.charAt(0).toUpperCase()}</span><span><strong>{item.productName}</strong><small>{item.sku}</small>{item.preferredSupplierName && <small className="preferred-supplier-label"><Star size={12} /> {item.preferredSupplierName}{item.supplierSku ? ` · ${item.supplierSku}` : ""} · {formatMoney(item.supplierUnitCost)}/{item.unit}</small>}</span></div></td>
                    <td><span className={`stock-pill ${item.onHand <= 0 ? "stock-critical" : "stock-low"}`}>{quantity.format(item.onHand)} {item.unit}</span>{item.onOrder > 0 && <small className="stock-on-order">U nabavci: {quantity.format(item.onOrder)} {item.unit}</small>}</td>
                    <td>{quantity.format(item.demandLast30Days)} {item.unit}</td>
                    <td>{item.leadTimeDays} dana</td>
                    <td>{selected[item.productId] !== undefined
                      ? <label className="recommended-quantity">Količina<input type="number" min={item.minimumOrderQuantity} value={selected[item.productId]} onChange={(event) => setSelected((current) => ({ ...current, [item.productId]: Number(event.target.value) }))} aria-label={`Količina za ${item.productName}`} />{item.minimumOrderQuantity > 1 && <small>Minimalno {item.minimumOrderQuantity}</small>}</label>
                      : <strong className="order-qty">{quantity.format(item.recommendedQuantity)} {item.unit}</strong>}
                      <details className="recommendation-reason">
                        <summary>Kako je izračunato</summary>
                        <span>Ciljna zaliha {quantity.format(item.targetStock)} {item.unit} (minimum {quantity.format(item.minimumStock)}). Od cilja se oduzimaju stanje ({quantity.format(item.onHand)}) i količina u nabavci ({quantity.format(item.onOrder)}). {item.lowConfidence ? "Bez izlaza u poslednjih 30 dana, koristi se minimalna zaliha." : `Prosek potrošnje: ${item.averageDailyDemand.toLocaleString("sr-RS", { maximumFractionDigits: 2 })}/${item.unit}/dan.`}</span>
                      </details>
                      {item.lowConfidence && <span className="confidence"> · nizak nivo pouzdanosti</span>}
                    </td>
                    <td className="cost-cell">{formatMoney(item.estimatedCost)}</td>
                  </tr>
                ))}
                {!dashboard && <tr><td className="empty-state" colSpan={7}>Učitavanje preporuka…</td></tr>}
                {dashboard && recommendations.length === 0 && (
                  <tr><td className="empty-state" colSpan={7}>
                    {dashboard.recommendations.length === 0
                      ? "Trenutno nema proizvoda za poručivanje."
                      : "Nema preporuka koje odgovaraju izabranom pretraživanju."}
                  </td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="table-footer" id="methodology">
            <span>Procena troška ne uključuje porez, dostavu ni dobavljačke pakete.</span>
            <span>Model koristi prosečnu dnevnu potrošnju i sigurnosnu zalihu.</span>
          </div>
        </section>
        <footer className="footer-note"><span>© {new Date().getFullYear()} StockPilot</span><span>Preporuka je pomoć pri planiranju, ne automatska porudžbina.</span></footer>
        <span className="sr-only" aria-live="polite">Procena filtrirane nabavke: {formatMoney(totalCost)}</span>
      </div>
    </WorkspaceShell>
  );
}
