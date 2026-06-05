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

function pressureDashboard() {
  return {
    knowledgeBaseCount: 3,
    documentCount: 12,
    pendingIndexTaskCount: 4,
    runningIndexTaskCount: 1,
    failedIndexTaskCount: 2,
    failedDocumentCount: 2,
    pendingApprovalCount: 5,
    activeHighRiskTicketCount: 6,
    pendingHighRiskTicketCount: 3,
    activeTokenSessionCount: 7,
    totalIndexTaskCount: 10,
    indexFailureRate: 0.2,
    indexBacklogPressure: 0.42,
    operationsBacklogCount: 15,
    healthLevel: 'CRITICAL',
    alertCount: 3,
    healthSummary: '存在需要立即处理的索引失败或高风险阻塞项。',
    recommendedActions: [
      '处理失败索引任务和失败文档，优先查看失败原因后重试或修正文档格式。',
      '存在待审批高风险工单，请优先核对证据并完成审批。'
    ],
    generatedAt: '2026-06-06T01:20:00'
  };
}

async function routeDashboardShell(page: Page) {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-login-admin', adminSession()))
    });
  });

  await page.route('**/api/v1/knowledge-bases', async (route) => {
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
}

test('loads operations metrics for dashboard readers', async ({ page }) => {
  await routeDashboardShell(page);
  await page.route('**/api/v1/dashboard/operations', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-dashboard', pressureDashboard()))
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();

  await expect(page.getByText('索引队列')).toBeVisible();
  await expect(page.getByText('等待 4 / 运行 1')).toBeVisible();
  await expect(page.getByText('索引失败', { exact: true })).toBeVisible();
  await expect(page.getByText('失败任务 2 / 文档 12')).toBeVisible();
  await expect(page.getByText('失败率', { exact: true })).toBeVisible();
  await expect(page.getByText('20%', { exact: true })).toBeVisible();
  await expect(page.getByText('失败 2 / 索引任务 10')).toBeVisible();
  await expect(page.getByText('积压压力', { exact: true })).toBeVisible();
  await expect(page.getByText('42%', { exact: true })).toBeVisible();
  await expect(page.getByText('待处理 5 / 文档 12')).toBeVisible();
  await expect(page.getByText('全局待处理审批任务')).toBeVisible();
  await expect(page.getByText('运营待办', { exact: true })).toBeVisible();
  await expect(page.getByText('索引、审批和高风险阻塞合计')).toBeVisible();
  await expect(page.getByText('运营健康')).toBeVisible();
  await expect(page.getByText('3 个待处理提醒')).toBeVisible();
  await expect(page.getByText('运营摘要：存在需要立即处理的索引失败或高风险阻塞项。')).toBeVisible();
  await expect(page.getByText('失败率 20% / 积压压力 42%')).toBeVisible();
  await expect(page.getByText('处理失败索引任务和失败文档，优先查看失败原因后重试或修正文档格式。')).toBeVisible();
  await expect(page.getByText('待审批 3 / 已开放或待批')).toBeVisible();
  await expect(page.getByText('未吊销且未过期 Token 会话')).toBeVisible();
  await expect(page.getByRole('button', { name: '刷新运营指标' })).toBeVisible();
});

test('keeps operations dashboard refresh single-flight while loading', async ({ page }) => {
  let dashboardRequestCount = 0;
  let releaseDashboardResponse: (() => void) | null = null;

  await routeDashboardShell(page);
  await page.route('**/api/v1/dashboard/operations', async (route) => {
    dashboardRequestCount += 1;
    if (dashboardRequestCount === 1) {
      await new Promise<void>((resolve) => {
        releaseDashboardResponse = resolve;
      });
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-dashboard', pressureDashboard()))
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();

  await expect(page.getByText('运营指标正在刷新...')).toBeVisible();
  await expect(page.getByLabel('运营指标骨架-索引队列')).toBeVisible();
  await expect(page.getByLabel('运营摘要骨架')).toBeVisible();
  await expect(page.getByRole('button', { name: '刷新中...' })).toBeDisabled();
  await page.getByRole('button', { name: '刷新中...' }).click({ force: true });
  expect(dashboardRequestCount).toBe(1);

  releaseDashboardResponse?.();
  await expect(page.getByLabel('运营指标骨架-索引队列')).toHaveCount(0);
  await expect(page.getByLabel('运营摘要骨架')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '刷新运营指标' })).toBeEnabled();
  await expect(page.getByText('失败率 20% / 积压压力 42%')).toBeVisible();
  expect(dashboardRequestCount).toBe(1);
});

test('recovers operations dashboard metrics from inline retry after load failure', async ({ page }) => {
  let dashboardRequestCount = 0;

  await routeDashboardShell(page);
  await page.route('**/api/v1/dashboard/operations', async (route) => {
    dashboardRequestCount += 1;
    if (dashboardRequestCount === 1) {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'INTERNAL_ERROR',
          message: '运营指标读取失败',
          traceId: 'trace-dashboard-failed',
          data: null
        })
      });
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(okResponse('trace-dashboard', pressureDashboard()))
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();

  await expect(page.getByText('运营指标加载失败')).toBeVisible();
  await expect(page.locator('#总览').getByText('后端异常：运营指标读取失败，traceId: trace-dashboard-failed')).toBeVisible();
  await page.getByRole('button', { name: '重试运营指标' }).click();

  await expect(page.getByText('运营指标加载失败')).toHaveCount(0);
  await expect(page.getByText('失败率 20% / 积压压力 42%')).toBeVisible();
  expect(dashboardRequestCount).toBe(2);
});
