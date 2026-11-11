"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ArrowDownLeft, ArrowUpRight, Boxes, ChevronLeft, ChevronRight, Download, Plus, RefreshCw, X,
} from "lucide-react";
import {
  ApiError, exportMovementHistoryCsv, getCurrentUser, getMovementHistory, getProducts, recordMovement,
  type AuthUser, type Movement, type PageResponse, type Product,
} from "@/lib/api";
import WorkspaceShell from "@/components/workspace-shell";

const quantity = new Intl.NumberFormat("sr-RS");

const typeLabels: Record<Movement["type"], string> = {
  INBOUND: "Prijem robe",
  OUTBOUND: "Izlaz robe",
  ADJUSTMENT: "Korekcija stanja",
};

function movementAmount(movement: Movement) {
  const amount = movement.type === "OUTBOUND" ? -movement.quantity : movement.quantity;
  return `${amount > 0 ? "+" : ""}${quantity.format(amount)}`;
}

function movementError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Proverite količinu: prijem i izlaz moraju biti pozitivni, a korekcija različita od nule.";
    }
    if (error.status === 404) return "Proizvod nije pronađen. Osvežite katalog i pokušajte ponovo.";
    if (error.status === 409) return "Nema dovoljno zaliha za ovu promenu. Osvežite stanje i proverite količinu.";
    if (error.status === 403) return "Vaša uloga nema dozvolu za evidentiranje kretanja.";
  }
  return error instanceof Error ? error.message : "Evidentiranje kretanja nije uspelo.";
}

