import {test,expect,type Page} from '@playwright/test';
import {pageOf} from './fixtures/pagination';
const admin={id:1,version:0,displayName:'System Administrator',email:'admin@example.invalid',roles:['ADMIN'],status:'ACTIVE',systemAdmin:true};
const teacher={id:2,version:3,displayName:'Елена Морозова',email:'teacher@example.invalid',roles:['TEACHER'],status:'ACTIVE',systemAdmin:false};
const student={id:3,version:4,displayName:'Иван Петров',email:'student@example.invalid',roles:['STUDENT'],status:'ACTIVE',systemAdmin:false};
const previous={id:4,version:0,displayName:'Другой преподаватель',email:'previous@example.invalid',roles:['TEACHER'],status:'INACTIVE',systemAdmin:false};
const course={id:8,version:6,title:'Курс SQL',teacherId:4,duration:30,startDate:'2026-12-01',status:'ARCHIVED'};
async function setup(page:Page,role='ADMIN'){
    await page.addInitScript(r=>{sessionStorage.setItem('courseflow.session',JSON.stringify({accessToken:'mock',tokenType:'Bearer',expiresAt:new Date(Date.now()+3600000).toISOString(),userId:1,displayName:'Admin',roles:[r],passwordChangeRequired:false}));sessionStorage.setItem('courseflow.role',r)},role);
    const calls:{url:URL;method:string;body:unknown}[]=[];
    await page.route('**/api/**',async r=>{
        const url=new URL(r.request().url()), method=r.request().method(),body=method==='GET'?undefined:r.request().postDataJSON(); calls.push({url,method,body});
        if(url.pathname==='/api/users'&&method==='POST')return r.fulfill({status:201,json:{id:9,...(body as object),version:0,status:'ACTIVE'}});
        if(url.pathname==='/api/users/3/roles'&&method==='PUT')return r.fulfill({json:{...student,roles:['STUDENT','TEACHER'],version:5}});
        if(url.pathname==='/api/courses/8/teacher'&&method==='PUT')return r.fulfill({json:{...course,teacherId:2,version:7}});
        if(url.pathname==='/api/users')return r.fulfill({json:pageOf(url.searchParams.get('role')==='TEACHER'?[teacher,previous]:[admin,teacher,student,previous])});
        if(url.pathname.startsWith('/api/users/'))return r.fulfill({json:[admin,teacher,student,previous].find(x=>url.pathname.endsWith('/'+x.id))||admin});
        if(url.pathname==='/api/courses')return r.fulfill({json:pageOf(!url.searchParams.has('teacherId')||url.searchParams.get('teacherId')==='4'?[course]:[])});
        if(url.pathname==='/api/teachers/2')return r.fulfill({json:teacher});
        return r.fulfill({json:pageOf([])});
    });return calls;
}
async function fillAccount(page:Page){
    const drawer=page.locator('.entity-drawer-paper');
    await drawer.getByLabel(/Имя и фамилия/).fill('Новый пользователь');
    await drawer.getByLabel(/Email/).fill('new@example.invalid');
    await drawer.locator('input[name="password"]').fill('TestPassword123!');
    await drawer.getByRole('button',{name:'Показать пароль'}).click();
    await expect(drawer.locator('input[name="password"]')).toHaveAttribute('type','text');
    await drawer.getByRole('button',{name:'Скрыть пароль'}).click();
    await expect(drawer.locator('input[name="password"]')).toHaveAttribute('type','password');
    return drawer;
}
test('administrator creates a teacher with mandatory TEACHER and no duplicate account',async({page})=>{
    const calls=await setup(page);await page.goto('/admin/teachers');
    await expect(page.getByText(teacher.displayName,{exact:true})).toBeVisible();
    await expect(page.getByText(student.displayName,{exact:true})).toHaveCount(0);
    await page.getByRole('button',{name:'Создать преподавателя',exact:true}).click();
    const drawer=await fillAccount(page), role=drawer.getByRole('checkbox',{name:/Преподаватель/});
    await expect(role).toBeChecked();await expect(role).toBeDisabled();
    await expect(drawer.getByRole('checkbox',{name:/Обязательная смена пароля/})).toBeChecked();
    await drawer.getByRole('button',{name:'Создать преподавателя',exact:true}).click();
    await expect.poll(()=>calls.filter(x=>x.method==='POST'&&x.url.pathname==='/api/users').map(x=>x.body)).toEqual([{displayName:'Новый пользователь',email:'new@example.invalid',password:'TestPassword123!',roles:['TEACHER'],passwordChangeRequired:true}]);
});
test('generic user creation retains optional teacher role and protected admin actions',async({page})=>{
    const calls=await setup(page);await page.goto('/admin/users');
    await page.getByRole('button',{name:'Действия: System Administrator'}).click();
    await expect(page.getByRole('menuitem',{name:'Изменить роли'})).toBeDisabled();
    await expect(page.getByRole('menuitem',{name:'Удалить'})).toBeDisabled();
    await expect(page.getByRole('menuitem',{name:'Деактивировать'})).toBeDisabled();
    await page.keyboard.press('Escape');
    await page.getByRole('button',{name:'Создать пользователя',exact:true}).click();
    const drawer=await fillAccount(page);
    await expect(drawer.getByRole('checkbox',{name:/Студент/})).toBeChecked();
    await drawer.getByRole('checkbox',{name:/Преподаватель/}).check();
    await drawer.getByRole('button',{name:'Создать пользователя',exact:true}).click();
    await expect.poll(()=>calls.filter(x=>x.method==='POST'&&x.url.pathname==='/api/users').map(x=>(x.body as {roles:string[]}).roles)).toEqual([['STUDENT','TEACHER']]);
});
test('administrator sees names on course list and opens teacher-scoped creation',async({page})=>{
    const calls=await setup(page);await page.goto('/admin/courses');
    await expect(page.getByRole('columnheader',{name:'Преподаватель'})).toBeVisible();
    await expect(page.getByText(previous.displayName,{exact:true})).toBeVisible();
    await page.getByRole('link',{name:'Преподаватели',exact:true}).click();
    await page.getByRole('button',{name:`Действия: ${teacher.displayName}`}).click();
    await page.getByRole('menuitem',{name:'Создать курс'}).click();
    await expect(page.locator('.entity-drawer-paper').getByLabel(/Преподаватель/)).toHaveValue(teacher.displayName);
    await expect.poll(()=>calls.some(x=>x.url.pathname==='/api/courses'&&x.url.searchParams.get('teacherId')==='2')).toBe(true);
});
test('administrator reassigns course using current version and grants teacher role without dropping roles',async({page})=>{
    const calls=await setup(page);page.on('dialog',d=>d.accept());await page.goto('/admin/teachers');
    await page.getByRole('button',{name:`Действия: ${teacher.displayName}`}).click();
    await page.getByRole('menuitem',{name:'Назначить на курс'}).click();
    const drawer=page.locator('.entity-drawer-paper');
    await drawer.getByRole('combobox',{name:'Курс'}).click();await page.getByRole('option',{name:course.title}).click();
    await expect(drawer.getByText(`Текущий преподаватель: ${previous.displayName}`)).toBeVisible();
    await drawer.getByRole('button',{name:'Назначить',exact:true}).click();
    await expect.poll(()=>calls.find(x=>x.method==='PUT'&&x.url.pathname==='/api/courses/8/teacher')?.body).toEqual({teacherId:2,version:6});
    await expect(drawer).not.toBeVisible();
    await page.getByRole('button',{name:'Назначить роль преподавателя'}).click();
    await drawer.getByRole('combobox',{name:'Пользователь'}).click();
    await page.getByRole('option',{name:new RegExp(student.displayName)}).click();
    await drawer.getByRole('button',{name:'Назначить роль',exact:true}).click();
    await expect.poll(()=>calls.find(x=>x.method==='PUT'&&x.url.pathname==='/api/users/3/roles')?.body).toEqual({roles:['STUDENT','TEACHER'],version:4});
});
test('student cannot open administrative teachers page',async({page})=>{
    await setup(page,'STUDENT');await page.goto('/admin/teachers');await expect(page).toHaveURL(/\/403$/);
});
