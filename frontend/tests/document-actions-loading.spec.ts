import { expect, Page, test } from '@playwright/test';

type DocumentItem = {
  id: number;
  fileName: string;
  parseStatus: string;
  indexStatus: string;
  chunkCount: number;
  failureReason: string | null;
};

function okResponse(traceId: string, data: unknown) {
  return {
    code: 'OK',
    message: 'success',
    traceId,
    data
  };
}

async function routeWorkspace(page: Page) {
  const documents = new Map<number, DocumentItem>([
    [
      31,
      {
        id: 31,
        fileName: 'vpn-guide.md',
        parseStatus: 'SUCCESS',
        indexStatus: 'SUCCESS',
        chunkCount: 3,
        failureReason: null
      }
    ],
    [
      32,
      {
        id: 32,
        fileName: 'failed-vpn.md',
        parseStatus: 'FAILED',
        indexStatus: 'FAILED',
        chunkCount: 0,
        failureReason: '解析失败'
      }
    ],
    [
      33,
      {
        id: 33,
        fileName: 'printer-guide.md',
        parseStatus: 'SUCCESS',
        indexStatus: 'SUCCESS',
        chunkCount: 2,
        failureReason: null
      }
    ]
  ]);
  const counts = {
    upload: 0,
    reindex: 0,
    retryFailed: 0,
    delete: 0
  };
  let nextDocumentId = 100;
  let releaseUploadResponse: (() => void) | null = null;
  let releaseReindexResponse: (() => void) | null = null;
  let releaseRetryFailedResponse: (() => void) | null = null;
  let releaseDeleteResponse: (() => void) | null = null;

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
      body: JSON.stringify(
        okResponse('trace-kb-list', [
          {
            id: 7,
            name: 'IT Support KB',
            description: 'Seeded support knowledge',
            status: 'ACTIVE'
          }
        ])
      )
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
          knowledgeBaseCount: 1,
          documentCount: documents.size,
          pendingIndexTaskCount: 0,
          runningIndexTaskCount: 0,
          failedIndexTaskCount: 1,
          failedDocumentCount: 1,
          pendingApprovalCount: 0,
          activeHighRiskTicketCount: 0,
          pendingHighRiskTicketCount: 0,
          activeTokenSessionCount: 1,
          totalIndexTaskCount: 1,
          indexFailureRate: 1,
          indexBacklogPressure: 0,
          operationsBacklogCount: 1,
          healthLevel: 'ATTENTION',
          alertCount: 1,
          healthSummary: '存在需要跟进的运营待办。',
          recommendedActions: ['处理失败索引任务和失败文档。'],
          generatedAt: '2026-06-06T04:00:00'
        })
      )
    });
  });

  await page.route('**/api/v1/documents**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/api/v1/documents/upload') {
      counts.upload += 1;
      if (counts.upload === 1) {
        await new Promise<void>((resolve) => {
          releaseUploadResponse = resolve;
        });
      }
      const document = {
        id: nextDocumentId++,
        fileName: 'uploaded-runbook.md',
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

    if (url.pathname === '/api/v1/documents/retry-failed') {
      counts.retryFailed += 1;
      if (counts.retryFailed === 1) {
        await new Promise<void>((resolve) => {
          releaseRetryFailedResponse = resolve;
        });
      }
      const retriedDocuments = [...documents.values()]
        .filter((document) => document.indexStatus === 'FAILED')
        .map((document) => ({
          ...document,
          parseStatus: 'SUCCESS',
          indexStatus: 'PENDING',
          failureReason: null
        }));
      retriedDocuments.forEach((document) => documents.set(document.id, document));
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(okResponse('trace-document-retry-failed', retriedDocuments))
      });
      return;
    }

    const reindexMatch = url.pathname.match(/^\/api\/v1\/documents\/(\d+)\/reindex$/);
    if (reindexMatch) {
      counts.reindex += 1;
      if (counts.reindex === 1) {
        await new Promise<void>((resolve) => {
          releaseReindexResponse = resolve;
        });
      }
      const documentId = Number(reindexMatch[1]);
      const document = documents.get(documentId);
      const reindexedDocument = document
        ? { ...document, parseStatus: 'SUCCESS', indexStatus: 'PENDING', failureReason: null }
        : null;
      if (reindexedDocument) {
        documents.set(documentId, reindexedDocument);
      }
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(okResponse('trace-document-reindex', reindexedDocument))
      });
      return;
    }

    const documentMatch = url.pathname.match(/^\/api\/v1\/documents\/(\d+)$/);
    if (documentMatch && request.method() === 'DELETE') {
      counts.delete += 1;
      if (counts.delete === 1) {
        await new Promise<void>((resolve) => {
          releaseDeleteResponse = resolve;
        });
      }
      documents.delete(Number(documentMatch[1]));
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(okResponse('trace-document-delete', null))
      });
      return;
    }

    const pageNumber = Number(url.searchParams.get('page') || '0');
    const keyword = url.searchParams.get('keyword')?.toLowerCase();
    const status = url.searchParams.get('indexStatus');
    const items = [...documents.values()].filter((document) => {
      return (!keyword || document.fileName.toLowerCase().includes(keyword)) && (!status || document.indexStatus === status);
    });
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        okResponse('trace-documents', {
          items,
          page: pageNumber,
          size: Number(url.searchParams.get('size') || '10'),
          totalItems: items.length,
          totalPages: items.length ? 1 : 0,
          hasPrevious: false,
          hasNext: false
        })
      )
    });
  });

  return {
    releaseUploadResponse: () => releaseUploadResponse?.(),
    releaseReindexResponse: () => releaseReindexResponse?.(),
    releaseRetryFailedResponse: () => releaseRetryFailedResponse?.(),
    releaseDeleteResponse: () => releaseDeleteResponse?.(),
    counts: () => ({ ...counts })
  };
}

