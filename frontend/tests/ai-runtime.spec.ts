import { expect, type Page, test } from '@playwright/test';

function okResponse(traceId: string, data: unknown) {
  return {
    code: 'OK',
    message: 'success',
    traceId,
    data
  };
}

function adminSession() {
  return {
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
  };
}

function healthyDashboard() {
  return {
    knowledgeBaseCount: 0,
    documentCount: 0,
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
    recommendedActions: ['保持日常巡检。'],
    generatedAt: '2026-06-06T02:00:00'
  };
}

function readyAiRuntimeStatus() {
  return {
    activeProfiles: ['mysql', 'ai-openai'],
    chat: {
      enabled: true,
      modelAvailable: true,
      credentialConfigured: true,
      provider: 'openai',
      baseUrl: 'https://relay.example.com/v1',
      path: '/chat/completions',
      model: 'gpt-5.4'
    },
    embedding: {
      enabled: false,
      modelAvailable: false,
      credentialConfigured: true,
      provider: 'none',
      baseUrl: 'https://relay.example.com/v1',
      path: '/embeddings',
      model: 'text-embedding-3-small'
    },
    readinessLevel: 'READY',
    warnings: ['AI runtime configuration is ready for enabled components.'],
    generatedAt: '2026-06-06T02:10:00'
  };
}

async function routeAdminShell(page: Page) {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-login-admin', adminSession()))
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
      body: JSON.stringify(okResponse('trace-dashboard', healthyDashboard()))
    });
  });
}

test('shows AI runtime configuration without exposing secrets', async ({ page }) => {
  const runtimeRequests: string[] = [];
  await routeAdminShell(page);

  await page.route('**/api/v1/ai/runtime', async (route) => {
    runtimeRequests.push(new URL(route.request().url()).pathname);
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-ai-runtime', readyAiRuntimeStatus()))
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: 'AI配置' }).click();

  await expect(page).toHaveURL(/\/ai-config$/);
  await expect(page.getByRole('heading', { name: 'AI 运行配置' })).toBeVisible();
  await expect(page.getByText('可用')).toBeVisible();
  await expect(page.getByText('https://relay.example.com/v1').first()).toBeVisible();
  await expect(page.getByText('/chat/completions')).toBeVisible();
  await expect(page.getByText('gpt-5.4')).toBeVisible();
  await expect(page.getByText('mysql')).toBeVisible();
  await expect(page.getByText('ai-openai')).toBeVisible();
  await expect(page.getByText('API key')).toBeVisible();
  await expect(page.getByText('secret-runtime-key')).toHaveCount(0);
  await page.getByRole('button', { name: '刷新配置' }).click();
  await expect(page.getByText('AI 运行配置已刷新')).toBeVisible();

  expect(runtimeRequests).toEqual(['/api/v1/ai/runtime', '/api/v1/ai/runtime']);
});

test('keeps AI runtime refresh single-flight while loading', async ({ page }) => {
  let runtimeRequestCount = 0;
  let releaseRuntimeResponse: (() => void) | null = null;
  await routeAdminShell(page);

  await page.route('**/api/v1/ai/runtime', async (route) => {
    runtimeRequestCount += 1;
    if (runtimeRequestCount === 1) {
      await new Promise<void>((resolve) => {
        releaseRuntimeResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-ai-runtime', readyAiRuntimeStatus()))
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: 'AI配置' }).click();

  await expect(page.getByLabel('AI 运行配置概览骨架')).toBeVisible();
  await expect(page.getByLabel('AI 启动检查骨架')).toBeVisible();
  await expect(page.getByLabel('AI 组件状态骨架')).toBeVisible();
  await expect(page.getByRole('button', { name: '刷新中...' })).toBeDisabled();
  await page.getByRole('button', { name: '刷新中...' }).click({ force: true });
  expect(runtimeRequestCount).toBe(1);

  releaseRuntimeResponse?.();
  await expect(page.getByLabel('AI 运行配置概览骨架')).toHaveCount(0);
  await expect(page.getByLabel('AI 启动检查骨架')).toHaveCount(0);
  await expect(page.getByLabel('AI 组件状态骨架')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '刷新配置' })).toBeEnabled();
  await expect(page.getByText('可用')).toBeVisible();
  expect(runtimeRequestCount).toBe(1);
});

test('recovers AI runtime status from inline retry after load failure', async ({ page }) => {
  let runtimeRequestCount = 0;
  await routeAdminShell(page);

  await page.route('**/api/v1/ai/runtime', async (route) => {
    runtimeRequestCount += 1;
    if (runtimeRequestCount === 1) {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'INTERNAL_ERROR',
          message: 'AI 配置读取失败',
          traceId: 'trace-ai-runtime-failed',
          data: null
        })
      });
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-ai-runtime', readyAiRuntimeStatus()))
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: 'AI配置' }).click();

  await expect(page.getByText('AI 运行配置加载失败')).toBeVisible();
  await expect(page.getByRole('alert').getByText('后端异常：AI 配置读取失败，traceId: trace-ai-runtime-failed')).toBeVisible();
  await page.getByRole('button', { name: '重试配置读取' }).click();

  await expect(page.getByText('AI 运行配置加载失败')).toHaveCount(0);
  await expect(page.getByText('可用')).toBeVisible();
  expect(runtimeRequestCount).toBe(2);
});
