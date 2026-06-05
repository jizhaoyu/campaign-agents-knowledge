import { expect, test } from '@playwright/test';

test('uses paged admin management requests for audits users and token sessions', async ({ page }) => {
  const auditRequests: string[] = [];
  const userRequests: string[] = [];
  const sessionRequests: string[] = [];

  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-login-admin',
        data: {
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
        }
      })
    });
  });

  await page.route('**/api/v1/knowledge-bases', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-kb-empty',
        data: []
      })
    });
  });

  await page.route('**/api/v1/chat/history**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-history-empty',
        data: []
      })
    });
  });

  await page.route('**/api/v1/dashboard/operations', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-dashboard',
        data: {
          knowledgeBaseCount: 0,
          documentCount: 0,
          pendingIndexTaskCount: 0,
          runningIndexTaskCount: 0,
          failedIndexTaskCount: 0,
          failedDocumentCount: 0,
          pendingApprovalCount: 0,
          activeHighRiskTicketCount: 0,
          pendingHighRiskTicketCount: 0,
          activeTokenSessionCount: 2,
          healthLevel: 'HEALTHY',
          alertCount: 0,
          healthSummary: '当前无待处理运营告警。',
          recommendedActions: ['保持索引队列、审批队列和高风险工单的日常巡检。'],
          generatedAt: '2026-06-06T02:00:00'
        }
      })
    });
  });

  await page.route('**/api/v1/audits**', async (route) => {
    const url = new URL(route.request().url());
    auditRequests.push(url.search);
    const pageNumber = Number(url.searchParams.get('page') || '0');
    const traceId = url.searchParams.get('traceId');
    const eventType = url.searchParams.get('eventType');
    const filtered = traceId === 'trace-audit-0' && eventType === 'USER_UNLOCKED';
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-audits',
        data: {
          items: [
            {
              id: filtered ? 12 : pageNumber === 0 ? 11 : 10,
              actorId: 1,
              eventType: filtered || pageNumber === 0 ? 'USER_UNLOCKED' : 'TOKEN_SESSION_REVOKED',
              targetType: 'USER',
              targetId: 1,
              traceId: filtered ? 'trace-audit-0' : `trace-audit-${pageNumber}`,
              payloadJson: filtered ? '{"reason":"manual-check"}' : '{}'
            }
          ],
          page: pageNumber,
          size: 8,
          totalItems: filtered ? 1 : 9,
          totalPages: filtered ? 1 : 2,
          hasPrevious: pageNumber > 0,
          hasNext: !filtered && pageNumber === 0
        }
      })
    });
  });

  await page.route('**/api/v1/users/token-sessions**', async (route) => {
    const url = new URL(route.request().url());
    sessionRequests.push(url.search);
    const pageNumber = Number(url.searchParams.get('page') || '0');
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-token-sessions',
        data: {
          items: [
            {
              id: pageNumber === 0 ? 21 : 20,
              userId: 1,
              username: pageNumber === 0 ? 'admin' : 'support',
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
          page: pageNumber,
          size: 8,
          totalItems: 9,
          totalPages: 2,
          hasPrevious: pageNumber > 0,
          hasNext: pageNumber === 0
        }
      })
    });
  });

  await page.route('**/api/v1/users**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname !== '/api/v1/users') {
      await route.fallback();
      return;
    }
    userRequests.push(url.search);
    const pageNumber = Number(url.searchParams.get('page') || '0');
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-users',
        data: {
          items: [
            {
              id: pageNumber === 0 ? 1 : 2,
              username: pageNumber === 0 ? 'admin' : 'support',
              displayName: pageNumber === 0 ? '系统管理员' : '支持工程师',
              status: 'ACTIVE',
              failedLoginCount: 0,
              lockedUntil: null,
              roles: pageNumber === 0 ? ['ADMIN'] : ['SUPPORT']
            }
          ],
          page: pageNumber,
          size: 8,
          totalItems: 9,
          totalPages: 2,
          hasPrevious: pageNumber > 0,
          hasNext: pageNumber === 0
        }
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();

  await page.getByRole('link', { name: '用户' }).click();
  await page.getByRole('button', { name: '刷新用户' }).click();
  await expect(page.getByText('系统管理员 / admin')).toBeVisible();
  await page.getByLabel('用户分页').getByRole('button', { name: '下一页' }).click();
  await expect(page.getByText('支持工程师 / support')).toBeVisible();

  await page.getByRole('link', { name: '会话' }).click();
  await page.getByRole('button', { name: '刷新会话' }).click();
  await expect(page.getByText('#21 / admin / REFRESHABLE')).toBeVisible();
  await page.getByLabel('会话分页').getByRole('button', { name: '下一页' }).click();
  await expect(page.getByText('#20 / support / REFRESHABLE')).toBeVisible();

  await page.getByRole('link', { name: '审计' }).click();
  await page.getByRole('button', { name: '查询审计' }).click();
  await expect(page.getByText('USER_UNLOCKED')).toBeVisible();
  await page.getByLabel('审计分页').getByRole('button', { name: '下一页' }).click();
  await expect(page.getByText('TOKEN_SESSION_REVOKED')).toBeVisible();
  await page.getByLabel('traceId').fill('trace-audit-0');
  await page.getByLabel('事件').fill('USER_UNLOCKED');
  await page.getByRole('button', { name: '查询审计' }).click();
  await expect(page.getByLabel('审计时间线').getByText('#12 / actor #1 / USER #1')).toBeVisible();
  await expect(page.getByText('{"reason":"manual-check"}')).toBeVisible();
  await expect(page.getByRole('button', { name: '复制 traceId' })).toBeVisible();

  expect(userRequests).toEqual(['?page=0&size=8', '?page=1&size=8']);
  expect(sessionRequests).toEqual(['?page=0&size=8', '?page=1&size=8']);
  expect(auditRequests).toEqual([
    '?page=0&size=8',
    '?page=1&size=8',
    '?page=0&size=8&traceId=trace-audit-0&eventType=USER_UNLOCKED'
  ]);
});
