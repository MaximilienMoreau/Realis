const TOKEN_KEY   = "realis_token";
const USER_ID_KEY = "realis_user_id";
const EMAIL_KEY   = "realis_email";

export function saveAuth(token: string, userId: string, email: string): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(TOKEN_KEY,   token);
  localStorage.setItem(USER_ID_KEY, userId);
  localStorage.setItem(EMAIL_KEY,   email);
}

export function getToken(): string | null {
  return typeof window !== "undefined" ? localStorage.getItem(TOKEN_KEY) : null;
}

export function getStoredUser(): { userId: string; email: string } | null {
  if (typeof window === "undefined") return null;
  const userId = localStorage.getItem(USER_ID_KEY);
  const email  = localStorage.getItem(EMAIL_KEY) ?? "";
  return userId ? { userId, email } : null;
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_ID_KEY);
  localStorage.removeItem(EMAIL_KEY);
}

export function isAuthenticated(): boolean {
  return getToken() !== null;
}
