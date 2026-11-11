"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  AlertTriangle, ArrowDownLeft, ArrowUpRight, Boxes, CircleHelp,
  ClipboardList, Package, Plus, X,
} from "lucide-react";
import {
  ApiError, createProduct, getCurrentUser, getDashboard, getProducts, recordMovement,
  type AuthUser, type Dashboard as DashboardData, type Product,
} from "@/lib/api";
import { useCurrency } from "@/components/currency-context";
import WorkspaceShell from "@/components/workspace-shell";

const quantity = new Intl.NumberFormat("sr-RS");

export default function Dashboard() {
  const router = useRouter();
  const { formatMoney, pricesReady } = useCurrency();
  const [data, setData] = useState<DashboardData | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [userLoaded, setUserLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formMode, setFormMode] = useState<"product" | "movement" | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const canManageProducts = userLoaded && (!user || user.role === "OWNER" || user.role === "ADMIN");
  const canRecordMovement = userLoaded && (!user || user.role !== "READ_ONLY");
  useEffect(() => {
    let active = true;
    let redirecting = false;
    const redirectToLogin = () => {
      if (!redirecting) {
        redirecting = true;
        router.replace("/login?next=%2F&status=login-required");
      }
    };
    getCurrentUser()
      .then((currentUser) => {
        if (active) {
          setUser(currentUser);
          setUserLoaded(true);
        }
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof ApiError && requestError.status === 401) {
          redirectToLogin();
        } else if (active) {
          setError("Nije moguće proveriti korisničku sesiju.");
        }
      });
    getDashboard()
      .then((dashboard) => { if (active) setData(dashboard); })
      .catch((requestError: unknown) => {
        if (requestError instanceof ApiError && requestError.status === 401) {
          redirectToLogin();
        } else if (active) {
          setError("Nije moguće povezati se sa API servisom.");
        }
      });
    return () => { active = false; };
  }, [reloadKey, router]);

  async function openForm(mode: "product" | "movement") {
    setFormError(null);
    setFormMode(mode);
    if (mode === "movement") {
      try {
        setProducts(await getProducts());
      } catch (loadError) {
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2F&status=session-expired");
        } else {
          setFormError("Nije moguće učitati katalog proizvoda.");
        }
      }
    }
  }

  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    const values = new FormData(event.currentTarget);
    try {
      if (formMode === "product") {
        await createProduct({
          sku: String(values.get("sku")),
          name: String(values.get("name")),
          category: String(values.get("category") || "") || null,
          unit: String(values.get("unit")),
          minimumStock: Number(values.get("minimumStock")),
          leadTimeDays: Number(values.get("leadTimeDays")),
          unitCost: Number(values.get("unitCost")),
        });
      } else {
        await recordMovement({
          productId: String(values.get("productId")),
          type: String(values.get("type")) as "INBOUND" | "OUTBOUND" | "ADJUSTMENT",
          quantity: Number(values.get("quantity")),
          note: String(values.get("note") || ""),
        });
      }
      setFormMode(null);
      try {
        setData(await getDashboard());
      } catch (refreshError) {
        if (refreshError instanceof ApiError && refreshError.status === 401) {
          router.replace("/login?next=%2F&status=session-expired");
        } else {
          setError("Promena je sačuvana, ali dashboard nije mogao da se osveži. Ponovo učitajte stranicu.");
        }
      }
    } catch (submitError) {
      if (submitError instanceof ApiError && submitError.status === 401) {
        router.replace("/login?next=%2F&status=session-expired");
      } else if (submitError instanceof ApiError && submitError.status === 403) {
        setFormError("Vaša uloga nema dozvolu za ovu izmenu.");
      } else {
        setFormError(submitError instanceof Error ? submitError.message : "Operacija nije uspela.");
      }
    } finally {
      setSaving(false);
    }
  }

  function createOrderForLowStock(product: Product) {
    try {
      const recommendation = data?.recommendations.find((item) => item.productId === product.id);
      window.sessionStorage.setItem("stockpilot-recommended-order", JSON.stringify([{
        productId: product.id,
        quantity: recommendation?.recommendedQuantity ?? Math.max(1, product.minimumStock - product.onHand - product.onOrder),
        preferredSupplierId: recommendation?.preferredSupplierId ?? null,
      }]));
      router.push("/orders");
    } catch {
      setError("Nije moguće pripremiti porudžbinu za ovaj proizvod.");
    }
  }

  return (
    <WorkspaceShell user={user} active="dashboard">
      <div className="content">
          <div className="page-heading"><div><div className="eyebrow">STANJE INVENTARA</div><h1>Pregled zaliha</h1><p>Proizvodi, stanje na lageru i predlozi za nabavku.</p></div><div className="heading-actions">{canRecordMovement && <button className="secondary-action" onClick={() => openForm("movement")}><ArrowDownLeft size={15} /> Evidentiraj robu</button>}{canManageProducts && <button className="primary-action" onClick={() => openForm("product")} disabled={!pricesReady}><Plus size={16} /> Novi proizvod</button>}</div></div>
          {error && <div className="error-banner" role="alert">{error} Proverite da li je backend pokrenut. <button onClick={() => { setError(null); setReloadKey((key) => key + 1); }}>Pokušaj ponovo</button></div>}

          <section className="metrics" aria-label="Ključni pokazatelji">
            <article className="metric-card"><div className="metric-top"><span>Aktivni proizvodi</span><span className="metric-icon purple"><Package size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.activeProducts) : "—"}</div><div className="metric-foot"><span className="foot-muted">U vašem katalogu</span></div></article>
            <article className="metric-card"><div className="metric-top"><span>Jedinice na stanju</span><span className="metric-icon blue"><Boxes size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.unitsOnHand) : "—"}</div><div className="metric-foot"><span className="foot-muted">Kroz sve proizvode</span></div></article>
            <article className="metric-card"><div className="metric-top"><span>Ispod minimuma</span><span className="metric-icon amber"><AlertTriangle size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.productsBelowMinimum) : "—"}<span className="value-suffix"> artikala</span></div><div className="metric-foot"><span className="foot-warning"><span className="tiny-dot" /> Zahteva pažnju</span></div></article>
            <article className="metric-card"><div className="metric-top"><span>Predlog za nabavku</span><span className="metric-icon green"><ClipboardList size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.productsToReorder) : "—"}<span className="value-suffix"> artikala</span></div><div className="metric-foot"><span className="foot-muted">Procena {data ? formatMoney(data.estimatedReorderCost) : "—"}</span></div></article>
          </section>

          <section className="section-card recommendations" id="recommendations">
            <div className="section-heading"><div><div className="section-title-row"><span className="section-icon"><ClipboardList size={18} /></span><h2>Predlog za nabavku</h2></div><p>Izračunato iz potrošnje i roka isporuke.</p></div><a className="text-link" href="/purchasing">Sve preporuke <span>→</span></a></div>
            <div className="table-wrap"><table><thead><tr><th>PROIZVOD</th><th>TRENUTNO STANJE</th><th>PRODAJA / 30 DANA</th><th>ROK DOBAVLJAČA</th><th>PREDLOG NABAVKE</th><th>PROCENJENI TROŠAK</th></tr></thead><tbody>
              {data?.recommendations.slice(0, 5).map((item) => <tr key={item.productId}><td><div className="product-cell"><span className="product-thumb">{item.productName.charAt(0)}</span><span><strong>{item.productName}</strong><small>{item.sku}</small>{item.preferredSupplierName && <small className="preferred-supplier-label">{item.preferredSupplierName}</small>}</span></div></td><td><span className={`stock-pill ${item.onHand <= 5 ? "stock-critical" : "stock-low"}`}>{quantity.format(item.onHand)} {item.unit}</span>{item.onOrder > 0 && <small className="stock-on-order">U nabavci: {quantity.format(item.onOrder)} {item.unit}</small>}</td><td>{quantity.format(item.demandLast30Days)} {item.unit}</td><td>{item.leadTimeDays} dana</td><td><strong className="order-qty">{quantity.format(item.recommendedQuantity)} {item.unit}</strong>{item.lowConfidence && <span className="confidence"> · malo podataka</span>}</td><td className="cost-cell">{formatMoney(item.estimatedCost)}</td></tr>)}
              {!data && !error && <tr><td className="empty-state" colSpan={6}>Učitavanje predloga…</td></tr>}
              {data?.recommendations.length === 0 && <tr><td className="empty-state" colSpan={6}>Trenutno nema artikala za nabavku.</td></tr>}
            </tbody></table></div>
            <div className="table-footer"><span>Preporuke su izračunate na osnovu prodaje u poslednjih 30 dana i roka isporuke.</span><a href="/purchasing#methodology">Kako se računa? <CircleHelp size={14} /></a></div>
          </section>

          <div className="lower-grid">
            <section className="section-card stock-card" id="inventory"><div className="compact-heading"><div><h2>Artikli za proveru</h2><p>Najniže stanje u katalogu</p></div><a className="text-link" href="/products">Svi artikli →</a></div>
              <div className="stock-list">{data?.lowStock.slice(0, 4).map((product, index) => <div className="stock-row" key={product.id}><span className={`mini-thumb tone-${index}`}>{product.name.charAt(0)}</span><span className="stock-name"><strong>{product.name}</strong><small>{product.sku}</small></span><span className="stock-remaining"><strong>{quantity.format(product.onHand)}</strong><small>od min. {product.minimumStock} {product.unit}</small></span>{canManageProducts && <button className="stock-order-action" type="button" onClick={() => createOrderForLowStock(product)} aria-label={`Napravi porudžbinu za ${product.name}`}>Poruči</button>}</div>)}
                {!data && <div className="empty-state small-empty">Učitavanje zaliha…</div>}
                {data?.lowStock.length === 0 && <div className="empty-state small-empty">Nema artikala ispod minimuma.</div>}
              </div>
            </section>
            <section className="section-card activity-card"><div className="compact-heading"><div><h2>Nedavna kretanja</h2><p>Poslednje evidentirane promene zaliha</p></div><a className="text-link" href="/movements">Istorija →</a></div>
              <div className="activity-list">{data?.recentMovements.slice(0, 4).map((movement) => {
                const delta = movement.type === "OUTBOUND" ? -movement.quantity : movement.quantity;
                const positive = delta > 0;
                return <div className="activity-row" key={movement.id}><span className={`activity-icon ${positive ? "activity-in" : "activity-out"}`}>{movement.type === "INBOUND" ? <ArrowDownLeft size={17} /> : movement.type === "OUTBOUND" ? <ArrowUpRight size={17} /> : <Boxes size={17} />}</span><span className="activity-copy"><strong>{movement.type === "INBOUND" ? "Prijem robe" : movement.type === "OUTBOUND" ? "Izlaz robe" : "Korekcija stanja"}</strong><small>{movement.productName} · {movement.note ?? "Bez napomene"}</small></span><span className={`activity-qty ${positive ? "qty-in" : "qty-out"}`}>{delta > 0 ? "+" : ""}{quantity.format(delta)}</span></div>;
              })}
                {!data && <div className="empty-state small-empty">Učitavanje aktivnosti…</div>}
              </div>
            </section>
          </div>
          <footer className="footer-note"><span>© {new Date().getFullYear()} StockPilot</span><span>Vaše zalihe, pod kontrolom.</span><a href="/settings">Nalog i bezbednost</a></footer>
      </div>
      {formMode && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setFormMode(null); }}><section className="entry-modal" role="dialog" aria-modal="true" aria-labelledby="entry-title"><div className="modal-heading"><div><span className="modal-kicker">KATALOG I ZALIHE</span><h2 id="entry-title">{formMode === "product" ? "Dodaj proizvod" : "Evidentiraj kretanje robe"}</h2></div><button className="close-button" onClick={() => setFormMode(null)} aria-label="Zatvori"><X size={18} /></button></div><form onSubmit={submitForm}>
        {formMode === "product" ? <div className="form-grid"><label>SKU<input name="sku" required maxLength={64} placeholder="npr. COF-002" /></label><label>Naziv proizvoda<input name="name" required maxLength={200} placeholder="Naziv proizvoda" /></label><label>Kategorija<input name="category" maxLength={100} placeholder="Kategorija" /></label><label>Jedinica<input name="unit" required maxLength={16} defaultValue="kom" /></label><label>Minimalna zaliha<input name="minimumStock" type="number" min="0" required defaultValue="5" /></label><label>Rok isporuke (dani)<input name="leadTimeDays" type="number" min="0" required defaultValue="5" /></label><label className="full-field">Nabavna cena (RSD)<input name="unitCost" type="number" min="0" step="0.01" required defaultValue="0" disabled={!pricesReady} /></label></div> : <div className="form-grid"><label className="full-field">Proizvod<select name="productId" required defaultValue=""><option value="" disabled>Izaberite proizvod</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name} ({product.sku}) · stanje {product.onHand}</option>)}</select></label><label>Vrsta kretanja<select name="type" defaultValue="INBOUND"><option value="INBOUND">Prijem robe (+)</option><option value="OUTBOUND">Izlaz robe (−)</option><option value="ADJUSTMENT">Korekcija stanja (±)</option></select></label><label>Količina<input name="quantity" type="number" required step="1" defaultValue="1" /></label><label className="full-field">Napomena<input name="note" maxLength={300} placeholder="Opcionalna napomena" /></label></div>}
        {formMode === "movement" && <p className="form-hint">Za korekciju unesite pozitivan ili negativan broj. Izlaz ne može biti veći od stanja.</p>}
        {formError && <p className="form-error" role="alert">{formError}</p>}<div className="modal-actions"><button type="button" className="secondary-action" onClick={() => setFormMode(null)}>Otkaži</button><button type="submit" className="primary-action" disabled={saving || (formMode === "product" && !pricesReady)}>{saving ? "Čuvanje…" : "Sačuvaj"}</button></div>
      </form></section></div>}
    </WorkspaceShell>
  );
}
