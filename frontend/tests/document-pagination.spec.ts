import { expect, test } from '@playwright/test';

test('uses server-side pagination and filters for document management', async ({ page }) => {
  const documentRequests: Array<{
    page: string | null;
    size: string | null;
    keyword: string | null;
    indexStatus: string | null;
  }> = [];
  let releaseDocumentsResponse: (() => void) | null = null;

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
        traceId: 'trace-kb-list',
        data: [
          {
            id: 7,
            name: 'IT Support KB',
            description: 'Seeded support knowledge',
            status: 'ACTIVE'
          }
        ]
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
          knowledgeBaseCount: 1,
          documentCount: 3,
          pendingIndexTaskCount: 0,
          runningIndexTaskCount: 0,
          failedIndexTaskCount: 0,
          failedDocumentCount: 1,
          pendingApprovalCount: 0,
          activeHighRiskTicketCount: 0,
          pendingHighRiskTicketCount: 0,
          activeTokenSessionCount: 1,
          totalIndexTaskCount: 1,
          indexFailureRate: 0,
          indexBacklogPressure: 0,
          operationsBacklogCount: 0,
          healthLevel: 'ATTENTION',
          alertCount: 1,
          healthSummary: '存在需要跟进的运营待办。',
          recommendedActions: ['处理失败索引任务和失败文档，优先查看失败原因后重试或修正文档格式。'],
          generatedAt: '2026-06-06T01:20:00'
        }
      })
    });
  });

  await page.route('**/api/v1/documents**', async (route) => {
    const url = new URL(route.request().url());
    documentRequests.push({
      page: url.searchParams.get('page'),
      size: url.searchParams.get('size'),
      keyword: url.searchParams.get('keyword'),
      indexStatus: url.searchParams.get('indexStatus')
    });
    const pageNumber = Number(url.searchParams.get('page') || '0');
    const keyword = url.searchParams.get('keyword');
    const status = url.searchParams.get('indexStatus');
    if (documentRequests.length === 1) {
      await new Promise<void>((resolve) => {
        releaseDocumentsResponse = resolve;
      });
    }
    const isNoMatch = keyword === 'missing';
    const items =
      isNoMatch
        ? []
        : keyword === 'failed' && status === 'FAILED'
        ? [
            {
              id: 32,
              fileName: 'failed-vpn.md',
              parseStatus: 'FAILED',
              indexStatus: 'FAILED',
              chunkCount: 0,
              failureReason: '解析失败'
            }
          ]
        : pageNumber === 1
          ? [
              {
                id: 21,
                fileName: 'vpn-page-2.md',
                parseStatus: 'SUCCESS',
                indexStatus: 'SUCCESS',
                chunkCount: 2,
                failureReason: null
              }
            ]
          : [
              {
                id: 31,
                fileName: 'vpn-page-1.md',
                parseStatus: 'SUCCESS',
                indexStatus: 'SUCCESS',
                chunkCount: 3,
                failureReason: null
              }
            ];

    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-documents',
        data: {
          items,
          page: pageNumber,
          size: Number(url.searchParams.get('size') || '10'),
          totalItems: isNoMatch ? 1 : keyword === 'failed' ? 1 : 11,
          totalPages: isNoMatch || keyword === 'failed' ? 1 : 2,
          hasPrevious: pageNumber > 0,
          hasNext: !keyword && pageNumber === 0
        }
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '知识库' }).click();

  await expect(page.getByLabel('文档列表骨架')).toBeVisible();
  await expect(page.getByRole('button', { name: '刷新中...' })).toBeVisible();
  releaseDocumentsResponse?.();
  await expect(page.getByLabel('文档列表骨架')).toHaveCount(0);
  await expect(page.getByText('vpn-page-1.md')).toBeVisible();
  await expect(page.getByText('第 1 / 2 页，共 11 个')).toBeVisible();
  await page.getByLabel('文档分页').getByRole('button', { name: '下一页' }).click();
  await expect(page.getByText('vpn-page-2.md')).toBeVisible();
  await expect(page.getByText('第 2 / 2 页，共 11 个')).toBeVisible();

  await page.getByLabel('搜索文档').fill('failed');
  await page.getByLabel('索引状态').selectOption('FAILED');
  await expect(page.getByText('failed-vpn.md')).toBeVisible();
  await expect(page.getByText('第 1 / 1 页，共 1 个')).toBeVisible();

  await page.getByLabel('每页数量').selectOption('20');
  await expect(page.getByText('failed-vpn.md')).toBeVisible();

  await page.getByLabel('搜索文档').fill('missing');
  await expect(page.getByText('没有匹配文档')).toBeVisible();
  await expect(page.getByText('换个关键词或状态筛选试试。')).toBeVisible();

  expect(documentRequests).toContainEqual({ page: '0', size: '10', keyword: null, indexStatus: null });
  expect(documentRequests).toContainEqual({ page: '1', size: '10', keyword: null, indexStatus: null });
  expect(documentRequests).toContainEqual({ page: '0', size: '10', keyword: 'failed', indexStatus: 'FAILED' });
  expect(documentRequests).toContainEqual({ page: '0', size: '20', keyword: 'failed', indexStatus: 'FAILED' });
  expect(documentRequests).toContainEqual({ page: '0', size: '20', keyword: 'missing', indexStatus: 'FAILED' });
});
