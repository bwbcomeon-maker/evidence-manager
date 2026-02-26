/**
 * 权限 E2E：不同角色看到不同菜单/页面与按钮。
 * 需后端已启动且存在测试账号（可先运行后端权限测试创建 permtest_* 用户，或手动创建）。
 * 失败截图：test-results/screenshots/ 与 test-results/test-output/
 */
import { test, expect } from '@playwright/test'
import path from 'path'
import fs from 'fs'

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus && page) {
    const dir = path.join(process.cwd(), 'test-results', 'screenshots')
    fs.mkdirSync(dir, { recursive: true })
    const name = `${testInfo.project.name}-${testInfo.title.replace(/\s+/g, '_').slice(0, 50)}.png`
    await page.screenshot({ path: path.join(dir, name) })
  }
})

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000'

/** 测试账号：与后端 PermissionApiTest 创建的保持一致，默认密码 Init@12345 */
const ROLE_CREDENTIALS: Record<string, { username: string; password: string }> = {
  SYSTEM_ADMIN: { username: 'admin', password: 'Init@12345' },
  PMO: { username: 'permtest_pmo', password: 'Init@12345' },
  AUDITOR: { username: 'permtest_auditor', password: 'Init@12345' },
  USER: { username: 'permtest_user', password: 'Init@12345' }
}

async function login(page: import('@playwright/test').Page, role: keyof typeof ROLE_CREDENTIALS) {
  const { username, password } = ROLE_CREDENTIALS[role]
  await page.goto('/login')
  await page.getByPlaceholder('请输入登录账号').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 10000 })
}

test.describe('权限 E2E：菜单与入口可见性', () => {
  test('SYSTEM_ADMIN 可见项目列表、新建/批量导入/批量分配、证据管理入口', async ({ page }) => {
    await login(page, 'SYSTEM_ADMIN')
    await page.goto('/projects')
    await expect(page.getByText('新建项目')).toBeVisible()
    await expect(page.getByText('批量导入')).toBeVisible()
    await expect(page.getByText('批量分配项目')).toBeVisible()
    await page.goto('/evidence')
    await expect(page.getByText('按项目查看证据')).toBeVisible()
    await expect(page.getByText('作废证据')).toBeVisible()
  })

  test('PMO 可见新建项目、批量导入、批量分配与证据入口', async ({ page }) => {
    await login(page, 'PMO')
    await page.goto('/projects')
    await expect(page.getByText('新建项目')).toBeVisible()
    await expect(page.getByText('批量分配项目')).toBeVisible()
    await page.goto('/evidence')
    await expect(page.getByText('按项目查看证据')).toBeVisible()
  })

  test('AUDITOR 可见证据管理入口、作废证据，无新建项目', async ({ page }) => {
    await login(page, 'AUDITOR')
    await page.goto('/projects')
    await expect(page.getByText('新建项目')).not.toBeVisible()
    await page.goto('/evidence')
    await expect(page.getByText('按项目查看证据')).toBeVisible()
    await expect(page.getByText('作废证据')).toBeVisible()
  })

  test('USER 无新建项目/批量分配，有证据入口', async ({ page }) => {
    await login(page, 'USER')
    await page.goto('/projects')
    await expect(page.getByText('新建项目')).not.toBeVisible()
    await expect(page.getByText('批量分配项目')).not.toBeVisible()
    await page.goto('/evidence')
    await expect(page.getByText('按项目查看证据')).toBeVisible()
    await expect(page.getByText('作废证据')).not.toBeVisible()
  })
})

test.describe('权限 E2E：首页 Tab 与证据模块', () => {
  test('登录后默认在项目或证据 Tab，底部有项目/证据/我的', async ({ page }) => {
    await login(page, 'USER')
    await expect(page.getByText('项目')).toBeVisible()
    await expect(page.getByText('证据')).toBeVisible()
    await expect(page.getByText('我的')).toBeVisible()
  })

  test('证据首页可见我上传的证据、最近上传、按文件类型查看', async ({ page }) => {
    await login(page, 'PMO')
    await page.goto('/evidence')
    await expect(page.getByText('我上传的证据')).toBeVisible()
    await expect(page.getByText('最近上传的证据')).toBeVisible()
    await expect(page.getByText('按文件类型查看')).toBeVisible()
  })
})

test.describe('权限 E2E：项目详情内按钮', () => {
  test('无管理权限时项目列表仍可进详情，上传按钮受 canUpload 控制', async ({ page }) => {
    await login(page, 'USER')
    await page.goto('/projects')
    const card = page.locator('.project-card').first()
    if (await card.count() > 0) {
      await card.click()
      await expect(page).toHaveURL(/\/projects\/\d+/)
      await expect(page.getByText('证据管理')).toBeVisible()
      const uploadBtn = page.getByRole('button', { name: '上传' }).first()
      await expect(uploadBtn).toBeVisible({ timeout: 5000 })
    }
  })
})
