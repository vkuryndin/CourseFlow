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
      if (path === '/api/auth/reset-password') {
        expect(new URL(req.url()).search).toBe('');
        return route.fulfill({status: 204});
      }
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
  await expect(page.locator('input[name="password"]')).toHaveValue('');
  await expect(page.getByRole('button', {name: 'Войти', exact: true})).toBeEnabled();
  await expect(page.getByRole('alert')).toHaveCount(0);
}

test('UI-048 registration success link restores login and allows submission', async ({page}) => {
  const calls = await mockAuth(page);
  await page.goto('/login');
  await page.getByRole('link', {name: 'Зарегистрироваться'}).click();
  await page.getByLabel('Имя').fill('Test Student');
  await page.getByLabel('Email').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', {name: 'Зарегистрироваться'}).click();
  await page.getByRole('alert').getByRole('link', {name: 'Войти'}).click();
  await expectLogin(page);
  await page.getByLabel('Email').fill(email);
  await page.locator('input[name="password"]').fill(password);
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

test('reset link consumes token and automatically authenticates using remembered email', async ({page}) => {
  const calls = await mockAuth(page);
  await page.goto('/forgot-password');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', {name: 'Отправить запрос'}).click();
  await expect(page.getByRole('alert')).toContainText('запрос на восстановление принят');
  await page.goto('/reset-password?token=mock-reset-token');
  await expect(page.getByLabel('Email')).toHaveValue(email);
  await expect(page.getByLabel('Токен')).toHaveCount(0);
  await page.getByLabel('Новый пароль').fill(password);
  await page.getByRole('button', {name: 'Сменить пароль'}).click();
  await expect(page).toHaveURL(/\/student$/);
  await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem('courseflow.session') || 'null')?.accessToken)).toBe('mock-token');
  expect(calls.slice(-2)).toEqual([
    {path: '/api/auth/reset-password', body: {token: 'mock-reset-token', newPassword: password}},
    {path: '/api/auth/login', body: {email, password}},
  ]);
  expect(await page.evaluate(() => localStorage.getItem('courseflow.recovery-email'))).toBeNull();
});

test('UI-049 changing auth route and browser history clears previous form values', async ({page}) => {
  await mockAuth(page);
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('link', {name: 'Зарегистрироваться'}).click();
  await expect(page.getByLabel('Имя')).toHaveValue('');
  await expect(page.getByLabel('Email')).toHaveValue('');
  await expect(page.locator('input[name="password"]')).toHaveValue('');
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
  const current = page.locator('input[name="currentPassword"]');
  const next = page.locator('input[name="newPassword"]');
  await current.fill(password);
  await page.getByRole('button',{name:'Показать пароль',exact:true}).first().click();
  await expect(current).toHaveAttribute('type','text');
  await expect(next).toHaveAttribute('type','password');
  await next.fill('OtherPass123!');
  await expect(current).toHaveAttribute('type','password');
  await page.getByRole('button', {name: 'Сменить пароль'}).click();
  await expectLogin(page);
  expect(calls.some(c => c.path === '/api/auth/change-password')).toBe(true);
  expect(calls.some(c => c.path === '/api/auth/logout')).toBe(true);
  expect(await page.evaluate(() => sessionStorage.getItem('courseflow.session'))).toBeNull();
});


test('password visibility is hidden on blur, retained value and keyboard accessible', async ({page}) => {
  await mockAuth(page);
  await page.goto('/login');
  const input = page.locator('input[name="password"]');
  await input.fill(password);
  await expect(input).toHaveAttribute('type','password');
  await page.getByRole('button',{name:'Показать пароль',exact:true}).click();
  await expect(input).toHaveAttribute('type','text');
  await page.getByLabel('Email').focus();
  await expect(input).toHaveAttribute('type','password');
  await expect(input).toHaveValue(password);
  await input.focus();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button',{name:'Показать пароль',exact:true})).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(input).toHaveAttribute('type','text');
  await page.keyboard.press('Tab');
  await expect(input).toHaveAttribute('type','password');
});

test('failed recovery does not create a session or attempt login', async ({page}) => {
  const calls = await mockAuth(page);
  await page.route('**/api/auth/reset-password', r => r.fulfill({status:400,json:{message:'Invalid or expired reset token'}}));
  await page.goto('/reset-password?token=expired-token');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Новый пароль').fill(password);
  await page.getByRole('button',{name:'Сменить пароль'}).click();
  await expect(page.getByRole('alert')).toContainText('Ссылка восстановления недействительна');
  await expect(page).toHaveURL(/reset-password/);
  expect(calls.filter(c => c.path === '/api/auth/login')).toHaveLength(0);
  expect(await page.evaluate(()=>sessionStorage.getItem('courseflow.session'))).toBeNull();
});

test('recovery on another browser accepts the email and logs in with the existing API', async ({page}) => {
  const calls = await mockAuth(page);
  await page.goto('/reset-password?token=other-device');
  await expect(page.getByLabel('Email')).toHaveValue('');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Новый пароль').fill(password);
  await page.getByRole('button',{name:'Сменить пароль'}).click();
  await expect(page).toHaveURL(/\/student$/);
  expect(calls).toEqual([
    {path:'/api/auth/reset-password',body:{token:'other-device',newPassword:password}},
    {path:'/api/auth/login',body:{email,password}},
  ]);
});

test('successful reset and failed login offer ordinary login without reusing the reset token', async ({page}) => {
  const calls = await mockAuth(page);
  let loginCount = 0;
  await page.route('**/api/auth/login', r => { loginCount++; return r.fulfill({status:401,json:{message:'Invalid email or password'}}); });
  await page.goto('/reset-password?token=valid-token');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Новый пароль').fill(password);
  await page.getByRole('button',{name:'Сменить пароль'}).click();
  await expect(page.getByText('Пароль изменён.',{exact:true})).toBeVisible();
  await expect(page.getByText(/Повторное восстановление не требуется/)).toBeVisible();
  await expect(page.getByRole('button',{name:'Сменить пароль'})).toHaveCount(0);
  expect(calls.filter(c=>c.path==='/api/auth/reset-password')).toHaveLength(1);
  expect(loginCount).toBe(1);
  expect(await page.evaluate(()=>sessionStorage.getItem('courseflow.session'))).toBeNull();
  await page.getByRole('link',{name:'Войти с новым паролем'}).click();
  await expectLogin(page);
});
