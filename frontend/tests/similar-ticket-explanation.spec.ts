import { expect, test } from '@playwright/test';

test('shows matched keyword explanations for similar tickets', async ({ page }) => {
  const requestedPaths: string[] = [];

  await page.route('**/api/v1/auth/login', async (route) => {
    requestedPaths.push(new URL(route.request().url()).pathname);
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-login-user',
        data: {
          accessToken: 'regular-access-token',
          refreshToken: 'regular-refresh-token',
          expiresIn: 7200,
          refreshExpiresIn: 604800,
          username: 'user',
          displayName: '普通用户',
          roles: ['USER'],
          permissions: ['chat:use', 'ticket:draft', 'ticket:submit', 'ticket:similar:read']
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

  await page.route('**/api/v1/chat/ask', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-ask',
        data: {
          conversationId: 99,
          answer: '建议先确认账号状态，再检查客户端版本。',
          citations: [],
          confidence: 'HIGH',
          fallback: false
        }
      })
    });
  });

  await page.route('**/api/v1/tickets/similar**', async (route) => {
    const url = new URL(route.request().url());
    requestedPaths.push(`${url.pathname}?conversationId=${url.searchParams.get('conversationId')}`);
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-similar',
        data: [
          {
            ticketId: 42,
            title: 'VPN 历史故障',
            priority: 'HIGH',
            status: 'OPEN',
            score: 2,
            matchedKeywords: ['vpn', '客户端'],
            matchSummary: '命中关键词：vpn、客户端；来源：标题、描述'
          }
        ]
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '问答' }).click();
  await page.getByLabel('问题').fill('VPN 客户端无法连接怎么办？');
  await page.getByRole('button', { name: '提问' }).click();

  await page.getByRole('link', { name: '工单' }).click();
  await page.getByRole('button', { name: '相似工单' }).click();

  await expect(page.getByText('VPN 历史故障')).toBeVisible();
  await expect(page.getByText('命中关键词：vpn、客户端；来源：标题、描述')).toBeVisible();
  await expect(page.getByLabel('工单 #42 命中关键词').getByText('vpn')).toBeVisible();
  await expect(page.getByLabel('工单 #42 命中关键词').getByText('客户端')).toBeVisible();
  expect(requestedPaths).toContain('/api/v1/tickets/similar?conversationId=99');
});
