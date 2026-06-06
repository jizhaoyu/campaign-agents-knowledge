import { useState, useTransition } from 'react';
import { Session } from '../api';
export { hasAnyPermission, hasAnyRole, hasPermission, hasRole } from '../auth/permissions';

const STORAGE_KEY = 'kta-session';

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string');
}

export function parseStoredSession(value: string | null): Session | null {
  if (!value) {
    return null;
  }
  try {
    const parsed = JSON.parse(value) as Partial<Session>;
    if (
      typeof parsed.accessToken !== 'string' ||
      typeof parsed.refreshToken !== 'string' ||
      typeof parsed.expiresIn !== 'number' ||
      typeof parsed.refreshExpiresIn !== 'number' ||
      typeof parsed.username !== 'string' ||
      typeof parsed.displayName !== 'string' ||
      !isStringArray(parsed.roles) ||
      (parsed.permissions !== undefined && !isStringArray(parsed.permissions))
    ) {
      return null;
    }
    return parsed as Session;
  } catch {
    return null;
  }
}

export function useSession() {
  const [isPending, startTransition] = useTransition();
  const [session, setSession] = useState<Session | null>(() => {
    const sessionFromStorage = parseStoredSession(localStorage.getItem(STORAGE_KEY));
    if (!sessionFromStorage) {
      localStorage.removeItem(STORAGE_KEY);
    }
    return sessionFromStorage;
  });

  function saveSession(nextSession: Session) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
  }

  function logout() {
    localStorage.removeItem(STORAGE_KEY);
    startTransition(() => {
      setSession(null);
    });
  }

  return { session, saveSession, logout, isPending };
}
