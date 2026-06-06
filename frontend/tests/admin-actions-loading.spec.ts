import { expect, Page, test } from '@playwright/test';

function okResponse(traceId: string, data: unknown) {
  return {
    code: 'OK',
    message: 'success',
    traceId,
    data
  };
}

async function routeAdminWorkspace(page: Page) {
  const counts = {
    approve: 0,
    unlockUser: 0,
    revokeUserSessions: 0,
    revokeSession: 0
  };
  let releaseApproveResponse: (() => void) | null = null;
  let releaseUnlockUserResponse: (() => void) | null = null;
  let releaseRevokeUserSessionsResponse: (() => void) | null = null;
  let releaseRevokeSessionResponse: (() => void) | null = null;

  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-login-admin', {
          accessToken: 'admin-access-token',
          refreshToken: 'admin-refresh-token',
          expiresIn: 7200,
          refreshExpiresIn: 604800,
          username: 'admin',
          displayName: '管理员',
          roles: ['ADMIN'],
          permissions: [
            'knowledge:manage',
            'chat:use',
            'ticket:draft',
            'ticket:submit',
            'ticket:similar:read',
            'approval:review',
            'dashboard:read',
            'audit:read',
            'user:admin',
            'token-session:admin'
          ]
        })
      )
    });
  });

  await page.route('**/api/v1/knowledge-bases', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-kb-empty', []))
    });
  });

  await page.route('**/api/v1/chat/history**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-history-empty', []))
    });
  });

  await page.route('**/api/v1/dashboard/operations', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-dashboard', {
          knowledgeBaseCount: 0,
          documentCount: 0,
          pendingIndexTaskCount: 0,
          runningIndexTaskCount: 0,
          failedIndexTaskCount: 0,
          failedDocumentCount: 0,
          pendingApprovalCount: 1,
          activeHighRiskTicketCount: 0,
          pendingHighRiskTicketCount: 0,
          activeTokenSessionCount: 2,
          totalIndexTaskCount: 0,
          indexFailureRate: 0,
          indexBacklogPressure: 0,
          operationsBacklogCount: 1,
          healthLevel: 'ATTENTION',
          alertCount: 1,
          healthSummary: '存在待审批任务。',
          recommendedActions: ['处理待审批任务。'],
          generatedAt: '2026-06-06T05:00:00'
        })
      )
    });
  });

  await page.route('**/api/v1/approvals/pending', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-approvals', [
          {
            id: 91,
            targetType: 'TICKET_CREATE',
            targetId: 302,
            approverId: 4,
            status: 'PENDING',
            comment: null
          }
        ])
      )
    });
  });

  await page.route('**/api/v1/approvals/comment-templates', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-templates', [
          {
            code: 'APPROVE_EVIDENCE_SUFFICIENT',
            action: 'approve',
            label: '证据充分，批准开单',
            comment: '证据充分，同意开单。'
          }
        ])
      )
    });
  });

  await page.route('**/api/v1/approvals/91/approve', async (route) => {
    counts.approve += 1;
    if (counts.approve === 1) {
      await new Promise<void>((resolve) => {
        releaseApproveResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-approve', {
          id: 91,
          targetType: 'TICKET_CREATE',
          targetId: 302,
          approverId: 4,
          status: 'APPROVED',
          comment: '证据充分，同意开单。'
        })
      )
    });
  });

  await page.route('**/api/v1/users/token-sessions**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname !== '/api/v1/users/token-sessions') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-token-sessions', {
          items: [
            {
              id: 21,
              userId: 1,
              username: 'admin',
              tokenFingerprint: 'abc123def456',
              roleCodes: 'ADMIN',
              issuedAt: '2026-06-06T01:00:00',
              expiresAt: '2026-06-06T03:00:00',
              refreshExpiresAt: '2026-06-13T01:00:00',
              lastRefreshedAt: null,
              revokedAt: null,
              accessTokenActive: true,
              active: true
            }
          ],
          page: 0,
          size: 8,
          totalItems: 1,
          totalPages: 1,
          hasPrevious: false,
          hasNext: false
        })
      )
    });
  });

  await page.route('**/api/v1/users/1/token-sessions/revoke', async (route) => {
    counts.revokeUserSessions += 1;
    if (counts.revokeUserSessions === 1) {
      await new Promise<void>((resolve) => {
        releaseRevokeUserSessionsResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-user-token-sessions-revoke', [
          {
            id: 21,
            userId: 1,
            username: 'admin',
            tokenFingerprint: 'abc123def456',
            roleCodes: 'ADMIN',
            issuedAt: '2026-06-06T01:00:00',
            expiresAt: '2026-06-06T03:00:00',
            refreshExpiresAt: '2026-06-13T01:00:00',
            lastRefreshedAt: null,
            revokedAt: '2026-06-06T05:10:00',
            accessTokenActive: false,
            active: false
          }
        ])
      )
    });
  });

  await page.route('**/api/v1/users/token-sessions/21/revoke', async (route) => {
    counts.revokeSession += 1;
    if (counts.revokeSession === 1) {
      await new Promise<void>((resolve) => {
        releaseRevokeSessionResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-token-session-revoke', {
          id: 21,
          userId: 1,
          username: 'admin',
          tokenFingerprint: 'abc123def456',
          roleCodes: 'ADMIN',
          issuedAt: '2026-06-06T01:00:00',
          expiresAt: '2026-06-06T03:00:00',
          refreshExpiresAt: '2026-06-13T01:00:00',
          lastRefreshedAt: null,
          revokedAt: '2026-06-06T05:12:00',
          accessTokenActive: false,
          active: false
        })
      )
    });
  });

  await page.route('**/api/v1/users/1/unlock', async (route) => {
    counts.unlockUser += 1;
    if (counts.unlockUser === 1) {
      await new Promise<void>((resolve) => {
        releaseUnlockUserResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-user-unlock', {
          id: 1,
          username: 'admin',
          displayName: '系统管理员',
          status: 'ACTIVE',
          failedLoginCount: 0,
          lockedUntil: null,
          roles: ['ADMIN']
        })
      )
    });
  });

  await page.route('**/api/v1/users**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname !== '/api/v1/users') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-users', {
          items: [
            {
              id: 1,
              username: 'admin',
              displayName: '系统管理员',
              status: 'ACTIVE',
              failedLoginCount: 5,
              lockedUntil: '2026-06-06T06:00:00',
              roles: ['ADMIN']
            }
          ],
          page: 0,
          size: 8,
          totalItems: 1,
          totalPages: 1,
          hasPrevious: false,
          hasNext: false
        })
      )
    });
  });

  return {
    releaseApproveResponse: () => releaseApproveResponse?.(),
    releaseUnlockUserResponse: () => releaseUnlockUserResponse?.(),
    releaseRevokeUserSessionsResponse: () => releaseRevokeUserSessionsResponse?.(),
    releaseRevokeSessionResponse: () => releaseRevokeSessionResponse?.(),
    counts: () => ({ ...counts })
  };
}

