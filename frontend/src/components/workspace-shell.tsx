"use client";

import { ReactNode, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Boxes,
  ClipboardCheck,
  ClipboardList,
  Banknote,
  Euro,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  RefreshCw,
  Store,
  Settings2,
  X,
} from "lucide-react";
import { logout, type AuthUser } from "@/lib/api";
import { useCurrency } from "@/components/currency-context";
import ThemeToggle from "@/components/theme-toggle";

type WorkspaceSection = "dashboard" | "products" | "purchasing" | "orders" | "suppliers" | "movements" | "settings";

const sectionLabels: Record<WorkspaceSection, string> = {
  dashboard: "Pregled",
  products: "Proizvodi",
  purchasing: "Nabavka",
  orders: "Porudžbine",
  suppliers: "Dobavljači",
  movements: "Kretanja robe",
  settings: "Podešavanja",
};

const roleLabels: Record<AuthUser["role"], string> = {
  OWNER: "Vlasnik naloga",
  ADMIN: "Administrator",
  STAFF: "Zaposleni",
  READ_ONLY: "Samo čitanje",
};

export default function WorkspaceShell({
  user,
  active,
  children,
}: {
  user: AuthUser | null;
  active: WorkspaceSection;
  children: ReactNode;
}) {
  return <WorkspaceFrame user={user} active={active}>{children}</WorkspaceFrame>;
}

