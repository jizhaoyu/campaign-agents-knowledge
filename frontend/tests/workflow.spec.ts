import { expect, Page, test } from '@playwright/test';

function okResponse(traceId: string, data: unknown) {
  return {
    code: 'OK',
    message: 'success',
    traceId,
    data
  };
}

async function mockWorkspaceBootstrap(page: Page) {
  const knowledgeBases = [
    {
      id: 1,
      name: 'IT Support KB',
      description: 'Seeded support knowledge',
      status: 'ACTIVE'
    }
  ];
  const documents = new Map<number, { id: number; fileName: string; parseStatus: string; indexStatus: string; chunkCount: number; failureReason: string | null }>();
  let nextKnowledgeBaseId = 10;
  let nextDocumentId = 100;
  let chatAskRequestCount = 0;
  let ticketDraftRequestCount = 0;
  let ticketSubmitRequestCount = 0;
  let releaseChatAskResponse: (() => void) | null = null;
  let releaseTicketDraftResponse: (() => void) | null = null;
  let releaseTicketSubmitResponse: (() => void) | null = null;

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
    if (route.request().method() === 'POST') {
      const body = JSON.parse(route.request().postData() || '{}') as { name: string; description?: string | null };
      const knowledgeBase = {
        id: nextKnowledgeBaseId++,
        name: body.name,
        description: body.description || null,
        status: 'ACTIVE'
      };
      knowledgeBases.unshift(knowledgeBase);
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(okResponse('trace-kb-create', knowledgeBase))
      });
      return;
    }

    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-kb-list', knowledgeBases))
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
          documentCount: documents.size,
          pendingIndexTaskCount: 0,
          runningIndexTaskCount: 0,
          failedIndexTaskCount: 0,
          failedDocumentCount: 0,
          pendingApprovalCount: 0,
          activeHighRiskTicketCount: 0,
          pendingHighRiskTicketCount: 0,
          activeTokenSessionCount: 1,
          totalIndexTaskCount: 0,
          indexFailureRate: 0,
          indexBacklogPressure: 0,
          operationsBacklogCount: 0,
          healthLevel: 'HEALTHY',
          alertCount: 0,
          healthSummary: '当前无待处理运营告警。',
          recommendedActions: ['保持索引队列、审批队列和高风险工单的日常巡检。'],
          generatedAt: '2026-06-06T03:00:00'
        })
      )
    });
  });

  await page.route('**/api/v1/documents**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v1/documents/upload') {
      const document = {
        id: nextDocumentId++,
        fileName: 'vpn-guide.md',
        parseStatus: 'SUCCESS',
        indexStatus: 'SUCCESS',
        chunkCount: 1,
        failureReason: null
      };
      documents.set(document.id, document);
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(okResponse('trace-document-upload', document))
      });
      return;
    }

    const pageNumber = Number(url.searchParams.get('page') || '0');
    const filteredDocuments = [...documents.values()].filter((document) => {
      const keyword = url.searchParams.get('keyword')?.toLowerCase();
      const status = url.searchParams.get('indexStatus');
      return (!keyword || document.fileName.toLowerCase().includes(keyword)) && (!status || document.indexStatus === status);
    });
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-document-list', {
          items: filteredDocuments,
          page: pageNumber,
          size: 10,
          totalItems: filteredDocuments.length,
          totalPages: filteredDocuments.length ? 1 : 0,
          hasPrevious: false,
          hasNext: false
        })
      )
    });
  });

  await page.route('**/api/v1/chat/ask', async (route) => {
    chatAskRequestCount += 1;
    if (chatAskRequestCount === 1) {
      await new Promise<void>((resolve) => {
        releaseChatAskResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-ask', {
          conversationId: 88,
          answer: '建议先确认账号状态，再检查客户端版本和网络连接。',
          citations: [
            {
              documentId: 100,
              chunkId: 1001,
              documentName: 'vpn-guide.md',
              snippet: 'VPN 连接失败时，先确认账号状态。'
            }
          ],
          confidence: 'HIGH',
          fallback: false
        })
      )
    });
  });

  await page.route('**/api/v1/tickets/draft', async (route) => {
    ticketDraftRequestCount += 1;
    if (ticketDraftRequestCount === 1) {
      await new Promise<void>((resolve) => {
        releaseTicketDraftResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-ticket-draft', {
          conversationId: 88,
          title: '【智能草稿】VPN 无法连接',
          description: '问题描述:\nVPN 无法连接\n\n知识库建议:\n确认账号状态。',
          priority: 'MEDIUM',
          suggestedAssigneeId: null
        })
      )
    });
  });

  await page.route('**/api/v1/tickets', async (route) => {
    if (new URL(route.request().url()).pathname !== '/api/v1/tickets') {
      await route.fallback();
      return;
    }
    ticketSubmitRequestCount += 1;
    if (ticketSubmitRequestCount === 1) {
      await new Promise<void>((resolve) => {
        releaseTicketSubmitResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-ticket-submit', {
          ticketId: 501,
          status: 'OPEN',
          approvalRequired: false,
          approvalTaskId: null
        })
      )
    });
  });

  return {
    releaseChatAskResponse: () => releaseChatAskResponse?.(),
    releaseTicketDraftResponse: () => releaseTicketDraftResponse?.(),
    releaseTicketSubmitResponse: () => releaseTicketSubmitResponse?.(),
    counts: () => ({ chatAskRequestCount, ticketDraftRequestCount, ticketSubmitRequestCount })
  };
}

