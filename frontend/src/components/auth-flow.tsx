"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Boxes, Check, Eye, EyeOff, ShieldCheck, TrendingUp } from "lucide-react";
import {
  ApiError,
  getCurrentUser,
  login,
  register,
  requestPasswordReset,
  resendVerification,
  resetPassword,
  verifyEmail,
} from "@/lib/api";
import { authHref, safeReturnPath } from "@/lib/routes";
import ThemeToggle from "@/components/theme-toggle";

type AuthMode = "login" | "register" | "forgot" | "verify" | "reset";
type AuthErrorMode = AuthMode | "resend";
type CompletionMode = "register" | "forgot" | "resend" | "verify" | "reset";

const pageCopy: Record<AuthMode, { title: string; description: string }> = {
  login: { title: "Dobro došli nazad", description: "Prijavite se u svoj StockPilot radni prostor." },
  register: { title: "Otvorite svoj nalog", description: "Kreirajte organizaciju i počnite da upravljate zalihama." },
  forgot: { title: "Zaboravili ste lozinku?", description: "Poslaćemo vam bezbedan link za postavljanje nove lozinke." },
  verify: { title: "Potvrdite email adresu", description: "Potvrdite adresu da biste mogli da se prijavite." },
  reset: { title: "Postavite novu lozinku", description: "Izaberite novu lozinku za svoj StockPilot nalog." },
};

const loginStatus: Record<string, { message: string; tone: "success" | "info" }> = {
  verified: { message: "Email je potvrđen. Sada možete bezbedno da se prijavite.", tone: "success" },
  "password-reset": { message: "Lozinka je promenjena. Prijavite se novom lozinkom.", tone: "success" },
  "session-expired": { message: "Sesija je istekla. Prijavite se ponovo da biste nastavili.", tone: "info" },
  "login-required": { message: "Prijavite se da biste pristupili ovom delu radnog prostora.", tone: "info" },
  "logged-out": { message: "Uspešno ste se odjavili.", tone: "success" },
};

function friendlyError(error: unknown, mode: AuthErrorMode): string {
  if (error instanceof ApiError) {
    if (error.status === 429) {
      if (!error.retryAfterSeconds) {
        return "Previše pokušaja. Pokušajte ponovo malo kasnije.";
      }
      const wait = error.retryAfterSeconds < 60
        ? `${error.retryAfterSeconds} sek.`
        : `${Math.ceil(error.retryAfterSeconds / 60)} min.`;
      return `Previše pokušaja. Pokušajte ponovo za približno ${wait}`;
    }
    if (error.status === 400) {
      if (mode === "verify") {
        return "Link za potvrdu je istekao, neispravan ili je već iskorišćen. Zatražite novi link.";
      }
      if (mode === "reset") {
        if (/token|expired|invalid/i.test(error.message)) {
          return "Link za promenu lozinke je istekao ili je već iskorišćen. Zatražite novi link.";
        }
        if (/72 bajta|72 bytes/i.test(error.message)) {
          return "Lozinka je predugačka. Koristite najviše 72 bajta UTF-8 teksta.";
        }
        return "Unesite novu lozinku od 12 do 128 karaktera i proverite da li je link ispravan.";
      }
      if (mode === "register" && /72 bajta|72 bytes/i.test(error.message)) {
        return "Lozinka je predugačka. Koristite najviše 72 bajta UTF-8 teksta.";
      }
      if (mode === "register") {
        return "Proverite unete podatke. Email, ime i naziv organizacije moraju biti ispravni, a lozinka imati 12–128 karaktera.";
      }
      if (mode === "forgot" || mode === "resend") {
        return "Unesite ispravnu email adresu.";
      }
      return "Proverite email i lozinku pa pokušajte ponovo.";
    }
    if (error.status === 401) {
      return "Email ili lozinka nisu ispravni. Proverite podatke i pokušajte ponovo.";
    }
    if (error.status === 403) {
      if (mode === "login" && /potvrdite email|email.*potvrđen|email.*verified/i.test(error.message)) {
        return "Ovaj nalog još nije verifikovan. Potvrdite email adresu da biste mogli da se prijavite.";
      }
      return "Bezbednosna provera nije uspela. Osvežite stranicu i pokušajte ponovo.";
    }
    if (error.status === 404) {
      return "Auth servis nije dostupan. Proverite da li je aplikacija pokrenuta i pokušajte ponovo.";
    }
    if (error.status === 409 && mode === "register") {
      return "Nalog sa ovom email adresom već postoji. Prijavite se ili zatražite novu lozinku.";
    }
    if (error.status === 413) return "Zahtev je prevelik. Proverite unete podatke.";
    if (error.status === 415) return "Zahtev nije u ispravnom formatu. Osvežite stranicu i pokušajte ponovo.";
    if (error.status === 422) return "Neki podaci nisu ispravni. Proverite polja i pokušajte ponovo.";
    if (error.status >= 500) {
      return "Došlo je do greške na serveru. Pokušajte ponovo za nekoliko trenutaka.";
    }
    return "Zahtev nije uspeo. Proverite podatke i pokušajte ponovo.";
  }
  return "Trenutno nije moguće povezati se sa servisom. Proverite vezu i pokušajte ponovo.";
}

