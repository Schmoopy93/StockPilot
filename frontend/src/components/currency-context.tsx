"use client";

import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { usePathname } from "next/navigation";
import { ApiError, getCurrencyStatus, refreshCurrencyStatus, type CurrencyStatus } from "@/lib/api";

type DisplayCurrency = "RSD" | "EUR";

type CurrencyContextValue = {
  status: CurrencyStatus | null;
  error: string | null;
  refreshing: boolean;
  displayCurrency: DisplayCurrency;
  pricesReady: boolean;
  formatMoney: (amount: number) => string;
  toggleCurrency: () => void;
  refresh: () => Promise<void>;
};

const CurrencyContext = createContext<CurrencyContextValue | null>(null);
const rsdFormatter = new Intl.NumberFormat("sr-RS", { style: "currency", currency: "RSD" });
const eurFormatter = new Intl.NumberFormat("sr-RS", { style: "currency", currency: "EUR" });

export function CurrencyProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [status, setStatus] = useState<CurrencyStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [preferredCurrency, setPreferredCurrency] = useState<DisplayCurrency>("RSD");

  useEffect(() => {
    if (["/login", "/register", "/forgot-password", "/reset-password", "/verify-email"].includes(pathname)) {
      return;
    }
    let active = true;
    getCurrencyStatus()
      .then((currencyStatus) => {
        if (active) {
          setStatus(currencyStatus);
          setError(null);
        }
      })
      .catch((loadError: unknown) => {
        if (!active) return;
        const message = loadError instanceof ApiError
          ? loadError.message
          : "Nije moguće učitati status valute.";
        setError(message);
      });
    return () => { active = false; };
  }, [pathname]);

  const displayCurrency = status?.legacyPricesConverted
    ? preferredCurrency
    : "EUR";

  const formatMoney = useCallback((amount: number) => {
    if (!status || !Number.isFinite(amount)) return "—";
    if (!status.legacyPricesConverted) return eurFormatter.format(amount);
    if (displayCurrency === "EUR") {
      return status.eurToRsd
        ? eurFormatter.format(amount / status.eurToRsd)
        : "—";
    }
    return rsdFormatter.format(amount);
  }, [displayCurrency, status]);

  const toggleCurrency = useCallback(() => {
    if (!status?.legacyPricesConverted || !status.eurToRsd) return;
    setPreferredCurrency((current) => current === "RSD" ? "EUR" : "RSD");
  }, [status]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      const updatedStatus = await refreshCurrencyStatus();
      if (!status?.legacyPricesConverted && updatedStatus.legacyPricesConverted) {
        window.location.reload();
        return;
      }
      setStatus(updatedStatus);
    } catch (refreshError) {
      setError(refreshError instanceof ApiError
        ? refreshError.message
        : "Besplatni kurs EUR/RSD nije dostupan. Pokušajte ponovo kasnije.");
    } finally {
      setRefreshing(false);
    }
  }, [status]);

  const value = useMemo(() => ({
    status,
    error,
    refreshing,
    displayCurrency,
    pricesReady: status?.legacyPricesConverted ?? false,
    formatMoney,
    toggleCurrency,
    refresh,
  }), [displayCurrency, error, formatMoney, refresh, refreshing, status, toggleCurrency]);

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency() {
  const context = useContext(CurrencyContext);
  if (!context) throw new Error("useCurrency must be used within a CurrencyProvider");
  return context;
}