test('runs the core knowledge-to-ticket workflow', async ({ page }) => {
  const workspace = await mockWorkspaceBootstrap(page);
  await page.goto('/');

  await expect(page.getByRole('heading', { name: '企业知识库到工单闭环的 AI 工作台' })).toBeVisible();
  await page.getByRole('button', { name: '进入工作台' }).click();

  await expect(page.getByRole('heading', { name: '知识驱动的工单协同台' })).toBeVisible();

  const suffix = Date.now();
  await page.getByRole('link', { name: '知识库' }).click();
  await page.getByLabel('新建知识库').fill(`E2E Support KB ${suffix}`);
  await page.getByPlaceholder('知识库说明').fill('Created by Playwright');
  await page.getByRole('button', { name: '创建知识库' }).click();
  await expect(page.getByText(`知识库已创建：E2E Support KB ${suffix}`)).toBeVisible();

  const kbValue = await page
    .getByLabel('选择知识库')
    .locator('option')
    .filter({ hasText: `E2E Support KB ${suffix}` })
    .getAttribute('value');
  expect(kbValue).toBeTruthy();
  await page.getByLabel('选择知识库').selectOption(kbValue!);
  await page.getByLabel('上传 Markdown / TXT / PDF / DOCX').setInputFiles({
    name: 'vpn-guide.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('VPN 连接失败时，先确认账号状态，再检查客户端版本和网络连接。', 'utf-8')
  });
  await page.getByRole('button', { name: '上传并后台索引' }).click();
  await expect(page.getByText('文档已上传，正在后台解析和索引')).toBeVisible();
  await expect(page.getByRole('heading', { name: '文档管理' })).toBeVisible();
  await page.getByLabel('搜索文档').fill('vpn');
  await page.getByLabel('索引状态').selectOption('SUCCESS');
  const documentRow = page.locator('.document-row').filter({ hasText: 'vpn-guide.md' });
  await expect(documentRow.getByText('解析 SUCCESS / 索引 SUCCESS')).toBeVisible();
  await expect(documentRow.getByRole('button', { name: '重建索引' })).toBeEnabled();
  await expect(documentRow.getByRole('button', { name: '删除' })).toBeEnabled();

  await page.getByRole('link', { name: '问答' }).click();
  await page.getByLabel('问题').fill('VPN 无法连接应该怎么处理？');
  await page.getByRole('button', { name: '提问' }).click();
  await expect(page.getByLabel('回答生成骨架')).toBeVisible();
  await expect(page.getByRole('button', { name: '生成中...' })).toBeDisabled();
  await page.getByRole('button', { name: '生成中...' }).click({ force: true });
  expect(workspace.counts().chatAskRequestCount).toBe(1);
  workspace.releaseChatAskResponse();
  await expect(page.getByLabel('回答生成骨架')).toHaveCount(0);
  await expect(page.getByText(/已生成回答/)).toBeVisible();
  await expect(page.locator('#问答').getByText('vpn-guide.md')).toBeVisible();
  await expect(page.getByRole('heading', { name: '最近问答' })).toBeVisible();

  await page.getByRole('link', { name: '工单' }).click();
  await page.getByRole('button', { name: '生成草稿' }).click();
  await expect(page.getByLabel('工单草稿骨架')).toBeVisible();
  await expect(page.getByRole('button', { name: '生成中...' })).toBeDisabled();
  await page.getByRole('button', { name: '生成中...' }).click({ force: true });
  expect(workspace.counts().ticketDraftRequestCount).toBe(1);
  workspace.releaseTicketDraftResponse();
  await expect(page.getByLabel('工单草稿骨架')).toHaveCount(0);
  await expect(page.getByText('工单草稿已生成')).toBeVisible();
  await page.getByRole('button', { name: '提交工单' }).click();
  await expect(page.getByRole('button', { name: '提交中...' })).toBeDisabled();
  await page.getByRole('button', { name: '提交中...' }).click({ force: true });
  expect(workspace.counts().ticketSubmitRequestCount).toBe(1);
  workspace.releaseTicketSubmitResponse();
  await expect(page.getByText(/工单已提交/)).toBeVisible();
  await expect(page.getByText('Ticket #501')).toBeVisible();
});

test('returns to login when the stored token is invalid', async ({ page }) => {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-login-user', {
          accessToken: 'valid-access-token',
          refreshToken: 'valid-refresh-token',
          expiresIn: 7200,
          refreshExpiresIn: 604800,
          username: 'user',
          displayName: '普通用户',
          roles: ['USER'],
          permissions: ['chat:use', 'ticket:draft', 'ticket:submit', 'ticket:similar:read']
        })
      )
    });
  });

  await page.route('**/api/v1/knowledge-bases', async (route) => {
    if (route.request().headers().authorization === 'Bearer invalid-token') {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'UNAUTHORIZED',
          message: '登录态已失效，请重新登录',
          traceId: 'trace-invalid-token',
          data: null
        })
      });
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-kb-list', []))
    });
  });

  await page.route('**/api/v1/chat/history**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-history-empty', []))
    });
  });

  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'UNAUTHORIZED',
        message: '登录态已失效，请重新登录',
        traceId: 'trace-refresh-expired',
        data: null
      })
    });
  });

  await page.goto('/');

  await page.getByRole('button', { name: '进入工作台' }).click();
  await expect(page.getByRole('heading', { name: '知识驱动的工单协同台' })).toBeVisible();

  await page.evaluate(() => {
    const savedSession = JSON.parse(localStorage.getItem('kta-session') || '{}');
    localStorage.setItem('kta-session', JSON.stringify({ ...savedSession, accessToken: 'invalid-token' }));
  });
  await page.reload();

  await expect(page.getByRole('heading', { name: '企业知识库到工单闭环的 AI 工作台' })).toBeVisible();
  await expect(page.getByText(/登录态已失效，请重新登录/)).toBeVisible();
});
