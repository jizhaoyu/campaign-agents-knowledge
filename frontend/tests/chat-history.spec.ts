import { expect, test } from '@playwright/test';

test('loads and restores recent chat history', async ({ page }) => {
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
        traceId: 'trace-history',
        data: [
          {
            conversationId: 88,
            knowledgeBaseId: 7,
            question: '历史 VPN 问题',
            answer: '历史回答：先确认账号状态，再检查客户端版本。',
            citations: [],
            confidence: 'NONE',
            fallback: true,
            createdAt: '2026-06-06T00:00:00',
            updatedAt: '2026-06-06T00:00:01'
          }
        ]
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();

  await expect(page.getByRole('heading', { name: '最近问答' })).toBeVisible();
  await expect(page.getByRole('button', { name: /历史 VPN 问题/ })).toBeVisible();

  await page.getByRole('button', { name: /历史 VPN 问题/ }).click();

  await expect(page.getByText('历史回答：先确认账号状态，再检查客户端版本。')).toBeVisible();
  await expect(page.locator('#问答 .answer-card').getByText('Conversation #88')).toBeVisible();
  await expect(page.getByText('已恢复会话 #88')).toBeVisible();
  await expect(page.getByLabel('选择知识库')).toHaveValue('7');
});
