import { useAuthStore } from "../stores/auth";

export class ApiError extends Error {
  status: number;
  /** Seconds the server asked us to wait, when it said to retry at all. */
  retryAfter: number | null;

  constructor(status: number, message: string, retryAfter: number | null = null) {
    super(message);
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const auth = useAuthStore();
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");

  if (auth.token) {
    headers.set("Authorization", `Bearer ${auth.token}`);
  }

  const response = await fetch(`/api${path}`, { ...options, headers });

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 401) {
      auth.logout();
    }
    const message = body?.message ?? `Request failed with status ${response.status}`;
    const retryAfter = Number(response.headers.get("Retry-After"));
    throw new ApiError(response.status, message, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null);
  }

  return body as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: "POST", body: data === undefined ? undefined : JSON.stringify(data) }),
  put: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: "PUT", body: data === undefined ? undefined : JSON.stringify(data) }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" })
};
