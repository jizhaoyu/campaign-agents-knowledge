import { FormEvent } from 'react';

export function LoginPage({ onLogin }: { onLogin: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <main className="login-shell">
      <section className="login-panel">
        <div>
          <p className="eyebrow">Knowledge Ticket Agent</p>
          <h1>企业知识库到工单闭环的 AI 工作台</h1>
          <p className="muted">简洁版先跑通登录、上传文档、知识问答、工单草稿、审批和审计回看。</p>
        </div>
        <form className="login-card" onSubmit={onLogin}>
          <label htmlFor="username">账号</label>
          <input id="username" name="username" defaultValue="admin" autoComplete="username" required />
          <label htmlFor="password">密码</label>
          <input
            id="password"
            name="password"
            type="password"
            defaultValue="admin123"
            autoComplete="current-password"
            required
          />
          <button type="submit" className="primary-action">
            进入工作台
          </button>
          <p className="hint">可用账号：admin / user / support / approver</p>
        </form>
      </section>
    </main>
  );
}