export default function MovementHistoryPage() {
  const router = useRouter();
  const requestSequence = useRef(0);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [userLoaded, setUserLoaded] = useState(false);
  const [products, setProducts] = useState<Product[]>([]);
  const [history, setHistory] = useState<PageResponse<Movement> | null>(null);
  const [productId, setProductId] = useState("");
  const [type, setType] = useState<Movement["type"] | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [movementProductId, setMovementProductId] = useState("");
  const [movementType, setMovementType] = useState<Movement["type"]>("INBOUND");
  const [movementQuantity, setMovementQuantity] = useState("1");
  const [movementNote, setMovementNote] = useState("");
  const canRecord = userLoaded && (!user || user.role !== "READ_ONLY");
  const selectedProduct = products.find((product) => product.id === movementProductId);

  const load = useCallback(async () => {
    const requestId = ++requestSequence.current;
    if (from && to && from > to) {
      setHistory(null);
      setLoading(false);
      setError("Početni datum ne može biti posle završnog datuma.");
      return;
    }
    setLoading(true);
    setError(null);
    setHistory(null);
    try {
      const result = await getMovementHistory({
        productId: productId || undefined,
        type: type || undefined,
        from: from || undefined,
        to: to || undefined,
        page,
        size: 25,
      });
      if (requestId === requestSequence.current) setHistory(result);
    } catch (loadError) {
      if (requestId !== requestSequence.current) return;
      if (loadError instanceof ApiError && loadError.status === 401) {
        router.replace("/login?next=%2Fmovements&status=login-required");
      } else {
        setError("Istorija kretanja nije dostupna. Proverite filtere ili pokušajte ponovo.");
      }
    } finally {
      if (requestId === requestSequence.current) setLoading(false);
    }
  }, [from, page, productId, router, to, type]);

  useEffect(() => {
    let active = true;
    Promise.all([getCurrentUser(), getProducts()])
      .then(([currentUser, items]) => {
        if (active) {
          setUser(currentUser);
          setUserLoaded(true);
          setProducts(items);
          setCatalogError(null);
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2Fmovements&status=login-required");
        } else {
          setCatalogError("Katalog proizvoda nije dostupan. Osvežite stranicu i pokušajte ponovo.");
        }
      });
    return () => { active = false; };
  }, [router, reloadKey]);

  useEffect(() => {
    void Promise.resolve().then(load);
    return () => { requestSequence.current += 1; };
  }, [load, reloadKey]);

  async function submitMovement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const amount = Number(movementQuantity);
    if (!selectedProduct || !Number.isSafeInteger(amount) || amount === 0
      || (movementType !== "ADJUSTMENT" && amount < 1)) {
      setFormError("Unesite ceo broj. Prijem i izlaz moraju biti veći od nule, a korekcija ne može biti nula.");
      return;
    }
    const stockChange = movementType === "OUTBOUND" ? -amount : amount;
    if (selectedProduct.onHand + stockChange < 0) {
      setFormError(`Kretanje bi spustilo stanje ispod nule. Dostupno: ${quantity.format(selectedProduct.onHand)} ${selectedProduct.unit}.`);
      return;
    }
    if (movementType === "ADJUSTMENT" && !movementNote.trim()) {
      setFormError("Unesite razlog korekcije radi evidencije zaliha.");
      return;
    }

    setSaving(true);
    setFormError(null);
    setError(null);
    try {
      await recordMovement({
        productId: selectedProduct.id,
        type: movementType,
        quantity: amount,
        note: movementNote.trim(),
      });
      setNotice(`${typeLabels[movementType]} je evidentiran za ${selectedProduct.name}.`);
      setFormOpen(false);
      setMovementQuantity("1");
      setMovementNote("");
      try {
        const updatedProducts = await getProducts();
        setProducts(updatedProducts);
        await load();
      } catch {
        setError("Kretanje je sačuvano, ali podaci nisu mogli da se osveže. Pritisnite „Osveži“.");
      }
    } catch (saveError) {
      setFormError(movementError(saveError));
    } finally {
      setSaving(false);
    }
  }

  const dateTime = new Intl.DateTimeFormat("sr-RS", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Europe/Belgrade",
  });
  const activeFilters = Number(Boolean(productId)) + Number(Boolean(type)) + Number(Boolean(from)) + Number(Boolean(to));
  async function exportHistory() {
    setExporting(true);
    setError(null);
    try {
      await exportMovementHistoryCsv({
        productId: productId || undefined,
        type: type || undefined,
        from: from || undefined,
        to: to || undefined,
      });
    } catch (exportError) {
      if (exportError instanceof ApiError && exportError.status === 401) {
        router.replace("/login?next=%2Fmovements&status=session-expired");
      } else {
        setError(exportError instanceof Error ? exportError.message : "Izvoz kretanja nije uspeo.");
      }
    } finally {
      setExporting(false);
    }
  }
  const enteredQuantity = Number(movementQuantity);
  const previewQuantity = selectedProduct && Number.isSafeInteger(enteredQuantity)
    ? selectedProduct.onHand + (movementType === "OUTBOUND" ? -enteredQuantity : enteredQuantity)
    : null;

  return (
    <WorkspaceShell user={user} active="movements">
      <div className="content workspace-content">
        <div className="page-heading">
          <div>
            <div className="eyebrow">PRAĆENJE INVENTARA</div>
            <h1>Kretanja robe</h1>
            <p>Evidentirajte prijem, utrošak i popisne korekcije, pa proverite trag promena zaliha.</p>
          </div>
          {canRecord && (
            <button
              className="primary-action"
              type="button"
              onClick={() => {
                setFormError(null);
                setMovementProductId(products[0]?.id ?? "");
                setFormOpen(true);
              }}
              disabled={products.length === 0}
            >
              <Plus size={16} /> Evidentiraj kretanje
            </button>
          )}
        </div>
        {notice && <div className="success-banner" role="status">{notice}<button type="button" aria-label="Zatvori poruku" onClick={() => setNotice(null)}><X size={15} /></button></div>}
        {catalogError && <div className="error-banner" role="alert">{catalogError}<button type="button" onClick={() => setReloadKey((key) => key + 1)}>Pokušaj ponovo</button></div>}
        {error && <div className="error-banner" role="alert">{error}<button type="button" onClick={() => setReloadKey((key) => key + 1)}>Osveži podatke</button></div>}
        <section className="movement-filter-bar section-card" aria-label="Filteri kretanja">
          <label>Proizvod<select value={productId} onChange={(event) => { setPage(0); setProductId(event.target.value); }}><option value="">Svi proizvodi</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name} · {product.sku}</option>)}</select></label>
          <label>Vrsta kretanja<select value={type} onChange={(event) => { setPage(0); setType(event.target.value as Movement["type"] | ""); }}><option value="">Sve vrste</option><option value="INBOUND">Prijem robe</option><option value="OUTBOUND">Izlaz robe</option><option value="ADJUSTMENT">Korekcija stanja</option></select></label>
          <label>Od datuma<input type="date" value={from} max={to || undefined} onChange={(event) => { setPage(0); setFrom(event.target.value); }} /></label>
          <label>Do datuma<input type="date" value={to} min={from || undefined} onChange={(event) => { setPage(0); setTo(event.target.value); }} /></label>
          <button className="secondary-action" type="button" onClick={() => { setProductId(""); setType(""); setFrom(""); setTo(""); setPage(0); }} disabled={activeFilters === 0}>Očisti filtere</button>
        </section>
        <section className="section-card movement-history-card" aria-labelledby="movement-history-title" aria-busy={loading}>
          <div className="catalog-toolbar">
            <div><h2 id="movement-history-title">Dnevnik kretanja</h2><p>{history ? `${quantity.format(history.totalElements)} evidentiranih promena` : "Učitavanje istorije…"}</p></div>
            <div className="movement-toolbar-actions">
              <button className="secondary-action" type="button" onClick={() => void exportHistory()} disabled={exporting || !history || history.totalElements === 0}>
                <Download size={15} /> {exporting ? "Izvoz…" : "Izvezi CSV"}
              </button>
              <button className="secondary-action" type="button" onClick={() => setReloadKey((key) => key + 1)} disabled={loading} aria-label="Osveži istoriju kretanja">
                <RefreshCw size={15} /> Osveži
              </button>
            </div>
          </div>
          <div className="table-wrap">
            <table className="catalog-table movement-history-table">
              <thead><tr><th>VREME</th><th>PROIZVOD</th><th>VRSTA</th><th>KOLIČINA</th><th>PORUDŽBINA</th><th>NAPOMENA</th></tr></thead>
              <tbody>
                {history?.content.map((movement) => {
                  const product = products.find((item) => item.id === movement.productId);
                  return (
                    <tr key={movement.id}>
                      <td>{dateTime.format(new Date(movement.occurredAt))}</td>
                      <td><div className="product-cell"><span className="product-thumb">{movement.productName.charAt(0).toUpperCase()}</span><span><strong>{movement.productName}</strong>{product && <small>{product.sku}</small>}</span></div></td>
                      <td><span className={`movement-type movement-${movement.type.toLowerCase()}`}>{movement.type === "INBOUND" ? <ArrowDownLeft size={15} /> : movement.type === "OUTBOUND" ? <ArrowUpRight size={15} /> : <Boxes size={15} />}{typeLabels[movement.type]}</span></td>
                      <td><strong className={`movement-amount movement-amount-${movement.type.toLowerCase()}`}>{movementAmount(movement)}</strong></td>
                      <td>{movement.purchaseOrderNumber || "—"}</td>
                      <td>{movement.note || "—"}</td>
                    </tr>
                  );
                })}
                {history?.content.length === 0 && <tr><td className="empty-state" colSpan={6}>{activeFilters ? "Nema kretanja za izabrane filtere. Očistite filtere ili promenite period." : "Nema evidentiranih kretanja. Evidentirajte prijem početnih zaliha da biste započeli praćenje."}</td></tr>}
                {!history && <tr><td className="empty-state" colSpan={6}>{loading ? "Učitavanje istorije kretanja…" : "Istorija trenutno nije dostupna."}</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="movement-pagination">
            <span>{history ? `Strana ${history.number + 1} od ${Math.max(1, history.totalPages)}` : " "}</span>
            <div><button className="secondary-action" type="button" disabled={!history || history.first || loading} onClick={() => setPage((current) => Math.max(0, current - 1))}><ChevronLeft size={16} /> Prethodna</button><button className="secondary-action" type="button" disabled={!history || history.last || loading} onClick={() => setPage((current) => current + 1)}>Sledeća <ChevronRight size={16} /></button></div>
          </div>
        </section>
        <footer className="footer-note"><span>Najnovije kretanje je prikazano prvo. Izlaz se unosi kao pozitivna količina, korekcija kao pozitivna ili negativna razlika.</span></footer>
      </div>
      {formOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setFormOpen(false); }}>
          <section className="entry-modal" role="dialog" aria-modal="true" aria-labelledby="movement-dialog-title">
            <div className="modal-heading">
              <div><span className="modal-kicker">EVIDENCIJA ZALIHA</span><h2 id="movement-dialog-title">Novo kretanje robe</h2></div>
              <button className="close-button" type="button" onClick={() => setFormOpen(false)} aria-label="Zatvori" disabled={saving}><X size={18} /></button>
            </div>
            <form onSubmit={(event) => void submitMovement(event)}>
              <div className="form-grid">
                <label className="full-field">Proizvod
                  <select value={movementProductId} onChange={(event) => setMovementProductId(event.target.value)} required>
                    <option value="" disabled>Izaberite proizvod</option>
                    {products.map((product) => <option key={product.id} value={product.id}>{product.name} · {product.sku} · stanje {quantity.format(product.onHand)} {product.unit}</option>)}
                  </select>
                </label>
                <label>Vrsta kretanja
                  <select value={movementType} onChange={(event) => { setMovementType(event.target.value as Movement["type"]); setFormError(null); }}>
                    <option value="INBOUND">Prijem robe (+)</option>
                    <option value="OUTBOUND">Izlaz / utrošak (−)</option>
                    <option value="ADJUSTMENT">Korekcija stanja (±)</option>
                  </select>
                </label>
                <label>{movementType === "ADJUSTMENT" ? "Promena količine" : "Količina"}
                  <input
                    type="number"
                    step="1"
                    min={movementType === "ADJUSTMENT" ? (selectedProduct ? -selectedProduct.onHand : undefined) : 1}
                    value={movementQuantity}
                    onChange={(event) => setMovementQuantity(event.target.value)}
                    required
                  />
                </label>
                {selectedProduct && (
                  <div className="movement-stock-preview full-field">
                    <span>Trenutno stanje <strong>{quantity.format(selectedProduct.onHand)} {selectedProduct.unit}</strong></span>
                    <span>Posle promene <strong>{previewQuantity === null ? "—" : `${quantity.format(previewQuantity)} ${selectedProduct.unit}`}</strong></span>
                  </div>
                )}
                <label className="full-field">{movementType === "ADJUSTMENT" ? "Razlog korekcije (obavezno)" : "Napomena (opciono)"}
                  <input value={movementNote} onChange={(event) => setMovementNote(event.target.value)} maxLength={300} required={movementType === "ADJUSTMENT"} placeholder={movementType === "ADJUSTMENT" ? "npr. popis, oštećenje, otpis" : movementType === "INBOUND" ? "npr. broj prijemnice ili dobavljača" : "npr. prodaja, utrošak, otpis"} />
                </label>
              </div>
              {formError && <p className="form-error" role="alert">{formError}</p>}
              <div className="modal-actions">
                <button type="button" className="secondary-action" onClick={() => setFormOpen(false)} disabled={saving}>Otkaži</button>
                <button type="submit" className="primary-action" disabled={saving || !movementProductId}>{saving ? "Evidentiranje…" : "Sačuvaj kretanje"}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </WorkspaceShell>
  );
}
