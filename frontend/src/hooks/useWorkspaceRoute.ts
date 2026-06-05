import { useEffect, useState } from 'react';
import {
  WorkspaceRouteId,
  defaultWorkspaceRouteId,
  visibleWorkspaceNavItems,
  workspacePathFor,
  workspaceRouteFromPath
} from '../auth/permissions';
import { Session } from '../api';

function currentRouteId() {
  return workspaceRouteFromPath(window.location.pathname);
}

function replaceRoute(routeId: WorkspaceRouteId) {
  const path = workspacePathFor(routeId);
  if (window.location.pathname !== path) {
    window.history.replaceState(null, '', path);
  }
}

export function useWorkspaceRoute(session: Session | null) {
  const [routeId, setRouteId] = useState<WorkspaceRouteId>(() =>
    typeof window === 'undefined' ? defaultWorkspaceRouteId : currentRouteId()
  );
  const visibleRoutes = visibleWorkspaceNavItems(session);
  const routeVisible = visibleRoutes.some((item) => item.id === routeId);

  useEffect(() => {
    const onPopState = () => setRouteId(currentRouteId());
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    if (!session) {
      return;
    }
    const fallbackRoute = visibleRoutes[0]?.id ?? defaultWorkspaceRouteId;
    const normalizedRoute = routeVisible ? routeId : fallbackRoute;
    if (normalizedRoute !== routeId) {
      setRouteId(normalizedRoute);
    }
    replaceRoute(normalizedRoute);
  }, [routeId, routeVisible, session, visibleRoutes]);

  function navigate(route: WorkspaceRouteId) {
    const path = workspacePathFor(route);
    if (window.location.pathname !== path) {
      window.history.pushState(null, '', path);
    }
    setRouteId(route);
  }

  return {
    routeId,
    routeVisible,
    navigate
  };
}
