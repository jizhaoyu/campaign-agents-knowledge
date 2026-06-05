import { expect, test } from '@playwright/test';

test('shows AI runtime configuration without exposing secrets', async ({ page }) => {
  const runtimeRequests: string[] = [];

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
          activeTokenSessionCount: 1,
          healthLevel: 'HEALTHY',
          alertCount: 0,
          healthSummary: '当前无待处理运营告警。',
          recommendedActions: ['保持日常巡检。'],
          generatedAt: '2026-06-06T02:00:00'
        }
      })
    });
  });

  await page.route('**/api/v1/ai/runtime', async (route) => {
    runtimeRequests.push(new URL(route.request().url()).pathname);
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-ai-runtime',
        data: {
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
        }
      })
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
