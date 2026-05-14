import { API_BASE, HTTP_REQUEST } from '../config';

export interface LoginResponse {
  userId: string;
}

interface LoginPayload {
  workerCode: string;
  password: string;
}

export async function loginUser(payload: LoginPayload): Promise<LoginResponse> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/auth/login`, {
    method: HTTP_REQUEST.post,
    headers: {
      'Content-Type': HTTP_REQUEST.jsonContentType,
    },
    body: JSON.stringify(payload),
  });

  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`);
  }

  const raw: unknown = await resp.json();
  const rawObj = raw as { userId?: string | number } | null;
  const candidate = rawObj?.userId != null ? String(rawObj.userId).trim() : '';
  if (!candidate) {
    throw new Error('Missing userId in auth response');
  }

  return { userId: candidate };
}