function WorkspaceFrame({
  user,
  active,
  children,
}: {
  user: AuthUser | null;
  active: WorkspaceSection;
  children: ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const { status, error: currencyError, refreshing, displayCurrency, pricesReady, toggleCurrency, refresh } = useCurrency();
  const initials = user?.name.trim().split(/\s+/).slice(0, 2)
    .map((part) => part.charAt(0)).join("").toUpperCase() ?? "D";
  const rateDate = status?.rateDate
    ? new Intl.DateTimeFormat("sr-RS").format(new Date(`${status.rateDate}T12:00:00`))
    : null;
  const rate = status?.eurToRsd?.toLocaleString("sr-RS", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  });
  const canRefreshCurrency = !user || user.role === "OWNER" || user.role === "ADMIN";
  async function handleLogout() {
    setLogoutError(null);
    try {
      await logout();
      router.replace("/login?status=logged-out");
      router.refresh();
    } catch {
      setLogoutError("Odjava nije uspela. Proverite vezu i pokušajte ponovo.");
    }
  }

  function closeMenu() {
    setMenuOpen(false);
  }

  return (
    <div className="app-shell">
      {menuOpen && <button className="sidebar-scrim" aria-label="Zatvori meni" onClick={closeMenu} />}
      <aside className={`sidebar${menuOpen ? " sidebar-open" : ""}`}>
        <Link className="brand" href="/" aria-label="StockPilot početna" onClick={closeMenu}>
          <span className="brand-mark"><Boxes size={21} /></span>
          <span>stock<span className="brand-accent">pilot</span></span>
        </Link>
        <div className="workspace-label">ORGANIZACIJA</div>
        <div className="workspace-switch" aria-label="Aktivna organizacija">
          <span className="workspace-avatar">{user?.organizationName.charAt(0).toUpperCase() ?? "D"}</span>
          <span className="workspace-copy">
            <strong>{user?.organizationName ?? "Demo prostor"}</strong>
            <small>{user ? "Vaš radni prostor" : "Demo režim"}</small>
          </span>
        </div>
        <div className="nav-label">MENI</div>
        <nav className="nav-list" aria-label="Glavna navigacija">
          <Link className={`nav-item${active === "dashboard" ? " active" : ""}`} href="/" aria-current={active === "dashboard" ? "page" : undefined} onClick={closeMenu}>
            <LayoutDashboard size={18} /> Pregled
          </Link>
          <Link className={`nav-item${active === "products" ? " active" : ""}`} href="/products" aria-current={active === "products" ? "page" : undefined} onClick={closeMenu}>
            <Package size={18} /> Proizvodi
          </Link>
          <Link className={`nav-item${active === "purchasing" ? " active" : ""}`} href="/purchasing" aria-current={active === "purchasing" ? "page" : undefined} onClick={closeMenu}>
            <ClipboardList size={18} /> Nabavka
          </Link>
          <Link className={`nav-item${active === "orders" ? " active" : ""}`} href="/orders" aria-current={active === "orders" ? "page" : undefined} onClick={closeMenu}>
            <ClipboardCheck size={18} /> Porudžbine
          </Link>
          <Link className={`nav-item${active === "suppliers" ? " active" : ""}`} href="/suppliers" aria-current={active === "suppliers" ? "page" : undefined} onClick={closeMenu}>
            <Store size={18} /> Dobavljači
          </Link>
          <Link className={`nav-item${active === "movements" ? " active" : ""}`} href="/movements" aria-current={active === "movements" ? "page" : undefined} onClick={closeMenu}>
            <Boxes size={18} /> Kretanja robe
          </Link>
        </nav>
        <div className="sidebar-bottom">
          <Link className={`nav-item settings-link${active === "settings" ? " active" : ""}`} href="/settings" aria-current={active === "settings" ? "page" : undefined} onClick={closeMenu}>
            <Settings2 size={18} /> Podešavanja
          </Link>
          <div className="profile">
            <span className="profile-avatar">{initials}</span>
            <span className="profile-copy">
              <strong>{user?.name ?? "Demo korisnik"}</strong>
              <small>{user ? roleLabels[user.role] : "Demo režim"}</small>
            </span>
            {user
              ? <button className="logout-button" onClick={handleLogout} aria-label="Odjavi se" title="Odjavi se"><LogOut size={15} /></button>
              : <Link className="profile-login" href="/login">Prijava</Link>}
          </div>
        </div>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <button
            className="mobile-menu-toggle"
            type="button"
            aria-label={menuOpen ? "Zatvori meni" : "Otvori meni"}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            {menuOpen ? <X size={19} /> : <Menu size={19} />}
          </button>
          <div className="breadcrumbs">
            StockPilot <span>/</span> <strong>{sectionLabels[active]}</strong>
          </div>
          <div className="topbar-actions">
            {pricesReady && status?.eurToRsd && (
              <button
                className="currency-toggle"
                type="button"
                onClick={toggleCurrency}
                aria-label={`Promeni prikaz valute na ${displayCurrency === "RSD" ? "EUR" : "RSD"}`}
                title={`Prikaži cene u ${displayCurrency === "RSD" ? "EUR" : "RSD"}`}
              >
                {displayCurrency === "RSD" ? <Euro size={19} /> : <Banknote size={19} />}
              </button>
            )}
            <ThemeToggle />
          </div>
        </header>
        <div className={`currency-status${!pricesReady ? " currency-status-pending" : ""}${currencyError ? " currency-status-error" : ""}`} role={currencyError || !pricesReady ? "status" : "note"}>
          <span>
            {currencyError
              ? `Besplatni kurs EUR/RSD nije dostupan: ${currencyError}`
              : !status
                ? "Učitavanje valutnog statusa…"
                : !pricesReady
                  ? "Postojeće cene su u EUR. Novi unosi cena su pauzirani dok ne stigne važeći kurs EUR/RSD."
                  : rate && rateDate
                    ? `Informativni, nezagarantovani kurs EUR/RSD: 1 EUR = ${rate} RSD · kurs od ${rateDate}`
                    : "Referentni kurs EUR/RSD trenutno nije dostupan."}
          </span>
          {canRefreshCurrency && (
            <button type="button" onClick={() => void refresh()} disabled={refreshing}>
              <RefreshCw size={14} className={refreshing ? "currency-refreshing" : undefined} />
              {refreshing ? "Osvežavanje…" : "Osveži kurs"}
            </button>
          )}
        </div>
        {logoutError && <div className="shell-error" role="alert">{logoutError}</div>}
        <div key={pathname} className="workspace-route">{children}</div>
      </main>
    </div>
  );
}
