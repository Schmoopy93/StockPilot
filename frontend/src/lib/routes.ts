const LOCAL_ORIGIN = "https://stockpilot.invalid";

export function safeReturnPath(value?: string): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return "/";
  }

  try {
    const url = new URL(value, LOCAL_ORIGIN);
    if (url.origin !== LOCAL_ORIGIN) return "/";
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return "/";
  }
}

export function authHref(path: string, returnTo?: string): string {
  const safePath = safeReturnPath(returnTo);
  return safePath === "/" ? path : `${path}?next=${encodeURIComponent(safePath)}`;
}

export function queryValue(value?: string | string[]): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
