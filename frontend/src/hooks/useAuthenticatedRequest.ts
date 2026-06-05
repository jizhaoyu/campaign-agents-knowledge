import { useRef } from 'react';
import { ApiError, isUnauthorizedError, request, Session } from '../api';
import { refreshLoginSession } from '../services/workspaceApi';

export function useAuthenticatedRequest({
  session,
  saveSession
}: {
  session: Session | null;
  saveSession: (nextSession: Session) => void;
}) {
  const refreshPromiseRef = useRef<Promise<Session> | null>(null);
  const token = session?.accessToken;

  async function refreshSession() {
    if (!session?.refreshToken) {
      throw new ApiError('登录态已失效，请重新登录', 401, 'UNAUTHORIZED', undefined, 'auth');
    }
    if (!refreshPromiseRef.current) {
      refreshPromiseRef.current = refreshLoginSession(session.refreshToken)
        .then((result) => {
          saveSession(result.data);
          return result.data;
        })
        .finally(() => {
          refreshPromiseRef.current = null;
        });
    }
    return refreshPromiseRef.current;
  }

  async function authRequest<T>(path: string, options: RequestInit = {}, accessToken = token) {
    if (!accessToken) {
      throw new ApiError('未登录或登录态已失效', 401, 'UNAUTHORIZED', undefined, 'auth');
    }
    try {
      return await request<T>(path, options, accessToken);
    } catch (error) {
      if (!isUnauthorizedError(error)) {
        throw error;
      }
      const refreshedSession = await refreshSession();
      return request<T>(path, options, refreshedSession.accessToken);
    }
  }

  return { authRequest };
}
