import { ReactNode } from 'react';
import { Session } from '../api';
import { WorkspaceRouteId, visibleWorkspaceNavItems } from '../auth/permissions';

export function Shell({
  session,
  onLogout,
  activeRouteId,
  onNavigate,
  children
}: {
  session: Session;
  onLogout: () => void;
  activeRouteId: WorkspaceRouteId;
  onNavigate: (routeId: WorkspaceRouteId) => void;
  children: ReactNode;
}) {
  const navigationItems = visibleWorkspaceNavItems(session);

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <span className="brand-mark">KT</span>
          <div>
            <strong>Agent Console</strong>
            <small>RAG to Ticket to Audit</small>
          </div>
        </div>
        <nav aria-label="工作台导航">
          {navigationItems.map((item) => (
            <a
              key={item.id}
              href={item.path}
              className={`nav-link${item.id === activeRouteId ? ' active' : ''}`}
              aria-current={item.id === activeRouteId ? 'page' : undefined}
              onClick={(event) => {
                event.preventDefault();
                onNavigate(item.id);
              }}
            >
              {item.label}
            </a>
          ))}
        </nav>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Enterprise Gateway</p>
            <h1>知识驱动的工单协同台</h1>
          </div>
          <div className="session-pill">
            <span>{session.displayName}</span>
            <small>{session.roles.join(' / ')}</small>
            <button type="button" onClick={onLogout}>
              退出
            </button>
          </div>
        </header>
        {children}
      </section>
    </main>
  );
}
