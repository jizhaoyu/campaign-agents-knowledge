import { expect, test } from '@playwright/test';

test('copies citations and submitted ticket id from the workspace', async ({ page }) => {
  await page.route('**/api/v1/auth/login', async (route) => {
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
          citations: [
            {
              documentId: 7,
              chunkId: 701,
              documentName: 'vpn-guide.md',
              snippet: 'VPN 连接失败时，先确认账号状态。'
            }
          ],
          confidence: 'HIGH',
          fallback: false
        }
      })
    });
  });

  await page.route('**/api/v1/tickets/draft', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-ticket-draft',
        data: {
          conversationId: 99,
          title: '【智能草稿】VPN 客户端无法连接',
          description: '问题描述:\nVPN 客户端无法连接\n\n知识库建议:\n确认账号状态。',
          priority: 'MEDIUM',
          suggestedAssigneeId: null
        }
      })
    });
  });

  await page.route('**/api/v1/tickets', async (route) => {
    if (new URL(route.request().url()).pathname !== '/api/v1/tickets') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-ticket-submit',
        data: {
          ticketId: 1234,
          status: 'OPEN',
          approvalRequired: false,
          approvalTaskId: null
        }
      })
    });
  });

  await page.goto('/');
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: async () => undefined
      }
    });
  });
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '问答' }).click();
  await page.getByLabel('问题').fill('VPN 客户端无法连接怎么办？');
  await page.getByRole('button', { name: '提问' }).click();
  await page.getByRole('button', { name: '复制引用' }).click();

  await expect(page.getByText('引用已复制：vpn-guide.md')).toBeVisible();

  await page.getByRole('link', { name: '工单' }).click();
  await page.getByRole('button', { name: '生成草稿' }).click();
  await page.getByRole('button', { name: '提交工单' }).click();
  await expect(page.getByText('Ticket #1234')).toBeVisible();
  await page.getByRole('button', { name: '复制工单号' }).click();

  await expect(page.getByText('工单号已复制：#1234')).toBeVisible();
});

test('shows manual copy guidance when clipboard is unavailable', async ({ page }) => {
  await page.route('**/api/v1/auth/login', async (route) => {
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
          permissions: ['chat:use']
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
          answer: '建议先确认账号状态。',
          citations: [
            {
              documentId: 7,
              chunkId: 701,
              documentName: 'vpn-guide.md',
              snippet: 'VPN 连接失败时，先确认账号状态。'
            }
          ],
          confidence: 'HIGH',
          fallback: false
        }
      })
    });
  });

  await page.goto('/');
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: undefined
    });
  });
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '问答' }).click();
  await page.getByLabel('问题').fill('VPN 客户端无法连接怎么办？');
  await page.getByRole('button', { name: '提问' }).click();
  await page.getByRole('button', { name: '复制引用' }).click();

  await expect(page.getByText('请手动复制引用：vpn-guide.md')).toBeVisible();
});
