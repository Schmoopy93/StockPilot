"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  AlertTriangle, ArrowDownLeft, ArrowUpRight, Boxes, ChevronDown, CircleHelp,
  ClipboardList, LayoutDashboard, Package, Plus, Settings2, Truck, X,
} from "lucide-react";
import {
  createProduct, getDashboard, getProducts, recordMovement,
  type Dashboard as DashboardData, type Product,
} from "@/lib/api";

const currency = new Intl.NumberFormat("sr-RS", { style: "currency", currency: "EUR" });
const quantity = new Intl.NumberFormat("sr-RS");

export default function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formMode, setFormMode] = useState<"product" | "movement" | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    getDashboard()
      .then((dashboard) => { if (active) setData(dashboard); })
      .catch(() => { if (active) setError("Nije moguće povezati se sa API servisom."); });
    return () => { active = false; };
  }, [reloadKey]);

  async function openForm(mode: "product" | "movement") {
    setFormError(null);
    setFormMode(mode);
    if (mode === "movement") {
      try {
        setProducts(await getProducts());
      } catch {
        setFormError("Nije moguće učitati katalog proizvoda.");
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
      } catch {
        setError("Promena je sačuvana, ali dashboard nije mogao da se osveži. Ponovo učitajte stranicu.");
      }
    } catch (submitError) {
      setFormError(submitError instanceof Error ? submitError.message : "Operacija nije uspela.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="StockPilot početna">
          <span className="brand-mark"><Boxes size={21} /></span>
          <span>stock<span className="brand-accent">pilot</span></span>
        </a>
        <div className="workspace-label">RADNI PROSTOR</div>
        <button className="workspace-switch"><span className="workspace-avatar">B</span><span className="workspace-copy"><strong>Bean &amp; Bloom</strong><small>Maloprodaja</small></span><ChevronDown size={16} /></button>
        <div className="nav-label">MENI</div>
        <nav className="nav-list" aria-label="Glavna navigacija">
          <a className="nav-item active" href="#dashboard"><LayoutDashboard size={18} /> Pregled</a>
          <a className="nav-item" href="#inventory"><Package size={18} /> Proizvodi <span className="nav-count">{data?.activeProducts ?? "—"}</span></a>
          <a className="nav-item" href="#recommendations"><ClipboardList size={18} /> Nabavka <span className="nav-dot" /></a>
          <a className="nav-item" href="#suppliers"><Truck size={18} /> Dobavljači</a>
        </nav>
        <div className="sidebar-bottom">
          <div className="help-card"><span className="help-icon"><CircleHelp size={17} /></span><strong>Treba vam pomoć?</strong><p>Naš tim je tu da pomogne.</p><a href="mailto:podrska@stockpilot.rs">Kontaktirajte podršku <span>↗</span></a></div>
          <a className="nav-item settings-link" href="#settings"><Settings2 size={18} /> Podešavanja</a>
          <div className="profile"><span className="profile-avatar">MP</span><span className="profile-copy"><strong>Marko Petrović</strong><small>Vlasnik naloga</small></span><ChevronDown size={16} /></div>
        </div>
      </aside>

      <section className="main-panel" id="dashboard">
        <header className="topbar">
          <div className="breadcrumbs">Pregled <span>/</span> <strong>Dashboard</strong></div>
          <div className="topbar-actions"><span className="today">Četvrtak, 5. novembar 2026.</span><button className="icon-button" aria-label="Obaveštenja"><span className="notification-dot" />♧</button><span className="top-avatar">MP</span></div>
        </header>
        <div className="content">
          <div className="page-heading"><div><div className="eyebrow">PREGLED POSLOVANJA</div><h1>Dobro jutro <span>✦</span></h1><p>Evo šta se dešava sa vašim zalihama danas.</p></div><div className="heading-actions"><button className="secondary-action" onClick={() => openForm("movement")}><ArrowDownLeft size={15} /> Evidentiraj robu</button><button className="primary-action" onClick={() => openForm("product")}><Plus size={16} /> Novi proizvod</button></div></div>
          {error && <div className="error-banner" role="alert">{error} Proverite da li je backend pokrenut. <button onClick={() => { setError(null); setReloadKey((key) => key + 1); }}>Pokušaj ponovo</button></div>}

          <section className="metrics" aria-label="Ključni pokazatelji">
            <article className="metric-card"><div className="metric-top"><span>Aktivni proizvodi</span><span className="metric-icon purple"><Package size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.activeProducts) : "—"}</div><div className="metric-foot"><span className="foot-muted">U vašem katalogu</span></div></article>
            <article className="metric-card"><div className="metric-top"><span>Jedinice na stanju</span><span className="metric-icon blue"><Boxes size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.unitsOnHand) : "—"}</div><div className="metric-foot"><span className="foot-muted">Kroz sve proizvode</span></div></article>
            <article className="metric-card"><div className="metric-top"><span>Ispod minimuma</span><span className="metric-icon amber"><AlertTriangle size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.productsBelowMinimum) : "—"}<span className="value-suffix"> artikala</span></div><div className="metric-foot"><span className="foot-warning"><span className="tiny-dot" /> Zahteva pažnju</span></div></article>
            <article className="metric-card"><div className="metric-top"><span>Predlog za nabavku</span><span className="metric-icon green"><ClipboardList size={18} /></span></div><div className="metric-value">{data ? quantity.format(data.productsToReorder) : "—"}<span className="value-suffix"> artikala</span></div><div className="metric-foot"><span className="foot-muted">Procena {data ? currency.format(data.estimatedReorderCost) : "—"}</span></div></article>
          </section>

          <section className="section-card recommendations" id="recommendations">
            <div className="section-heading"><div><div className="section-title-row"><span className="section-icon"><ClipboardList size={18} /></span><h2>Pametni predlog nabavke</h2><span className="new-pill">PREPORUČENO</span></div><p>Artikli za koje je vreme da obnovite zalihe.</p></div><a className="text-link" href="#inventory">Pogledaj sve <span>→</span></a></div>
            <div className="table-wrap"><table><thead><tr><th>PROIZVOD</th><th>TRENUTNO STANJE</th><th>PRODAJA / 30 DANA</th><th>ROK DOBAVLJAČA</th><th>PREDLOG NABAVKE</th><th>PROCENJENI TROŠAK</th></tr></thead><tbody>
              {data?.recommendations.slice(0, 5).map((item) => <tr key={item.productId}><td><div className="product-cell"><span className="product-thumb">{item.productName.charAt(0)}</span><span><strong>{item.productName}</strong><small>{item.sku}</small></span></div></td><td><span className={`stock-pill ${item.onHand <= 5 ? "stock-critical" : "stock-low"}`}>{quantity.format(item.onHand)} {item.unit}</span></td><td>{quantity.format(item.demandLast30Days)} {item.unit}</td><td>{item.leadTimeDays} dana</td><td><strong className="order-qty">{quantity.format(item.recommendedQuantity)} {item.unit}</strong>{item.lowConfidence && <span className="confidence"> · malo podataka</span>}</td><td className="cost-cell">{currency.format(item.estimatedCost)}</td></tr>)}
              {!data && !error && <tr><td className="empty-state" colSpan={6}>Učitavanje predloga…</td></tr>}
              {data?.recommendations.length === 0 && <tr><td className="empty-state" colSpan={6}>Trenutno nema artikala za nabavku.</td></tr>}
            </tbody></table></div>
            <div className="table-footer"><span>Preporuke su izračunate na osnovu prodaje u poslednjih 30 dana i roka isporuke.</span><a href="#methodology">Kako se računa? <CircleHelp size={14} /></a></div>
          </section>

          <div className="lower-grid">
            <section className="section-card stock-card" id="inventory"><div className="compact-heading"><div><h2>Artikli za proveru</h2><p>Najniže stanje u katalogu</p></div><a className="text-link" href="#inventory">Svi artikli →</a></div>
              <div className="stock-list">{data?.lowStock.slice(0, 4).map((product, index) => <div className="stock-row" key={product.id}><span className={`mini-thumb tone-${index}`}>{product.name.charAt(0)}</span><span className="stock-name"><strong>{product.name}</strong><small>{product.sku}</small></span><span className="stock-remaining"><strong>{quantity.format(product.onHand)}</strong><small>od min. {product.minimumStock} {product.unit}</small></span></div>)}
                {!data && <div className="empty-state small-empty">Učitavanje zaliha…</div>}
                {data?.lowStock.length === 0 && <div className="empty-state small-empty">Nema artikala ispod minimuma.</div>}
              </div>
            </section>
            <section className="section-card activity-card"><div className="compact-heading"><div><h2>Nedavne aktivnosti</h2><p>Poslednja kretanja robe</p></div><button className="more-button" aria-label="Više aktivnosti">···</button></div>
              <div className="activity-list">{data?.recentMovements.slice(0, 4).map((movement) => <div className="activity-row" key={movement.id}><span className={`activity-icon ${movement.type === "INBOUND" ? "activity-in" : "activity-out"}`}>{movement.type === "INBOUND" ? <ArrowDownLeft size={17} /> : <ArrowUpRight size={17} />}</span><span className="activity-copy"><strong>{movement.type === "INBOUND" ? "Prijem robe" : movement.type === "OUTBOUND" ? "Izlaz robe" : "Korekcija stanja"}</strong><small>{movement.productName} · {movement.note ?? "Bez napomene"}</small></span><span className={`activity-qty ${movement.type === "INBOUND" ? "qty-in" : "qty-out"}`}>{movement.type === "INBOUND" ? "+" : "−"}{movement.quantity}</span></div>)}
                {!data && <div className="empty-state small-empty">Učitavanje aktivnosti…</div>}
              </div>
            </section>
          </div>
          <footer className="footer-note"><span>© 2026 StockPilot</span><span>Vaše zalihe, pod kontrolom.</span><a href="#support">Pomoć i podrška</a></footer>
        </div>
      </section>
      {formMode && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setFormMode(null); }}><section className="entry-modal" role="dialog" aria-modal="true" aria-labelledby="entry-title"><div className="modal-heading"><div><span className="modal-kicker">KATALOG I ZALIHE</span><h2 id="entry-title">{formMode === "product" ? "Dodaj proizvod" : "Evidentiraj kretanje robe"}</h2></div><button className="close-button" onClick={() => setFormMode(null)} aria-label="Zatvori"><X size={18} /></button></div><form onSubmit={submitForm}>
        {formMode === "product" ? <div className="form-grid"><label>SKU<input name="sku" required maxLength={64} placeholder="npr. COF-002" /></label><label>Naziv proizvoda<input name="name" required maxLength={200} placeholder="Naziv proizvoda" /></label><label>Kategorija<input name="category" maxLength={100} placeholder="Kategorija" /></label><label>Jedinica<input name="unit" required maxLength={16} defaultValue="kom" /></label><label>Minimalna zaliha<input name="minimumStock" type="number" min="0" required defaultValue="5" /></label><label>Rok isporuke (dani)<input name="leadTimeDays" type="number" min="0" required defaultValue="5" /></label><label className="full-field">Nabavna cena (EUR)<input name="unitCost" type="number" min="0" step="0.01" required defaultValue="0" /></label></div> : <div className="form-grid"><label className="full-field">Proizvod<select name="productId" required defaultValue=""><option value="" disabled>Izaberite proizvod</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name} ({product.sku}) · stanje {product.onHand}</option>)}</select></label><label>Vrsta kretanja<select name="type" defaultValue="INBOUND"><option value="INBOUND">Prijem robe (+)</option><option value="OUTBOUND">Izlaz robe (−)</option><option value="ADJUSTMENT">Korekcija stanja (±)</option></select></label><label>Količina<input name="quantity" type="number" required step="1" defaultValue="1" /></label><label className="full-field">Napomena<input name="note" maxLength={300} placeholder="Opcionalna napomena" /></label></div>}
        {formMode === "movement" && <p className="form-hint">Za korekciju unesite pozitivan ili negativan broj. Izlaz ne može biti veći od stanja.</p>}
        {formError && <p className="form-error" role="alert">{formError}</p>}<div className="modal-actions"><button type="button" className="secondary-action" onClick={() => setFormMode(null)}>Otkaži</button><button type="submit" className="primary-action" disabled={saving}>{saving ? "Čuvanje…" : "Sačuvaj"}</button></div>
      </form></section></div>}
    </main>
  );
}
