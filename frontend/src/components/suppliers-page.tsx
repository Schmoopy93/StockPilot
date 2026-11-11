"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Building2, Mail, Package, Phone, Plus, Search, Star, Trash2, X } from "lucide-react";
import { ApiError, deleteSupplierProduct, getCurrentUser, getProducts, getSupplierPerformance, getSupplierProducts, getSuppliers, saveSupplier, saveSupplierProduct, type AuthUser, type Product, type Supplier, type SupplierPerformance, type SupplierProduct } from "@/lib/api";
import { useCurrency } from "@/components/currency-context";
import WorkspaceShell from "@/components/workspace-shell";

export default function SuppliersPage() {
  const router = useRouter();
  const { formatMoney, pricesReady } = useCurrency();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [userLoaded, setUserLoaded] = useState(false);
  const [suppliers, setSuppliers] = useState<Supplier[] | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [supplierProducts, setSupplierProducts] = useState<SupplierProduct[]>([]);
  const [search, setSearch] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [catalogSupplierId, setCatalogSupplierId] = useState<string | null>(null);
  const [performanceBySupplier, setPerformanceBySupplier] = useState<Record<string, SupplierPerformance>>({});
  const [performanceLoadingId, setPerformanceLoadingId] = useState<string | null>(null);
  const [performanceErrors, setPerformanceErrors] = useState<Record<string, string>>({});
  const [editingLink, setEditingLink] = useState<SupplierProduct | null>(null);
  const [catalogSaving, setCatalogSaving] = useState(false);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const canManage = userLoaded && (!user || user.role === "OWNER" || user.role === "ADMIN");

  async function load() {
    setError(null);
    try {
      const [currentUser, items, productList, links] = await Promise.all([
        getCurrentUser(), getSuppliers(), getProducts(), getSupplierProducts(),
      ]);
      setUser(currentUser);
      setUserLoaded(true);
      setSuppliers(items);
      setProducts(productList);
      setSupplierProducts(links);
    } catch (loadError) {
      if (loadError instanceof ApiError && loadError.status === 401) {
        router.replace("/login?next=%2Fsuppliers&status=login-required");
      } else {
        setError("Dobavljači trenutno nisu dostupni. Pokušajte ponovo.");
      }
    }
  }

  useEffect(() => {
    let active = true;
    Promise.all([getCurrentUser(), getSuppliers(), getProducts(), getSupplierProducts()])
      .then(([currentUser, items, productList, links]) => {
        if (active) {
          setUser(currentUser);
          setUserLoaded(true);
          setSuppliers(items);
          setProducts(productList);
          setSupplierProducts(links);
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2Fsuppliers&status=login-required");
        } else {
          setError("Dobavljači trenutno nisu dostupni. Pokušajte ponovo.");
        }
      });
    return () => { active = false; };
  }, [router]);

  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("sr-RS");
    return (suppliers ?? []).filter((supplier) => {
      const matchesTerm = !term || [supplier.name, supplier.email ?? "", supplier.phone ?? ""]
        .some((value) => value.toLocaleLowerCase("sr-RS").includes(term));
      return matchesTerm && (showInactive || supplier.active);
    });
  }, [search, showInactive, suppliers]);

  function openCreate() {
    setEditing(null);
    setFormError(null);
    setFormOpen(true);
  }

  function openEdit(supplier: Supplier) {
    setEditing(supplier);
    setFormError(null);
    setFormOpen(true);
  }

  async function loadPerformance(supplier: Supplier) {
    setPerformanceLoadingId(supplier.id);
    setPerformanceErrors((current) => ({ ...current, [supplier.id]: "" }));
    try {
      const performance = await getSupplierPerformance(supplier.id);
      setPerformanceBySupplier((current) => ({ ...current, [supplier.id]: performance }));
    } catch (loadError) {
      if (loadError instanceof ApiError && loadError.status === 401) {
        router.replace("/login?next=%2Fsuppliers&status=session-expired");
      } else {
        setPerformanceErrors((current) => ({
          ...current,
          [supplier.id]: loadError instanceof Error ? loadError.message : "Istorija dobavljača nije dostupna.",
        }));
      }
    } finally {
      setPerformanceLoadingId(null);
    }
  }

  async function toggleCatalog(supplier: Supplier) {
    setCatalogError(null);
    setEditingLink(null);
    if (catalogSupplierId === supplier.id) {
      setCatalogSupplierId(null);
      return;
    }
    setCatalogSupplierId(supplier.id);
    if (!performanceBySupplier[supplier.id]) await loadPerformance(supplier);
  }

  async function handleSaveSupplierProduct(event: FormEvent<HTMLFormElement>, supplier: Supplier) {
    event.preventDefault();
    setCatalogSaving(true);
    setCatalogError(null);
    const values = new FormData(event.currentTarget);
    const productId = editingLink?.productId ?? String(values.get("productId") ?? "");
    try {
      await saveSupplierProduct(supplier.id, productId, {
        supplierSku: String(values.get("supplierSku") ?? "").trim(),
        unitCost: Number(values.get("unitCost")),
        minimumOrderQuantity: Number(values.get("minimumOrderQuantity")),
        leadTimeDays: String(values.get("leadTimeDays") ?? "") === ""
          ? null : Number(values.get("leadTimeDays")),
        preferredSupplier: values.get("preferredSupplier") === "on",
      });
      setSupplierProducts(await getSupplierProducts());
      setEditingLink(null);
      setNotice("Artikal dobavljača je sačuvan.");
    } catch (saveError) {
      if (saveError instanceof ApiError && saveError.status === 401) {
        router.replace("/login?next=%2Fsuppliers&status=session-expired");
      } else if (saveError instanceof ApiError && saveError.status === 403) {
        setCatalogError("Samo vlasnik ili administrator može da uređuje katalog dobavljača.");
      } else {
        setCatalogError(saveError instanceof Error ? saveError.message : "Artikal dobavljača nije sačuvan.");
      }
    } finally {
      setCatalogSaving(false);
    }
  }

  async function handleDeleteSupplierProduct(link: SupplierProduct) {
    if (!window.confirm(`Ukloniti ${link.productName} iz kataloga dobavljača ${link.supplierName}?`)) return;
    setCatalogError(null);
    try {
      await deleteSupplierProduct(link.supplierId, link.productId);
      setSupplierProducts(await getSupplierProducts());
      setNotice("Artikal je uklonjen iz kataloga dobavljača.");
    } catch (deleteError) {
      if (deleteError instanceof ApiError && deleteError.status === 401) {
        router.replace("/login?next=%2Fsuppliers&status=session-expired");
      } else {
        setCatalogError(deleteError instanceof Error ? deleteError.message : "Artikal nije uklonjen.");
      }
    }
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    const values = new FormData(event.currentTarget);
    try {
      await saveSupplier({
        name: String(values.get("name")).trim(),
        email: String(values.get("email") ?? "").trim(),
        phone: String(values.get("phone") ?? "").trim(),
        notes: String(values.get("notes") ?? "").trim(),
        active: values.get("active") === "on",
      }, editing?.id);
      setFormOpen(false);
      setNotice(editing ? "Podaci o dobavljaču su sačuvani." : "Dobavljač je dodat.");
      await load();
    } catch (saveError) {
      if (saveError instanceof ApiError && saveError.status === 401) {
        router.replace("/login?next=%2Fsuppliers&status=session-expired");
      } else if (saveError instanceof ApiError && saveError.status === 403) {
        setFormError("Samo vlasnik ili administrator može da uređuje dobavljače.");
      } else {
        setFormError(saveError instanceof Error ? saveError.message : "Dobavljač nije sačuvan.");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <WorkspaceShell user={user} active="suppliers">
      <div className="content workspace-content">
        <div className="page-heading">
          <div>
            <div className="eyebrow">PARTNERI ZA NABAVKU</div>
            <h1>Dobavljači</h1>
            <p>Čuvajte kontaktne podatke i pratite sa kim nabavljate robu.</p>
          </div>
          {canManage && <button className="primary-action" type="button" onClick={openCreate}><Plus size={17} /> Dodaj dobavljača</button>}
        </div>

        {notice && <div className="success-banner" role="status">{notice}<button type="button" aria-label="Zatvori poruku" onClick={() => setNotice(null)}><X size={16} /></button></div>}
        {error && <div className="error-banner" role="alert">{error}<button type="button" onClick={() => void load()}>Pokušaj ponovo</button></div>}

        <section className="supplier-toolbar section-card">
          <div><strong>{suppliers?.filter((item) => item.active).length ?? "—"}</strong><span> aktivnih dobavljača</span></div>
          <div className="catalog-filters">
            <label className="catalog-search"><Search size={17} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Naziv, email ili telefon" aria-label="Pretraži dobavljače" /></label>
            <label className="stock-filter"><input type="checkbox" checked={showInactive} onChange={(event) => setShowInactive(event.target.checked)} /> Prikaži neaktivne</label>
          </div>
        </section>

        <section className="supplier-grid" aria-label="Lista dobavljača">
          {filtered.map((supplier) => (
            <article className={`supplier-card section-card${supplier.active ? "" : " supplier-inactive"}`} key={supplier.id}>
              <div className="supplier-card-top">
                <span className="supplier-icon"><Building2 size={21} /></span>
                <span className={`status-badge ${supplier.active ? "status-active" : "status-inactive"}`}>{supplier.active ? "Aktivan" : "Neaktivan"}</span>
              </div>
              <h2>{supplier.name}</h2>
              <div className="supplier-contact">
                {supplier.email ? <a href={`mailto:${supplier.email}`}><Mail size={16} />{supplier.email}</a> : <span><Mail size={16} />Email nije unet</span>}
                {supplier.phone ? <a href={`tel:${supplier.phone}`}><Phone size={16} />{supplier.phone}</a> : <span><Phone size={16} />Telefon nije unet</span>}
              </div>
              {supplier.notes && <p className="supplier-notes">{supplier.notes}</p>}
              <div className="supplier-card-actions">
                {canManage && <button className="secondary-action supplier-edit" type="button" onClick={() => openEdit(supplier)}>Uredi podatke</button>}
                <button className="secondary-action supplier-edit" type="button" onClick={() => toggleCatalog(supplier)}>
                  <Package size={15} /> Istorija i artikli ({supplierProducts.filter((link) => link.supplierId === supplier.id).length})
                </button>
              </div>
              {catalogSupplierId === supplier.id && (
                <div className="supplier-products-panel">
                  <div className="supplier-products-heading"><strong>Katalog artikala</strong><span>Dobavljačke cene i uslovi poručivanja</span></div>
                  {performanceLoadingId === supplier.id && <p className="supplier-insights-loading" role="status">Učitavanje istorije nabavki…</p>}
                  {performanceErrors[supplier.id] && <p className="form-error" role="alert">{performanceErrors[supplier.id]}<button type="button" className="text-action" onClick={() => void loadPerformance(supplier)}>Pokušaj ponovo</button></p>}
                  {performanceBySupplier[supplier.id] && (
                    <div className="supplier-insights">
                      <div className="supplier-insights-summary">
                        <span><small>Ukupno porudžbina</small><strong>{performanceBySupplier[supplier.id].totalOrders}</strong></span>
                        <span><small>Otvorene</small><strong>{performanceBySupplier[supplier.id].openOrders}</strong></span>
                        <span><small>Isporučeno na vreme*</small><strong>{performanceBySupplier[supplier.id].onTimeDeliveries}</strong></span>
                        <span><small>Kasne isporuke*</small><strong>{performanceBySupplier[supplier.id].lateDeliveries}</strong></span>
                      </div>
                      <p className="supplier-insights-note">* Računa se za porudžbine evidentirane nakon uvođenja praćenja prijema. Cene su iz najviše 100 poslednjih porudžbina.</p>
                      {performanceBySupplier[supplier.id].recentPrices.length > 0 && (
                        <div className="supplier-history-list">
                          <strong>Poslednje zabeležene nabavne cene</strong>
                          {performanceBySupplier[supplier.id].recentPrices.map((price) => (
                            <div className="supplier-product-row" key={price.productId}>
                              <span><strong>{price.productName}</strong><small>{price.sku} · {price.orderNumber}</small></span>
                              <span className="supplier-product-meta">{formatMoney(price.unitCost)} · {new Intl.DateTimeFormat("sr-RS", { dateStyle: "medium" }).format(new Date(price.orderedAt))}</span>
                            </div>
                          ))}
                        </div>
                      )}
                      {performanceBySupplier[supplier.id].recentOrders.length > 0 && (
                        <div className="supplier-history-list">
                          <strong>Poslednje porudžbine</strong>
                          {performanceBySupplier[supplier.id].recentOrders.slice(0, 10).map((order) => (
                            <div className="supplier-product-row" key={order.id}>
                              <span><strong>{order.orderNumber}</strong><small>{order.status === "RECEIVED" && order.lastReceivedAt
                                ? `Primljeno ${new Intl.DateTimeFormat("sr-RS", { dateStyle: "medium" }).format(new Date(order.lastReceivedAt))}`
                                : `${order.status === "DRAFT" ? "Nacrt" : order.status === "ORDERED" ? "Poručeno" : order.status === "PARTIALLY_RECEIVED" ? "Delimično primljeno" : order.status === "CANCELLED" ? "Otkazano" : "Kreirano"} · ${new Intl.DateTimeFormat("sr-RS", { dateStyle: "medium" }).format(new Date(order.createdAt))}`}</small></span>
                              <span className="supplier-product-meta">{formatMoney(order.total)}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                  {catalogError && <p className="form-error" role="alert">{catalogError}</p>}
                  {supplierProducts.filter((link) => link.supplierId === supplier.id).length === 0
                    ? <p className="supplier-catalog-empty">Još nema povezanih artikala.</p>
                    : <div className="supplier-product-list">
                      {supplierProducts.filter((link) => link.supplierId === supplier.id).map((link) => (
                        <div className="supplier-product-row" key={link.id}>
                          <span><strong>{link.productName}</strong><small>{link.productSku}{link.supplierSku ? ` · SKU dobavljača: ${link.supplierSku}` : ""}</small></span>
                          <span className="supplier-product-meta">{formatMoney(link.unitCost)} · min. {link.minimumOrderQuantity}{link.preferredSupplier && <span className="preferred-badge"><Star size={12} /> Primarni</span>}</span>
                          {canManage && <span className="supplier-product-actions">
                            {supplier.active && <button className="icon-action" type="button" onClick={() => setEditingLink(link)} aria-label={`Uredi ${link.productName}`}><Package size={15} /></button>}
                            <button className="icon-action" type="button" onClick={() => void handleDeleteSupplierProduct(link)} aria-label={`Ukloni ${link.productName}`}><Trash2 size={15} /></button>
                          </span>}
                        </div>
                      ))}
                    </div>}
                  {canManage && supplier.active && (
                    <form className="supplier-product-form" key={`${supplier.id}-${editingLink?.productId ?? "new"}`} onSubmit={(event) => void handleSaveSupplierProduct(event, supplier)}>
                      <strong>{editingLink ? `Uređivanje: ${editingLink.productName}` : "Poveži artikal"}</strong>
                      <label className="full-field">Proizvod
                        <select name="productId" defaultValue={editingLink?.productId ?? ""} disabled={Boolean(editingLink)} required>
                          <option value="" disabled>Izaberite proizvod</option>
                          {products.filter((product) => editingLink?.productId === product.id
                            || !supplierProducts.some((link) => link.supplierId === supplier.id && link.productId === product.id))
                            .map((product) => <option key={product.id} value={product.id}>{product.name} · {product.sku}</option>)}
                        </select>
                      </label>
                      <label>SKU dobavljača<input name="supplierSku" maxLength={100} defaultValue={editingLink?.supplierSku ?? ""} /></label>
                      <label>Nabavna cena (RSD)<input name="unitCost" type="number" min="0" step="0.01" required defaultValue={editingLink?.unitCost ?? ""} disabled={!pricesReady} /></label>
                      <label>Minimalna količina<input name="minimumOrderQuantity" type="number" min="1" step="1" required defaultValue={editingLink?.minimumOrderQuantity ?? 1} /></label>
                      <label>Rok isporuke (dani)<input name="leadTimeDays" type="number" min="0" step="1" defaultValue={editingLink?.leadTimeDays ?? ""} placeholder="Koristi rok iz artikla" /></label>
                      <label className="checkbox-field"><input name="preferredSupplier" type="checkbox" defaultChecked={editingLink?.preferredSupplier ?? false} /> Primarni dobavljač</label>
                      <div className="supplier-product-form-actions">
                        {editingLink && <button className="secondary-action" type="button" onClick={() => setEditingLink(null)}>Otkaži izmenu</button>}
                        <button className="primary-action" type="submit" disabled={catalogSaving || !pricesReady || (!editingLink && products.every((product) => supplierProducts.some((link) => link.supplierId === supplier.id && link.productId === product.id)))}>
                          {catalogSaving ? "Čuvanje…" : editingLink ? "Sačuvaj izmene" : "Poveži artikal"}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </article>
          ))}
          {suppliers && filtered.length === 0 && (
            <div className="section-card supplier-empty">
              <Building2 size={25} />
              <strong>{suppliers.length === 0 ? "Još nema dobavljača" : "Nema rezultata"}</strong>
              <p>{suppliers.length === 0 ? "Dodajte dobavljača da biste mogli da pravite porudžbine." : "Promenite pretragu ili uključite neaktivne dobavljače."}</p>
              {suppliers.length === 0 && canManage && <button className="primary-action" type="button" onClick={openCreate}><Plus size={16} /> Dodaj prvog dobavljača</button>}
            </div>
          )}
          {!suppliers && <div className="section-card supplier-empty" role="status">Učitavanje dobavljača…</div>}
        </section>

        {formOpen && (
          <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setFormOpen(false); }}>
            <section className="entry-modal" role="dialog" aria-modal="true" aria-labelledby="supplier-form-title">
              <div className="modal-heading">
                <div><span className="modal-kicker">PARTNER ZA NABAVKU</span><h2 id="supplier-form-title">{editing ? "Uredi dobavljača" : "Novi dobavljač"}</h2></div>
                <button className="close-button" type="button" onClick={() => setFormOpen(false)} aria-label="Zatvori"><X size={18} /></button>
              </div>
              <form onSubmit={handleSave}>
                <div className="form-grid">
                  <label className="full-field">Naziv dobavljača<input name="name" required maxLength={200} defaultValue={editing?.name ?? ""} autoFocus /></label>
                  <label>Email<input name="email" type="email" maxLength={320} defaultValue={editing?.email ?? ""} /></label>
                  <label>Telefon<input name="phone" type="tel" maxLength={50} defaultValue={editing?.phone ?? ""} /></label>
                  <label className="full-field">Napomene<textarea name="notes" maxLength={1000} rows={3} defaultValue={editing?.notes ?? ""} /></label>
                  {editing && <label className="checkbox-field"><input name="active" type="checkbox" defaultChecked={editing.active} /> Dobavljač je aktivan</label>}
                </div>
                {formError && <p className="form-error" role="alert">{formError}</p>}
                <div className="modal-actions"><button className="secondary-action" type="button" onClick={() => setFormOpen(false)}>Otkaži</button><button className="primary-action" type="submit" disabled={saving}>{saving ? "Čuvanje…" : "Sačuvaj dobavljača"}</button></div>
              </form>
            </section>
          </div>
        )}
        <footer className="footer-note"><span>Dobavljači se čuvaju unutar vaše organizacije.</span></footer>
      </div>
    </WorkspaceShell>
  );
}
