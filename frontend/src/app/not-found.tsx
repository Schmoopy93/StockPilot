import Link from "next/link";
import { Boxes, MoveLeft } from "lucide-react";

export default function NotFound() {
  return (
    <main className="not-found-page">
      <Link className="not-found-brand" href="/" aria-label="StockPilot početna">
        <span><Boxes size={20} /></span>
        <strong>stock<span>pilot</span></strong>
      </Link>
      <section className="not-found-card">
        <span className="not-found-code">404 · STRANICA NIJE PRONAĐENA</span>
        <h1>Ovde još nema zaliha.</h1>
        <p>Adresa možda nije tačna ili je stranica premeštena. Vratite se na pregled radnog prostora.</p>
        <Link className="primary-action" href="/"><MoveLeft size={15} /> Nazad na pregled</Link>
      </section>
    </main>
  );
}