test('keeps document write actions single-flight while requests are pending', async ({ page }) => {
  const workspace = await routeWorkspace(page);
  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '知识库' }).click();

  await expect(page.getByText('vpn-guide.md')).toBeVisible();
  await page.getByLabel('上传 Markdown / TXT / PDF / DOCX').setInputFiles({
    name: 'uploaded-runbook.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('上传验证文档', 'utf-8')
  });
  await page.getByRole('button', { name: '上传并后台索引' }).click();
  await expect(page.getByRole('button', { name: '上传中...' })).toBeDisabled();
  await page.getByRole('button', { name: '上传中...' }).click({ force: true });
  expect(workspace.counts().upload).toBe(1);
  workspace.releaseUploadResponse();
  await expect(page.getByText('文档已上传，正在后台解析和索引')).toBeVisible();

  const vpnRow = page.locator('.document-row').filter({ hasText: 'vpn-guide.md' });
  await vpnRow.getByRole('button', { name: '重建索引' }).click();
  await expect(vpnRow.getByRole('button', { name: '重建中...' })).toBeDisabled();
  await vpnRow.getByRole('button', { name: '重建中...' }).click({ force: true });
  expect(workspace.counts().reindex).toBe(1);
  workspace.releaseReindexResponse();
  await expect(page.getByText('已提交重建索引任务')).toBeVisible();

  await page.getByRole('button', { name: '重试失败文档' }).click();
  await expect(page.getByRole('button', { name: '重试中...' })).toBeDisabled();
  await page.getByRole('button', { name: '重试中...' }).click({ force: true });
  expect(workspace.counts().retryFailed).toBe(1);
  workspace.releaseRetryFailedResponse();
  await expect(page.getByText('已重试 1 个失败文档')).toBeVisible();

  const printerRow = page.locator('.document-row').filter({ hasText: 'printer-guide.md' });
  await printerRow.getByRole('button', { name: '删除' }).click();
  await expect(printerRow.getByRole('button', { name: '删除中...' })).toBeDisabled();
  await printerRow.getByRole('button', { name: '删除中...' }).click({ force: true });
  expect(workspace.counts().delete).toBe(1);
  workspace.releaseDeleteResponse();
  await expect(page.getByText('文档已删除')).toBeVisible();
  await expect(page.getByText('printer-guide.md')).toHaveCount(0);
});
