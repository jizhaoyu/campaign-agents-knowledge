import { TokenSessionAdmin, TokenSessionAdminPage } from '../api';
import { CardHeading } from '../components/CardHeading';
import { ListEmpty } from '../components/ListEmpty';
import { PaginationBar } from '../components/PaginationBar';

export function TokenSessionSection({
  tokenSessions,
  tokenSessionPage,
  onLoadTokenSessions,
  onChangeTokenSessionPage,
  onRevokeTokenSession
}: {
  tokenSessions: TokenSessionAdmin[];
  tokenSessionPage: TokenSessionAdminPage | null;
  onLoadTokenSessions: () => void;
  onChangeTokenSessionPage: (page: number) => void;
  onRevokeTokenSession: (sessionId: number) => void;
}) {
  return (
    <article className="card">
      <CardHeading marker="07" title="Token 会话" />
      <button type="button" onClick={onLoadTokenSessions}>
        刷新会话
      </button>
      <PaginationBar
        label="会话分页"
        page={tokenSessionPage}
        visibleCount={tokenSessions.length}
        onChangePage={onChangeTokenSessionPage}
      />
      <ListEmpty show={!tokenSessions.length} text="暂无会话数据" />
      {tokenSessions.map((session) => (
        <div className="list-item actionable" key={session.id}>
          <strong>
            #{session.id} / {session.username} / {session.active ? 'REFRESHABLE' : 'REVOKED'}
          </strong>
          <span>
            指纹 {session.tokenFingerprint} / Access {session.accessTokenActive ? '有效' : '过期'} / 角色{' '}
            {session.roleCodes || '无'}
          </span>
          <small>
            签发 {session.issuedAt} / Access 过期 {session.expiresAt} / Refresh 过期 {session.refreshExpiresAt}
          </small>
          <small>{session.lastRefreshedAt ? `最近刷新 ${session.lastRefreshedAt}` : '尚未刷新'}</small>
          <button type="button" onClick={() => onRevokeTokenSession(session.id)} disabled={!session.active}>
            吊销会话
          </button>
        </div>
      ))}
    </article>
  );
}
