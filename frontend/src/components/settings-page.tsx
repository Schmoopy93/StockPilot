"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Building2, KeyRound, ShieldCheck, UserRound } from "lucide-react";
import { ApiError, getCurrentUser, getDashboard, type AuthUser } from "@/lib/api";
import WorkspaceShell from "@/components/workspace-shell";

const roleLabels: Record<AuthUser["role"], string> = {
  OWNER: "Vlasnik naloga",
  ADMIN: "Administrator",
  STAFF: "Zaposleni",
  READ_ONLY: "Samo čitanje",
};

export default function SettingsPage() {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([getCurrentUser(), getDashboard()])
      .then(([currentUser]) => {
        if (active) {
          setUser(currentUser);
          setLoaded(true);
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          router.replace("/login?next=%2Fsettings&status=login-required");
        } else {
          setError("Podaci o nalogu trenutno nisu dostupni.");
          setLoaded(true);
        }
      });
    return () => { active = false; };
  }, [router]);

  return (
    <WorkspaceShell user={user} active="settings">
      <div className="content workspace-content settings-content">
        <div className="page-heading">
          <div><div className="eyebrow">RADNI PROSTOR</div><h1>Podešavanja naloga</h1><p>Pregledajte podatke o nalogu i bezbednosne opcije.</p></div>
        </div>
        {error && <div className="error-banner" role="alert">{error}</div>}
        {!loaded && !error ? <div className="settings-loading" role="status">Učitavanje podataka o nalogu…</div> : (
          <div className="settings-grid">
            <section className="section-card settings-card">
              <div className="settings-card-heading"><span className="settings-card-icon"><UserRound size={18} /></span><div><h2>Lični podaci</h2><p>Podaci povezani sa prijavom.</p></div></div>
              {user ? (
                <dl className="settings-details">
                  <div><dt>Ime i prezime</dt><dd>{user.name}</dd></div>
                  <div><dt>Email adresa</dt><dd>{user.email}<span className="verified-tag"><ShieldCheck size={13} /> Potvrđen</span></dd></div>
                  <div><dt>Uloga</dt><dd>{roleLabels[user.role]}</dd></div>
                </dl>
              ) : (
                <div className="settings-demo-note" role="status">
                  <p>Koristite demo režim. Ulogujte se registrovanim nalogom da biste videli lične podatke.</p>
                  <Link className="secondary-action" href="/login">Prijavite se</Link>
                </div>
              )}
            </section>
            <section className="section-card settings-card">
              <div className="settings-card-heading"><span className="settings-card-icon settings-security-icon"><KeyRound size={18} /></span><div><h2>Bezbednost</h2><p>Čuvajte pristup svom StockPilot nalogu.</p></div></div>
              <div className="settings-action">
                <span><strong>Promena lozinke</strong><small>Poslaćemo vam jednokratni link za promenu.</small></span>
                <Link className="secondary-action" href="/forgot-password">Zatražite promenu</Link>
              </div>
              <div className="settings-security-note"><ShieldCheck size={16} /><span>Sesije se automatski opozivaju nakon promene lozinke.</span></div>
            </section>
            <section className="section-card settings-card organization-card">
              <div className="settings-card-heading"><span className="settings-card-icon settings-org-icon"><Building2 size={18} /></span><div><h2>Organizacija</h2><p>Radni prostor u kom se čuvaju vaši podaci.</p></div></div>
              {user
                ? <div className="organization-name"><span>{user.organizationName.charAt(0).toUpperCase()}</span><strong>{user.organizationName}</strong></div>
                : <p className="settings-demo-note">Demo podaci nisu vezani za organizaciju.</p>}
            </section>
          </div>
        )}
        <footer className="footer-note"><span>© {new Date().getFullYear()} StockPilot</span><span>Vaše zalihe, pod kontrolom.</span></footer>
      </div>
    </WorkspaceShell>
  );
}
