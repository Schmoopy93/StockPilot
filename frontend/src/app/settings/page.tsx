import type { Metadata } from "next";
import SettingsPage from "@/components/settings-page";

export const metadata: Metadata = {
  title: "Podešavanja naloga | StockPilot",
  description: "Upravljajte podacima i bezbednošću StockPilot naloga.",
};

export default function SettingsRoute() {
  return <SettingsPage />;
}
