import { expect, Page, test } from '@playwright/test';

async function routeAdminShell(page: Page) {
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
          documentCount: 0,
          pendingIndexTaskCount: 0,
          runningIndexTaskCount: 0,
          failedIndexTaskCount: 0,
          failedDocumentCount: 0,
          pendingApprovalCount: 1,
          activeHighRiskTicketCount: 0,
          pendingHighRiskTicketCount: 0,
          activeTokenSessionCount: 1,
          totalIndexTaskCount: 0,
          indexFailureRate: 0,
          indexBacklogPressure: 0,
          operationsBacklogCount: 1,
          healthLevel: 'HEALTHY',
          alertCount: 0,
          healthSummary: '当前无待处理运营告警。',
          recommendedActions: ['保持日常巡检。'],
          generatedAt: '2026-06-06T02:00:00'
        }
      })
    });
  });
}

test('shows credential feedback when login fails', async ({ page }) => {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'UNAUTHORIZED',
        message: '用户名或密码错误',
        traceId: 'trace-login-failed',
        data: null
      })
    });
  });

  await page.goto('/');
  await page.getByLabel('账号').fill('admin');
  await page.getByLabel('密码').fill('wrong-password');
  await page.getByRole('button', { name: '进入工作台' }).click();

  await expect(page.getByText('登录失败：用户名或密码错误，traceId: trace-login-failed')).toBeVisible();
  await expect(page.getByText('登录态已失效，请重新登录')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '企业知识库到工单闭环的 AI 工作台' })).toBeVisible();
});

test('shows validation feedback when document upload fails', async ({ page }) => {
  await routeAdminShell(page);

  await page.route('**/api/v1/documents?**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-documents',
        data: {
          items: [],
          page: 0,
          size: 10,
          totalItems: 0,
          totalPages: 0,
          hasPrevious: false,
          hasNext: false
        }
      })
    });
  });

  await page.route('**/api/v1/documents/upload', async (route) => {
    await route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'VALIDATION_ERROR',
        message: '文件类型不支持',
        traceId: 'trace-upload-invalid',
        data: null
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '知识库' }).click();
  await page.setInputFiles('#file', {
    name: 'unsupported.exe',
    mimeType: 'application/octet-stream',
    buffer: Buffer.from('binary')
  });
  await page.getByRole('button', { name: '上传并后台索引' }).click();

  await expect(page.getByText('提交内容无效：文件类型不支持，traceId: trace-upload-invalid')).toBeVisible();
});

test('shows business feedback when approval decision fails', async ({ page }) => {
  await routeAdminShell(page);

  await page.route('**/api/v1/approvals/pending', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-approvals',
        data: [
          {
            id: 91,
            targetType: 'TICKET_CREATE',
            targetId: 302,
            approverId: 4,
            status: 'PENDING',
            comment: null
          }
        ]
      })
    });
  });

  await page.route('**/api/v1/approvals/comment-templates', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-templates',
        data: []
      })
    });
  });

  await page.route('**/api/v1/approvals/91/approve', async (route) => {
    await route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'APPROVAL_STATE_CHANGED',
        message: '审批任务已被其他人处理',
        traceId: 'trace-approval-conflict',
        data: null
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '审批' }).click();
  await page.getByRole('button', { name: '刷新待审批' }).click();
  await page.getByLabel('审批 #91 通过备注').fill('同意处理');
  await page.getByRole('button', { name: '通过' }).click();

  await expect(page.getByText('业务处理失败：审批任务已被其他人处理，traceId: trace-approval-conflict')).toBeVisible();
});

test('shows server feedback when AI runtime loading fails', async ({ page }) => {
  await routeAdminShell(page);

  await page.route('**/api/v1/ai/runtime', async (route) => {
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
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: 'AI配置' }).click();

  await expect(page.locator('.notice').getByText('后端异常：AI 配置读取失败，traceId: trace-ai-runtime-failed')).toBeVisible();
  await expect(page.getByText('AI 运行配置加载失败')).toBeVisible();
  await expect(page.getByRole('button', { name: '重试配置读取' })).toBeVisible();
});

test('shows network feedback when AI runtime endpoint is unreachable', async ({ page }) => {
  await routeAdminShell(page);

  await page.route('**/api/v1/ai/runtime', async (route) => {
    await route.abort('internetdisconnected');
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: 'AI配置' }).click();

  await expect(page.locator('.notice').getByText(/^网络请求失败：/)).toBeVisible();
});

test('shows network feedback when asking a knowledge-base question fails', async ({ page }) => {
  await routeAdminShell(page);

  await page.route('**/api/v1/chat/ask', async (route) => {
    await route.abort('connectionrefused');
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '问答' }).click();
  await page.getByLabel('问题').fill('VPN 无法连接应该怎么处理？');
  await page.getByRole('button', { name: '提问' }).click();

  await expect(page.getByText(/^网络请求失败：/)).toBeVisible();
});

test('shows network feedback when ticket submission fails', async ({ page }) => {
  await routeAdminShell(page);

  await page.route('**/api/v1/chat/ask', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-ask',
        data: {
          conversationId: 88,
          answer: '建议先确认账号状态，再检查客户端版本和网络连接。',
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
          conversationId: 88,
          title: '【智能草稿】VPN 无法连接',
          description: '问题描述:\nVPN 无法连接\n\n知识库建议:\n确认账号状态。',
          priority: 'MEDIUM',
          suggestedAssigneeId: null
        }
      })
    });
  });

  await page.route('**/api/v1/tickets', async (route) => {
    await route.abort('connectionrefused');
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '问答' }).click();
  await page.getByLabel('问题').fill('VPN 无法连接应该怎么处理？');
  await page.getByRole('button', { name: '提问' }).click();
  await expect(page.getByText(/已生成回答/)).toBeVisible();

  await page.getByRole('link', { name: '工单' }).click();
  await page.getByRole('button', { name: '生成草稿' }).click();
  await expect(page.getByText('工单草稿已生成')).toBeVisible();
  await page.getByRole('button', { name: '提交工单' }).click();

  await expect(page.getByText(/^网络请求失败：/)).toBeVisible();
});