function isUnverifiedAccount(error: unknown): error is ApiError {
  return error instanceof ApiError
    && error.status === 403
    && /potvrdite email|email.*potvrđen|email.*verified/i.test(error.message);
}

export default function AuthFlow({
  mode,
  token = "",
  nextPath = "/",
  status = "",
}: {
  mode: AuthMode;
  token?: string;
  nextPath?: string;
  status?: string;
}) {
  const router = useRouter();
  const returnTo = safeReturnPath(nextPath);
  const [error, setError] = useState<string | null>(null);
  const [actionStatus, setActionStatus] = useState<string | null>(null);
  const routeStatus = loginStatus[status];
  const statusMessage = actionStatus ?? routeStatus?.message ?? null;
  const statusTone = actionStatus ? "info" : routeStatus?.tone ?? "info";
  const [completion, setCompletion] = useState<{ mode: CompletionMode; message: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showResendForm, setShowResendForm] = useState(mode === "verify" && !token);
  const [verificationEmail, setVerificationEmail] = useState("");
  const [verificationRequired, setVerificationRequired] = useState(false);

  useEffect(() => {
    if (mode !== "login") return;
    let active = true;
    getCurrentUser()
      .then((user) => {
        if (active && user) router.replace(returnTo);
      })
      .catch((requestError: unknown) => {
        if (active && !(requestError instanceof ApiError && requestError.status === 401)) {
          setError("Nije moguće proveriti korisničku sesiju. Pokušajte ponovo.");
        }
      });
    return () => { active = false; };
  }, [mode, returnTo, router]);

  async function sendVerification(email: string) {
    setError(null);
    setActionStatus(null);
    setResending(true);
    try {
      const result = await resendVerification(email.trim());
      setActionStatus(result.message);
      setVerificationEmail(email.trim());
    } catch (requestError) {
      setError(friendlyError(requestError, "resend"));
    } finally {
      setResending(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setActionStatus(null);
    setVerificationRequired(false);
    setSubmitting(true);
    const values = new FormData(event.currentTarget);
    const email = String(values.get("email") ?? "").trim();
    const password = String(values.get("password") ?? "");

    try {
      if (mode === "login") {
        await login(email, password);
        router.replace(returnTo);
        router.refresh();
      } else if (mode === "register") {
        const name = String(values.get("name") ?? "").trim();
        const organizationName = String(values.get("organizationName") ?? "").trim();
        const confirmation = String(values.get("confirmPassword") ?? "");
        if (password !== confirmation) {
          setError("Lozinke se ne podudaraju.");
          return;
        }
        const result = await register({ email, name, organizationName, password });
        setVerificationEmail(email);
        setCompletion({ mode: "register", message: result.message });
      } else if (mode === "forgot") {
        const result = await requestPasswordReset(email);
        setCompletion({ mode: "forgot", message: result.message });
      } else if (mode === "verify") {
        if (!token || showResendForm) {
          if (!email) {
            setError("Unesite email adresu naloga.");
            return;
          }
          const result = await resendVerification(email);
          setVerificationEmail(email);
          setCompletion({ mode: "resend", message: result.message });
          return;
        }
        const result = await verifyEmail(token);
        setCompletion({ mode: "verify", message: result.message });
      } else if (mode === "reset") {
        const confirmation = String(values.get("confirmPassword") ?? "");
        if (!token) {
          setError("Link za promenu lozinke nije potpun. Zatražite novi link.");
          return;
        }
        if (password !== confirmation) {
          setError("Lozinke se ne podudaraju.");
          return;
        }
        const result = await resetPassword(token, password);
        setCompletion({ mode: "reset", message: result.message });
      }
    } catch (submitError) {
      if (mode === "login" && isUnverifiedAccount(submitError)) {
        setVerificationEmail(email);
        setVerificationRequired(true);
        setError("Ovaj nalog još nije verifikovan. Potvrdite email adresu da biste mogli da se prijavite.");
      } else {
        const errorMode = mode === "verify" && (!token || showResendForm) ? "resend" : mode;
        setError(friendlyError(submitError, errorMode));
        if (mode === "verify" && submitError instanceof ApiError && submitError.status === 400) {
          setShowResendForm(true);
        }
      }
    } finally {
      setSubmitting(false);
    }
  }

  const copy = pageCopy[mode];
  const passwordMode = mode === "login" || mode === "register" || mode === "reset";
  const completionAction = completion?.mode === "register"
    ? "Proverite prijemno sanduče i neželjenu poštu. Link za potvrdu važi ograničeno vreme."
    : completion?.mode === "forgot"
      ? "Ako email odgovara aktivnom nalogu, stići će vam poruka sa bezbednim linkom."
      : completion?.mode === "resend"
        ? "Ako nalog postoji i email nije potvrđen, poslaćemo uputstvo za potvrdu."
      : completion?.mode === "verify"
        ? "Email adresa je potvrđena. Sada možete da se prijavite."
        : completion?.mode === "reset"
          ? "Nova lozinka je spremna. Prijavite se sa novim podacima."
          : "";

  return (
    <main className="auth-page">
      <div className="auth-theme-toggle"><ThemeToggle /></div>
      <aside className="auth-showcase">
        <Link className="auth-brand" href="/login" aria-label="StockPilot">
          <span className="auth-brand-mark"><Boxes size={21} /></span>
          <span>stock<span>pilot</span></span>
        </Link>
        <div className="auth-showcase-content">
          <span className="auth-showcase-kicker">PAMETNIJE UPRAVLJANJE ZALIHAMA</span>
          <h2>Prava roba.<br /><span>U pravo vreme.</span></h2>
          <p>Jasan pregled zaliha i preporuke za nabavku, bez nagađanja i ručnog prebrojavanja.</p>
          <div className="auth-highlight">
            <span className="auth-highlight-icon"><TrendingUp size={19} /></span>
            <span><strong>Kontrola na jednom mestu</strong><small>Od stanja zaliha do sledeće porudžbine.</small></span>
          </div>
          <ul className="auth-benefits">
            <li><Check size={15} /> Pregled zaliha i kretanja</li>
            <li><Check size={15} /> Pravovremene preporuke za nabavku</li>
          </ul>
        </div>
        <div className="auth-showcase-footer"><ShieldCheck size={15} /> Vaši poslovni podaci ostaju zaštićeni.</div>
      </aside>

      <section className="auth-panel">
        <Link className="auth-brand auth-panel-brand" href="/login" aria-label="StockPilot">
          <span className="auth-brand-mark"><Boxes size={21} /></span>
          <span>stock<span>pilot</span></span>
        </Link>
        <section className="auth-card" aria-labelledby="auth-title">
          <div className="auth-eyebrow">STOCKPILOT · PAMETNA NABAVKA</div>
          <h1 id="auth-title">{copy.title}</h1>
          <p className="auth-description">{copy.description}</p>

          {statusMessage && (
            <div className={`auth-status auth-status-${statusTone}`} role="status" aria-live="polite">
              {statusMessage}
            </div>
          )}

          {completion ? (
            <div className="auth-completion" role="status" aria-live="polite">
              <span className="auth-completion-icon"><Check size={20} /></span>
              <h2>{completion.mode === "register" ? "Proverite svoj email" :
                completion.mode === "forgot" ? "Proverite prijemno sanduče" :
                  completion.mode === "resend" ? "Proverite svoj email" :
                  completion.mode === "verify" ? "Email je potvrđen" : "Lozinka je promenjena"}</h2>
              <p>{completion.message}</p>
              <small>{completionAction}</small>
              {completion.mode === "register" && verificationEmail && (
                <button
                  className="auth-text-button"
                  type="button"
                  onClick={() => void sendVerification(verificationEmail)}
                  disabled={resending}
                >
                  {resending ? "Slanje…" : "Pošalji ponovo email za potvrdu"}
                </button>
              )}
              <Link className="auth-submit" href={authHref("/login", returnTo)}>
                {completion.mode === "register" ? "Nastavi na prijavu" : "Nazad na prijavu"}
              </Link>
            </div>
          ) : (
            <form className="auth-form" method="post" onSubmit={handleSubmit} aria-busy={submitting}>
              {mode === "register" && (
                <>
                  <label>Ime i prezime<input name="name" autoComplete="name" maxLength={120} required /></label>
                  <label>Naziv organizacije<input name="organizationName" autoComplete="organization" maxLength={200} required /></label>
                </>
              )}
              {(mode === "login" || mode === "register" || mode === "forgot" ||
                (mode === "verify" && showResendForm)) && (
                  <label>Email adresa<input name="email" type="email" autoComplete="email" maxLength={320} required /></label>
                )}
              {passwordMode && (
                <label>
                  Lozinka
                  <span className="auth-password-field">
                    <input
                      name="password"
                      type={showPassword ? "text" : "password"}
                      autoComplete={mode === "login" ? "current-password" : "new-password"}
                      minLength={mode === "login" ? undefined : 12}
                      maxLength={128}
                      required
                    />
                    <button
                      className="auth-password-toggle"
                      type="button"
                      aria-label={showPassword ? "Sakrij lozinku" : "Prikaži lozinku"}
                      aria-pressed={showPassword}
                      onClick={() => setShowPassword((visible) => !visible)}
                    >
                      {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                    </button>
                  </span>
                  {mode !== "login" && <small className="auth-field-hint">Najmanje 12 karaktera.</small>}
                </label>
              )}
              {(mode === "register" || mode === "reset") && (
                <label>
                  Potvrdite lozinku
                  <input name="confirmPassword" type={showPassword ? "text" : "password"} autoComplete="new-password" minLength={12} maxLength={128} required />
                </label>
              )}

              {error && <p className="auth-error" role="alert">{error}</p>}
              {mode === "login" && verificationRequired && verificationEmail && (
                <div className="auth-verification-prompt" aria-live="polite">
                  <p>Potvrdite adresu <strong>{verificationEmail}</strong> da biste pristupili nalogu. Link nije stigao ili je istekao?</p>
                  <button
                    className="auth-text-button"
                    type="button"
                    onClick={() => void sendVerification(verificationEmail)}
                    disabled={resending}
                  >
                    {resending ? "Slanje…" : "Pošalji novi link za potvrdu"}
                  </button>
                </div>
              )}
              <button className="auth-submit" type="submit" disabled={submitting}>
                {submitting ? "Sačekajte…" : mode === "login" ? "Prijavite se" :
                  mode === "register" ? "Kreirajte nalog" :
                    mode === "forgot" ? "Pošaljite link" :
                      mode === "verify" ? showResendForm ? "Pošaljite novi link" : "Potvrdite email" :
                        "Sačuvajte novu lozinku"}
              </button>
            </form>
          )}

          <nav className="auth-links" aria-label="Opcije naloga">
            {mode === "login" && <>
              <Link href={authHref("/forgot-password", returnTo)}>Zaboravili ste lozinku?</Link>
              <span>Nemate nalog? <Link href={authHref("/register", returnTo)}>Registrujte se</Link></span>
            </>}
            {mode === "register" && <span>Već imate nalog? <Link href={authHref("/login", returnTo)}>Prijavite se</Link></span>}
            {mode === "forgot" && <span>Setili ste se lozinke? <Link href={authHref("/login", returnTo)}>Nazad na prijavu</Link></span>}
            {(mode === "verify" || mode === "reset") && <span><Link href={authHref("/login", returnTo)}>Nazad na prijavu</Link></span>}
          </nav>
        </section>
        <p className="auth-footer">Vaše zalihe, pod kontrolom.</p>
      </section>
    </main>
  );
}
