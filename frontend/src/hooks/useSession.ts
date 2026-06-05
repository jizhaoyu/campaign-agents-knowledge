import { useState, useTransition } from 'react';
import { Session } from '../api';
export { hasAnyPermission, hasAnyRole, hasPermission, hasRole } from '../auth/permissions';

const STORAGE_KEY = 'kta-session';

export function useSession() {
  const [isPending, startTransition] = useTransition();
  const [session, setSession] = useState<Session | null>(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as Session) : null;
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
