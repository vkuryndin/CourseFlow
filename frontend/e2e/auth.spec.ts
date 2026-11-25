import {test, expect, type Page} from '@playwright/test';

const email = 'student@example.test';
const password = 'ExamplePass123!';

async function mockAuth(page: Page) {
  const calls: {path: string; body: unknown}[] = [];
  await page.route('**/api/**', async route => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    if (req.method() === 'POST' && path.startsWith('/api/auth/')) {
      calls.push({path, body: req.postDataJSON()});
      if (path === '/api/auth/login') {
        return route.fulfill({json: {
          accessToken: 'mock-token', tokenType: 'Bearer',
          expiresAt: new Date(Date.now() + 3600000).toISOString(),
          userId: 1, displayName: 'Test Student', roles: ['STUDENT'],
          passwordChangeRequired: false,
        }});
      }
      return route.fulfill({status: 200, json: {}});
    }
    return route.fulfill({json: {}});
  });
  return calls;
}

async function expectLogin(page: Page) {
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', {name: 'Вход в систему'})).toBeVisible();
  await expect(page.getByLabel('Email')).toHaveValue('');
  await expect(page.getByLabel('Пароль', {exact: false})).toHaveValue('');
  await expect(page.getByRole('button', {name: 'Войти', exact: true})).toBeEnabled();
  await expect(page.getByRole('alert')).toHaveCount(0);
}

test('UI-048 registration success link restores login and allows submission', async ({page}) => {
  const calls = await mockAuth(page);
  await page.goto('/login');
  await page.getByRole('link', {name: 'Зарегистрироваться'}).click();
  await page.getByLabel('Имя').fill('Test Student');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Пароль').fill(password);
  await page.getByRole('button', {name: 'Зарегистрироваться'}).click();
  await page.getByRole('alert').getByRole('link', {name: 'Войти'}).click();
  await expectLogin(page);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Пароль').fill(password);
  await page.getByRole('button', {name: 'Войти'}).click();
  await expect(page).toHaveURL(/\/student$/);
  expect(calls).toContainEqual({path: '/api/auth/login', body: {email, password}});
});

test('UI-049 forgot-password success does not persist on login or next request', async ({page}) => {
  const calls = await mockAuth(page);
  await page.goto('/login');
  await page.getByRole('link', {name: 'Забыли пароль?'}).click();
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', {name: 'Отправить запрос'}).click();
  await expect(page.getByRole('alert')).toContainText('запрос на восстановление принят');
  await page.getByRole('alert').getByRole('link', {name: 'Войти'}).click();
  await expectLogin(page);
  await page.getByRole('link', {name: 'Забыли пароль?'}).click();
  await expect(page.getByLabel('Email')).toHaveValue('');
  await expect(page.getByRole('button', {name: 'Отправить запрос'})).toBeEnabled();
  expect(calls).toContainEqual({path: '/api/auth/forgot-password', body: {email}});
});

test('UI-049 reset link submits token and returns to a fresh login', async ({page}) => {
  const calls = await mockAuth(page);
  await page.goto('/reset-password?token=mock-reset-token');
  await expect(page.getByLabel('Токен')).toHaveCount(0);
  await page.getByLabel('Новый пароль').fill(password);
  await page.getByRole('button', {name: 'Сменить пароль'}).click();
  await page.getByRole('alert').getByRole('link', {name: 'Войти'}).click();
  await expectLogin(page);
  expect(calls).toContainEqual({path: '/api/auth/reset-password', body: {token: 'mock-reset-token', newPassword: password}});
});

test('UI-049 changing auth route and browser history clears previous form values', async ({page}) => {
  await mockAuth(page);
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Пароль').fill(password);
  await page.getByRole('link', {name: 'Зарегистрироваться'}).click();
  await expect(page.getByLabel('Имя')).toHaveValue('');
  await expect(page.getByLabel('Email')).toHaveValue('');
  await expect(page.getByLabel('Пароль')).toHaveValue('');
  await page.goBack();
  await expectLogin(page);
  await page.goForward();
  await expect(page.getByRole('heading', {name: 'Регистрация'})).toBeVisible();
  await expect(page.getByLabel('Email')).toHaveValue('');
});

test('UI-049 mandatory password change returns to login without stale state', async ({page}) => {
  const calls = await mockAuth(page);
  await page.addInitScript(() => {
    sessionStorage.setItem('courseflow.session', JSON.stringify({accessToken: 'mock', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(), userId: 1, displayName: 'Test Student', roles: ['STUDENT'], passwordChangeRequired: true}));
    sessionStorage.setItem('courseflow.role', 'STUDENT');
  });
  await page.goto('/change-password');
  await page.getByLabel('Текущий пароль').fill(password);
  await page.getByLabel('Новый пароль').fill('OtherPass123!');
  await page.getByRole('button', {name: 'Сменить пароль'}).click();
  await expectLogin(page);
  expect(calls.some(c => c.path === '/api/auth/change-password')).toBe(true);
  expect(calls.some(c => c.path === '/api/auth/logout')).toBe(true);
  expect(await page.evaluate(() => sessionStorage.getItem('courseflow.session'))).toBeNull();
});
