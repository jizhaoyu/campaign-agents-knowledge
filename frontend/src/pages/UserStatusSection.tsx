import { UserAdmin, UserAdminPage } from '../api';
import { CardHeading } from '../components/CardHeading';
import { ListEmpty } from '../components/ListEmpty';
import { PaginationBar } from '../components/PaginationBar';
import { SkeletonBlock } from '../components/SkeletonBlock';

export function UserStatusSection({
  users,
  userPage,
  usersLoading,
  onLoadUsers,
  onChangeUserPage,
  onUnlockUser,
  onRevokeUserTokenSessions
}: {
  users: UserAdmin[];
  userPage: UserAdminPage | null;
  usersLoading: boolean;
  onLoadUsers: () => void;
  onChangeUserPage: (page: number) => void;
  onUnlockUser: (userId: number) => void;
  onRevokeUserTokenSessions: (userId: number) => void;
}) {
  return (
    <article className="card">
      <CardHeading marker="06" title="用户状态" />
      <button type="button" onClick={onLoadUsers}>
        {usersLoading ? '刷新中...' : '刷新用户'}
      </button>
      <PaginationBar label="用户分页" page={userPage} visibleCount={users.length} onChangePage={onChangeUserPage} />
      {usersLoading && !users.length && <SkeletonBlock label="用户列表骨架" lines={4} variant="panel" />}
      <ListEmpty show={!users.length && !usersLoading} text="暂无用户数据" />
      {users.map((user) => (
        <div className="list-item actionable" key={user.id}>
          <strong>
            {user.displayName} / {user.username}
          </strong>
          <span>
            {user.status} / 失败 {user.failedLoginCount} 次 / {user.roles.join(' / ') || '无角色'}
          </span>
          <small>{user.lockedUntil ? `锁定至 ${user.lockedUntil}` : '未临时锁定'}</small>
          <button
            type="button"
            onClick={() => onUnlockUser(user.id)}
            disabled={!user.lockedUntil && user.failedLoginCount === 0}
          >
            解锁
          </button>
          <button type="button" onClick={() => onRevokeUserTokenSessions(user.id)}>
            吊销该用户会话
          </button>
        </div>
      ))}
    </article>
  );
}
