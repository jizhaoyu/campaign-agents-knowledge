import { expect, test } from '@playwright/test';

function okResponse(traceId: string, data: unknown) {
  return {
    code: 'OK',
    message: 'success',
    traceId,
    data
  };
}

test('filters knowledge bases by server-side keyword search', async ({ page }) => {
  const knowledgeBaseKeywords: Array<string | null> = [];
  const knowledgeBases = [
    {
      id: 1,
      name: 'IT Support KB',
      description: 'VPN and device support playbooks',
      status: 'ACTIVE'
    },
    {
      id: 2,
      name: 'Policy Archive',
      description: 'Human resources policy collection',
      status: 'ACTIVE'
    }
  ];

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

  await page.route('**/api/v1/knowledge-bases**', async (route) => {
    const url = new URL(route.request().url());
    const keyword = url.searchParams.get('keyword');
    knowledgeBaseKeywords.push(keyword);
    const normalizedKeyword = keyword?.toLowerCase() || '';
    const data = knowledgeBases.filter((knowledgeBase) => {
      if (!normalizedKeyword) {
        return true;
      }
      return (
        knowledgeBase.name.toLowerCase().includes(normalizedKeyword) ||
        knowledgeBase.description.toLowerCase().includes(normalizedKeyword)
      );
    });
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-kb-list', data))
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
          knowledgeBaseCount: knowledgeBases.length,
          documentCount: 0,
          pendingIndexTaskCount: 0,
          runningIndexTaskCount: 0,
          failedIndexTaskCount: 0,
          failedDocumentCount: 0,
          pendingApprovalCount: 0,
          activeHighRiskTicketCount: 0,
          pendingHighRiskTicketCount: 0,
          activeTokenSessionCount: 1,
          healthLevel: 'HEALTHY',
          alertCount: 0,
          healthSummary: '当前无待处理运营告警。',
          recommendedActions: ['保持索引队列、审批队列和高风险工单的日常巡检。'],
          generatedAt: '2026-06-06T07:00:00'
        })
      )
    });
  });

  await page.route('**/api/v1/documents**', async (route) => {
    const url = new URL(route.request().url());
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-documents', {
          items: [],
          page: Number(url.searchParams.get('page') || '0'),
          size: Number(url.searchParams.get('size') || '10'),
          totalItems: 0,
          totalPages: 0,
          hasPrevious: false,
          hasNext: false
        })
      )
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '知识库' }).click();

  await expect(page.getByText('当前显示 2 个知识库')).toBeVisible();
  await page.getByLabel('搜索知识库').fill('policy');
  await expect(page.getByText('当前显示 1 个知识库，未命中时可清空关键词')).toBeVisible();
  await expect(page.getByLabel('选择知识库')).toHaveValue('2');

  await page.getByLabel('搜索知识库').fill('missing');
  await expect(page.getByText('当前显示 0 个知识库，未命中时可清空关键词')).toBeVisible();
  await expect(page.getByLabel('选择知识库')).toHaveValue('');

  expect(knowledgeBaseKeywords).toContain(null);
  expect(knowledgeBaseKeywords).toContain('policy');
  expect(knowledgeBaseKeywords).toContain('missing');
});
