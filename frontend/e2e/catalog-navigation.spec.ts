import {test,expect} from '@playwright/test';
import {setup} from './fixtures/course-schedule';
test('student catalog and details retain sidebar; public catalog retains hero',async({page})=>{await setup(page,'STUDENT');await page.goto('/student/catalog');await expect(page.getByRole('heading',{name:'Каталог курсов'})).toBeVisible();await expect(page.locator('.catalog-hero')).toHaveCount(0);await expect(page.locator('.sidebar-inner').getByText('Публичный каталог',{exact:true})).toHaveCount(0);await page.getByRole('link',{name:'Подробнее →'}).first().click();await expect(page).toHaveURL(/\/student\/catalog\/courses\/1$/);await expect(page.locator('.desktop-sidebar')).toBeVisible();await page.getByRole('link',{name:'Преподаватель теста'}).click();await expect(page).toHaveURL(/\/student\/catalog\/teachers\/3$/);await expect(page.locator('.desktop-sidebar')).toBeVisible();await page.goto('/catalog');await expect(page.locator('.catalog-hero')).toBeVisible()});

test('guest still has the public hero and favicon files are served',async({page,request})=>{await setup(page);await page.goto('/');await expect(page.locator('.hero-art')).toBeVisible();await expect(page.locator('link[rel="icon"][type="image/svg+xml"]')).toHaveAttribute('href','/favicon.svg?v=125');for(const file of ['favicon.svg','favicon.ico','apple-touch-icon.png']){const r=await request.get('/'+file+'?v=125');expect(r.ok()).toBe(true);expect(r.headers()['content-type']).toMatch(/image\//)}await page.goto('/login');await expect(page.locator('link[rel="icon"][type="image/svg+xml"]')).toHaveCount(1)});


test('admin catalog and course and teacher details stay in administrative workspace',async({page})=>{
    await setup(page,'ADMIN');
    await page.goto('/admin/catalog');
    await expect(page.locator('.desktop-sidebar').getByRole('link',{name:'Каталог курсов'})).toHaveClass(/active/);
    await expect(page.locator('.catalog-hero')).toHaveCount(0);
    await page.getByRole('link',{name:'Подробнее →'}).first().click();
    await expect(page).toHaveURL(/\/admin\/catalog\/courses\/1$/);
    await expect(page.getByRole('link',{name:'Управление курсом'})).toHaveAttribute('href','/admin/courses/1/editor');
    await expect(page.getByRole('button',{name:'Записаться на курс'})).toHaveCount(0);
    await page.getByRole('link',{name:'Преподаватель теста'}).click();
    await expect(page).toHaveURL(/\/admin\/catalog\/teachers\/3$/);
    await expect(page.locator('.desktop-sidebar')).toBeVisible();
    await page.getByRole('link',{name:'← К каталогу'}).click();
    await expect(page).toHaveURL(/\/admin\/catalog$/);
});

test('admin course list exposes management actions through a compact menu', async({page})=>{
    await setup(page,'ADMIN');
    await page.goto('/admin/courses');
    const actions=page.getByRole('button',{name:'Действия: Сетевая безопасность'});
    await actions.click();
    const menu=page.getByRole('menu');
    for(const [name,path] of [['Конструктор','editor'],['Журнал','gradebook'],['Студенты','students'],['Расписание','schedule']]) {
        await expect(menu.getByRole('menuitem',{name})).toHaveAttribute('href',`/admin/courses/1/${path}`);
    }
    await expect(menu.getByRole('menuitem',{name:'Архивировать'})).toBeEnabled();
    await expect(menu.getByRole('menuitem',{name:'Удалить'})).toBeEnabled();
    await page.keyboard.press('Escape');
    await expect(menu).toHaveCount(0);
});
