"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, Edit2, Package, Plus, Search, X } from "lucide-react";
import { ApiError, createProduct, getCurrentUser, getProducts, updateProduct, type AuthUser, type Product } from "@/lib/api";
import { useCurrency } from "@/components/currency-context";
import WorkspaceShell from "@/components/workspace-shell";

const quantity = new Intl.NumberFormat("sr-RS");

export default function ProductsPage() {
  const router = useRouter();
  const { formatMoney, pricesReady } = useCurrency();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [userLoaded, setUserLoaded] = useState(false);
  const [products, setProducts] = useState<Product[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [showLowStock, setShowLowStock] = useState(false);
  const [categoryFilter, setCategoryFilter] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const canManage = userLoaded && (!user || user.role === "OWNER" || user.role === "ADMIN");

  async function loadProducts() {
    setError(null);
    try {
      const [currentUser, items] = await Promise.all([getCurrentUser(), getProducts()]);
      setUser(currentUser);
      setUserLoaded(true);
      setProducts(items);
    } catch (loadError) {
      if (loadError instanceof ApiError && loadError.status === 401) {
        router.replace("/login?next=%2Fproducts&status=login-required");
      } else {
        setError("Katalog trenutno nije dostupan. Proverite vezu pa pokušajte ponovo.");
      }
    }
  }

  useEffect(() => {
    let active = true;
    Promise.all([getCurrentUser(), getProducts()])
      .then(([currentUser, items]) => {
        if (active) {
          setUser(currentUser);
          setUserLoaded(true);
          setProducts(items);
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2Fproducts&status=login-required");
        } else {
          setError("Katalog trenutno nije dostupan. Proverite vezu pa pokušajte ponovo.");
        }
      });
    return () => { active = false; };
  }, [router]);

  const filteredProducts = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("sr-RS");
    return (products ?? []).filter((product) => {
      const matchesSearch = !term || [product.name, product.sku, product.category ?? ""]
        .some((value) => value.toLocaleLowerCase("sr-RS").includes(term));
      const matchesStock = !showLowStock || product.onHand + product.onOrder < product.minimumStock;
      const matchesCategory = !categoryFilter || product.category === categoryFilter;
      return matchesSearch && matchesStock && matchesCategory;
    });
  }, [categoryFilter, products, search, showLowStock]);

  const categories = [...new Set((products ?? []).map((product) => product.category).filter((value): value is string => Boolean(value)))].sort((a, b) => a.localeCompare(b, "sr"));

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    const values = new FormData(event.currentTarget);
    try {
      const productInput = {
        sku: String(values.get("sku")).trim(),
        name: String(values.get("name")).trim(),
        category: String(values.get("category") ?? "").trim() || null,
        unit: String(values.get("unit")).trim(),
        minimumStock: Number(values.get("minimumStock")),
        leadTimeDays: Number(values.get("leadTimeDays")),
        unitCost: Number(values.get("unitCost")),
      };
      if (editingProduct) {
        await updateProduct(editingProduct.id, productInput);
      } else {
        await createProduct(productInput);
      }
      setFormOpen(false);
      setEditingProduct(null);
      setNotice(editingProduct ? "Podaci o proizvodu su ažurirani." : "Proizvod je dodat u katalog.");
      await loadProducts();
    } catch (saveError) {
      if (saveError instanceof ApiError && saveError.status === 401) {
        router.replace("/login?next=%2Fproducts&status=login-required");
      } else if (saveError instanceof ApiError && saveError.status === 403) {
        setFormError("Vaša uloga nema dozvolu za dodavanje proizvoda.");
      } else {
        setFormError(saveError instanceof Error ? saveError.message : "Proizvod nije sačuvan.");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <WorkspaceShell user={user} active="products">
      <div className="content workspace-content">
        <div className="page-heading">
          <div>
            <div className="eyebrow">KATALOG ZALIHA</div>
            <h1>Proizvodi</h1>
            <p>Pratite stanje, minimalne zalihe i nabavne cene na jednom mestu.</p>
          </div>
          {canManage && (
            <button className="primary-action" type="button" onClick={() => { setEditingProduct(null); setFormError(null); setFormOpen(true); }} disabled={!pricesReady} title={!pricesReady ? "Čeka se konverzija po dostupnom kursu EUR/RSD." : undefined}>
              <Plus size={16} /> Novi proizvod
            </button>
          )}
        </div>

        {notice && <div className="success-banner" role="status">{notice}<button type="button" aria-label="Zatvori poruku" onClick={() => setNotice(null)}><X size={15} /></button></div>}
        {error && <div className="error-banner" role="alert">{error}<button type="button" onClick={() => void loadProducts()}>Pokušaj ponovo</button></div>}

        <section className="catalog-summary" aria-label="Sažetak kataloga">
          <article className="summary-card"><span className="summary-icon"><Package size={18} /></span><span><small>Aktivnih proizvoda</small><strong>{products ? quantity.format(products.length) : "—"}</strong></span></article>
          <article className="summary-card"><span className="summary-icon summary-icon-warning"><AlertTriangle size={18} /></span><span><small>Nedovoljno i sa robom u nabavci</small><strong>{products ? quantity.format(products.filter((product) => product.onHand + product.onOrder < product.minimumStock).length) : "—"}</strong></span></article>
          <article className="summary-card"><span className="summary-icon summary-icon-neutral"><Package size={18} /></span><span><small>Ukupno jedinica</small><strong>{products ? quantity.format(products.reduce((sum, product) => sum + product.onHand, 0)) : "—"}</strong></span></article>
        </section>

        <section className="section-card catalog-card" aria-labelledby="catalog-title">
          <div className="catalog-toolbar">
            <div><h2 id="catalog-title">Katalog proizvoda</h2><p>{products ? `${filteredProducts.length} od ${products.length} proizvoda` : "Učitavanje kataloga"}</p></div>
            <div className="catalog-filters">
              <label className="catalog-search"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Pretraži naziv ili SKU" aria-label="Pretraži proizvode" /></label>
              <label className="stock-filter">Kategorija<select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="">Sve kategorije</option>{categories.map((category) => <option key={category} value={category}>{category}</option>)}</select></label>
              <label className="stock-filter"><input type="checkbox" checked={showLowStock} onChange={(event) => setShowLowStock(event.target.checked)} /> Ispod minimuma</label>
            </div>
          </div>
          <div className="table-wrap">
            <table className="catalog-table">
              <thead><tr><th>PROIZVOD</th><th>KATEGORIJA</th><th>STANJE</th><th>MINIMUM</th><th>ROK ISPORUKE</th><th>NABAVNA CENA</th>{canManage && <th>AKCIJE</th>}</tr></thead>
              <tbody>
                {filteredProducts.map((product) => {
                  const low = product.onHand + product.onOrder < product.minimumStock;
                  return (
                    <tr key={product.id}>
                      <td><div className="product-cell"><span className="product-thumb">{product.name.charAt(0).toUpperCase()}</span><span><strong>{product.name}</strong><small>{product.sku}</small></span></div></td>
                      <td>{product.category || "—"}</td>
                      <td><span className={`stock-pill ${low ? "stock-critical" : "stock-healthy"}`}>{quantity.format(product.onHand)} {product.unit}</span>{product.onOrder > 0 && <small className="stock-on-order">U nabavci: {quantity.format(product.onOrder)} {product.unit}</small>}</td>
                      <td>{quantity.format(product.minimumStock)} {product.unit}</td>
                      <td>{product.leadTimeDays} dana</td>
                      <td className="cost-cell">{formatMoney(product.unitCost)}</td>
                      {canManage && <td><button className="icon-action" type="button" onClick={() => { setEditingProduct(product); setFormError(null); setFormOpen(true); }} aria-label={`Uredi ${product.name}`} title="Uredi proizvod"><Edit2 size={15} /></button></td>}
                    </tr>
                  );
                })}
                {!products && <tr><td className="empty-state" colSpan={canManage ? 7 : 6}>Učitavanje proizvoda…</td></tr>}
                {products && filteredProducts.length === 0 && (
                  <tr><td className="empty-state" colSpan={canManage ? 7 : 6}>
                    {products.length === 0 ? "Katalog je prazan. Dodajte prvi proizvod da biste pratili zalihe." : "Nema proizvoda koji odgovaraju filterima."}
                  </td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {formOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) { setFormOpen(false); setEditingProduct(null); } }}>
          <section className="entry-modal" role="dialog" aria-modal="true" aria-labelledby="product-dialog-title">
            <div className="modal-heading">
              <div><span className="modal-kicker">KATALOG ZALIHA</span><h2 id="product-dialog-title">{editingProduct ? "Uredi proizvod" : "Novi proizvod"}</h2></div>
              <button className="close-button" type="button" onClick={() => { setFormOpen(false); setEditingProduct(null); }} aria-label="Zatvori" disabled={saving}><X size={18} /></button>
            </div>
            <form onSubmit={handleCreate}>
              <div className="form-grid">
                <label>SKU<input name="sku" required maxLength={64} placeholder="npr. COF-002" defaultValue={editingProduct?.sku ?? ""} autoFocus /></label>
                <label>Naziv proizvoda<input name="name" required maxLength={200} placeholder="Naziv proizvoda" defaultValue={editingProduct?.name ?? ""} /></label>
                <label>Kategorija<input name="category" maxLength={100} placeholder="Kategorija" defaultValue={editingProduct?.category ?? ""} /></label>
                <label>Jedinica<input name="unit" required maxLength={16} defaultValue={editingProduct?.unit ?? "kom"} /></label>
                <label>Minimalna zaliha<input name="minimumStock" type="number" min="0" required defaultValue={editingProduct?.minimumStock ?? 5} /></label>
                <label>Rok isporuke (dani)<input name="leadTimeDays" type="number" min="0" required defaultValue={editingProduct?.leadTimeDays ?? 5} /></label>
                <label className="full-field">Nabavna cena (RSD)<input name="unitCost" type="number" min="0" step="0.01" required defaultValue={editingProduct?.unitCost ?? 0} disabled={!pricesReady} /></label>
              </div>
              {formError && <p className="form-error" role="alert">{formError}</p>}
              <div className="modal-actions">
                <button type="button" className="secondary-action" onClick={() => { setFormOpen(false); setEditingProduct(null); }} disabled={saving}>Otkaži</button>
                <button type="submit" className="primary-action" disabled={saving || !pricesReady}>{saving ? "Čuvanje…" : editingProduct ? "Sačuvaj izmene" : "Sačuvaj proizvod"}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </WorkspaceShell>
  );
}
