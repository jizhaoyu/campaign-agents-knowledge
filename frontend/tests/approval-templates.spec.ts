import { expect, test } from '@playwright/test';

test('applies editable approval comment templates when deciding tasks', async ({ page }) => {
  const approvalRequests: Array<{ path: string; body: unknown }> = [];

  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-login-approver',
        data: {
          accessToken: 'approver-access-token',
          refreshToken: 'approver-refresh-token',
          expiresIn: 7200,
          refreshExpiresIn: 604800,
          username: 'approver',
          displayName: '审批人',
          roles: ['APPROVER'],
          permissions: ['chat:use', 'ticket:similar:read', 'approval:review']
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
        data: [
          {
            code: 'APPROVE_EVIDENCE_SUFFICIENT',
            action: 'approve',
            label: '证据充分，批准开单',
            comment: '已核对知识库引用和工单内容，证据充分，同意创建正式工单。'
          },
          {
            code: 'REJECT_EVIDENCE_INSUFFICIENT',
            action: 'reject',
            label: '证据不足，退回补充',
            comment: '当前引用证据不足以支撑高优先级开单，请补充排障依据后重新提交。'
          }
        ]
      })
    });
  });

  await page.route('**/api/v1/approvals/91/approve', async (route) => {
    approvalRequests.push({
      path: new URL(route.request().url()).pathname,
      body: route.request().postDataJSON()
    });
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'OK',
        message: 'success',
        traceId: 'trace-approve',
        data: {
          id: 91,
          targetType: 'TICKET_CREATE',
          targetId: 302,
          approverId: 4,
          status: 'APPROVED',
          comment: '已核对知识库引用和工单内容，证据充分，同意创建正式工单。SLA 4 小时内跟进。'
        }
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '进入工作台' }).click();
  await page.getByRole('link', { name: '审批' }).click();
  await page.getByRole('button', { name: '刷新待审批' }).click();

  await page.getByLabel('通过模板').selectOption('APPROVE_EVIDENCE_SUFFICIENT');
  const approveComment = page.getByLabel('审批 #91 通过备注');
  await expect(approveComment).toHaveValue('已核对知识库引用和工单内容，证据充分，同意创建正式工单。');
  await approveComment.fill('已核对知识库引用和工单内容，证据充分，同意创建正式工单。SLA 4 小时内跟进。');
  await page.getByRole('button', { name: '通过' }).click();

  expect(approvalRequests).toEqual([
    {
      path: '/api/v1/approvals/91/approve',
      body: {
        templateCode: 'APPROVE_EVIDENCE_SUFFICIENT',
        comment: '已核对知识库引用和工单内容，证据充分，同意创建正式工单。SLA 4 小时内跟进。'
      }
    }
  ]);
});
