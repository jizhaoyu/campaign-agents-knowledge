import { expect, test } from '@playwright/test';

test('runs the core knowledge-to-ticket workflow', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: '企业知识库到工单闭环的 AI 工作台' })).toBeVisible();
  await page.getByRole('button', { name: '进入工作台' }).click();

  await expect(page.getByRole('heading', { name: '知识驱动的工单协同台' })).toBeVisible();

  const suffix = Date.now();
  await page.getByLabel('新建知识库').fill(`E2E Support KB ${suffix}`);
  await page.getByPlaceholder('知识库说明').fill('Created by Playwright');
  await page.getByRole('button', { name: '创建知识库' }).click();
  await expect(page.getByText(`知识库已创建：E2E Support KB ${suffix}`)).toBeVisible();

  const kbValue = await page
    .getByLabel('选择知识库')
    .locator('option')
    .filter({ hasText: `E2E Support KB ${suffix}` })
    .getAttribute('value');
  expect(kbValue).toBeTruthy();
  await page.getByLabel('选择知识库').selectOption(kbValue!);
  await page.getByLabel('上传 Markdown / TXT / PDF / DOCX').setInputFiles({
    name: 'vpn-guide.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('VPN 连接失败时，先确认账号状态，再检查客户端版本和网络连接。', 'utf-8')
  });
  await page.getByRole('button', { name: '上传并后台索引' }).click();
  await expect(page.getByText(/文档已索引/)).toBeVisible();
  await expect(page.getByRole('heading', { name: '文档管理' })).toBeVisible();
  await page.getByLabel('搜索文档').fill('vpn');
  await page.getByLabel('索引状态').selectOption('SUCCESS');
  const documentRow = page.locator('.document-row').filter({ hasText: 'vpn-guide.md' });
  await expect(documentRow.getByText('解析 SUCCESS / 索引 SUCCESS')).toBeVisible();
  await expect(documentRow.getByRole('button', { name: '重建索引' })).toBeEnabled();
  await expect(documentRow.getByRole('button', { name: '删除' })).toBeEnabled();

  await page.getByLabel('问题').fill('VPN 无法连接应该怎么处理？');
  await page.getByRole('button', { name: '提问' }).click();
  await expect(page.getByText(/已生成回答/)).toBeVisible();
  await expect(page.locator('#问答').getByText('vpn-guide.md')).toBeVisible();
  await expect(page.getByRole('heading', { name: '最近问答' })).toBeVisible();

  await page.getByRole('button', { name: '生成草稿' }).click();
  await expect(page.getByText('工单草稿已生成')).toBeVisible();
  await page.getByRole('button', { name: '提交工单' }).click();
  await expect(page.getByText(/工单已提交/)).toBeVisible();
});

test('returns to login when the stored token is invalid', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: '进入工作台' }).click();
  await expect(page.getByRole('heading', { name: '知识驱动的工单协同台' })).toBeVisible();

  await page.evaluate(() => {
    const savedSession = JSON.parse(localStorage.getItem('kta-session') || '{}');
    localStorage.setItem('kta-session', JSON.stringify({ ...savedSession, accessToken: 'invalid-token' }));
  });
  await page.reload();

  await expect(page.getByRole('heading', { name: '企业知识库到工单闭环的 AI 工作台' })).toBeVisible();
  await expect(page.getByText('登录态已失效，请重新登录')).toBeVisible();
});