test('keeps admin side-effect actions single-flight while requests are pending', async ({ page }) => {
  const workspace = await routeAdminWorkspace(page);
  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();

  await page.getByRole('link', { name: '审批' }).click();
  await page.getByRole('button', { name: '刷新待审批' }).click();
  await expect(page.getByText('#91 TICKET_CREATE')).toBeVisible();
  await page.getByLabel('通过模板').selectOption('APPROVE_EVIDENCE_SUFFICIENT');
  await page.getByRole('button', { name: '通过' }).click();
  await expect(page.getByRole('button', { name: '处理中...' })).toBeDisabled();
  await page.getByRole('button', { name: '处理中...' }).click({ force: true });
  await page.getByRole('button', { name: '驳回' }).click({ force: true });
  expect(workspace.counts().approve).toBe(1);
  workspace.releaseApproveResponse();
  await expect(page.getByRole('button', { name: '处理中...' })).toHaveCount(0);

  await page.getByRole('link', { name: '用户' }).click();
  await page.getByRole('button', { name: '刷新用户' }).click();
  await expect(page.getByText('系统管理员 / admin')).toBeVisible();
  await page.getByRole('button', { name: '解锁' }).click();
  await expect(page.getByRole('button', { name: '解锁中...' })).toBeDisabled();
  await page.getByRole('button', { name: '解锁中...' }).click({ force: true });
  expect(workspace.counts().unlockUser).toBe(1);
  workspace.releaseUnlockUserResponse();
  await expect(page.getByText('账号已解锁：admin')).toBeVisible();

  await page.getByRole('button', { name: '吊销该用户会话' }).click();
  await expect(page.getByRole('button', { name: '吊销中...' })).toBeDisabled();
  await page.getByRole('button', { name: '吊销中...' }).click({ force: true });
  expect(workspace.counts().revokeUserSessions).toBe(1);
  workspace.releaseRevokeUserSessionsResponse();
  await expect(page.getByText('用户 Token 会话已批量吊销：1 条')).toBeVisible();

  await page.getByRole('link', { name: '会话' }).click();
  await page.getByRole('button', { name: '刷新会话' }).click();
  await expect(page.getByText('#21 / admin / REFRESHABLE')).toBeVisible();
  await page.getByRole('button', { name: '吊销会话' }).click();
  await expect(page.getByRole('button', { name: '吊销中...' })).toBeDisabled();
  await page.getByRole('button', { name: '吊销中...' }).click({ force: true });
  expect(workspace.counts().revokeSession).toBe(1);
  workspace.releaseRevokeSessionResponse();
  await expect(page.getByText('Token 会话已吊销：#21')).toBeVisible();
});
