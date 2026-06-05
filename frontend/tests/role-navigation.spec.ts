import { expect, test } from '@playwright/test';

test('hides admin-only workspace surfaces for a regular user', async ({ page }) => {
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
    requestedPaths.push(new URL(route.request().url()).pathname);
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-kb-list',
        data: [
          {
            id: 1,
            name: 'IT Support KB',
            description: 'Seeded support knowledge',
            status: 'ACTIVE'
          }
        ]
      })
    });
  });

  await page.route('**/api/v1/chat/history**', async (route) => {
    requestedPaths.push(new URL(route.request().url()).pathname);
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

  await page.route('**/api/v1/documents**', async (route) => {
    requestedPaths.push(new URL(route.request().url()).pathname);
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'FORBIDDEN',
        message: '无权限',
        traceId: 'trace-documents-forbidden',
        data: null
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();

  const navigation = page.getByRole('navigation', { name: '工作台导航' });
  await expect(page.getByRole('heading', { name: '知识驱动的工单协同台' })).toBeVisible();
  await expect(navigation.getByText('问答')).toBeVisible();
  await expect(navigation.getByText('工单')).toBeVisible();
  await expect(navigation.getByText('审批')).toHaveCount(0);
  await expect(navigation.getByText('用户')).toHaveCount(0);
  await expect(navigation.getByText('会话')).toHaveCount(0);
  await expect(navigation.getByText('审计')).toHaveCount(0);
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.getByRole('link', { name: '问答' }).click();
  await expect(page).toHaveURL(/\/chat$/);
  await expect(page.getByRole('heading', { name: '最近问答' })).toBeVisible();
  await page.reload();
  await expect(page).toHaveURL(/\/chat$/);
  await expect(page.getByRole('heading', { name: '最近问答' })).toBeVisible();
  await page.goto('/users');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('button', { name: '创建知识库' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '文档上传' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '文档管理' })).toHaveCount(0);

  expect(requestedPaths).toContain('/api/v1/auth/login');
  expect(requestedPaths).toContain('/api/v1/knowledge-bases');
  expect(requestedPaths).toContain('/api/v1/chat/history');
  expect(requestedPaths).not.toContain('/api/v1/documents');
  expect(requestedPaths).not.toContain('/api/v1/dashboard/operations');
});
