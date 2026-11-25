import {test,expect} from '@playwright/test';
import {pageOf} from './fixtures/pagination';
test('gradebook loads row and column pages without losing existing cells',async({page})=>{
 await page.addInitScript(()=>{sessionStorage.setItem('courseflow.session',JSON.stringify({accessToken:'mock',tokenType:'Bearer',expiresAt:new Date(Date.now()+3600000).toISOString(),userId:1,displayName:'Admin',roles:['ADMIN'],passwordChangeRequired:false}));sessionStorage.setItem('courseflow.role','ADMIN')});
 const calls:URL[]=[];await page.route('**/api/**',r=>{const u=new URL(r.request().url());if(u.pathname.endsWith('/gradebook')){calls.push(u);const rp=Number(u.searchParams.get('page')||0),cp=Number(u.searchParams.get('columnPage')||0);const cols=Array.from({length:cp?1:20},(_,i)=>({type:'ASSIGNMENT',id:cp*20+i+1,title:`Work ${cp*20+i+1}`,moduleId:1,moduleTitle:'Module',maxScore:100}));const students=Array.from({length:rp?1:20},(_,i)=>({studentId:rp*20+i+1,studentName:`Student ${rp*20+i+1}`,results:cols.map(c=>({assessmentId:c.id,type:c.type,score:80,status:'GRADED'}))}));return r.fulfill({json:{courseId:1,columns:cols,students:pageOf(students,rp,20,21),columnPage:cp,columnSize:20,totalColumns:21,totalColumnPages:2}})}if(u.pathname==='/api/courses/1')return r.fulfill({json:{id:1,title:'Course'}});return r.fulfill({json:pageOf([])})});
 await page.goto('/admin/courses/1/gradebook');await expect(page.getByText('Student 1',{exact:true})).toBeAttached();const table=page.locator('.gradebook-scroll');await table.evaluate(el=>{el.scrollTop=el.scrollHeight;el.dispatchEvent(new Event('scroll',{bubbles:true}))});await expect(page.getByText('Student 21',{exact:true})).toBeAttached();await table.evaluate(el=>{el.scrollLeft=el.scrollWidth;el.dispatchEvent(new Event('scroll',{bubbles:true}))});await expect(page.getByText('Work 21',{exact:false}).first()).toBeAttached();await expect(page.getByText('Student 1',{exact:true})).toBeAttached();await expect(page.getByText('Студенты: 21 из 21 · Работы: 21 из 21')).toBeVisible();expect(calls.some(u=>u.searchParams.get('page')==='1'&&u.searchParams.get('columnPage')==='1')).toBe(true);await expect(table.getByRole('row').filter({has:page.getByText('Student 21',{exact:true})}).getByRole('button',{name:'80',exact:true})).toHaveCount(21);
});

test('gradebook groups headers and paints entire result cells with distinct statuses',async({page})=>{
    await page.addInitScript(()=>{sessionStorage.setItem('courseflow.session',JSON.stringify({accessToken:'mock',tokenType:'Bearer',expiresAt:new Date(Date.now()+3600000).toISOString(),userId:1,displayName:'Admin',roles:['ADMIN'],passwordChangeRequired:false}));sessionStorage.setItem('courseflow.role','ADMIN')});
    const columns=[{type:'ASSIGNMENT',id:1,title:'Практика: SQL',moduleId:1,moduleTitle:'Модуль 1',maxScore:20},{type:'QUIZ',id:1,title:'Проверка знаний: SQL',moduleId:1,moduleTitle:'Модуль 1',maxScore:100}];
    const students=[{studentId:1,studentName:'Student 1',results:[{type:'ASSIGNMENT',assessmentId:1,status:'SUBMITTED'},{type:'QUIZ',assessmentId:1,status:'EXPIRED'}]},{studentId:2,studentName:'Student 2',results:[{type:'ASSIGNMENT',assessmentId:1,status:'GRADED',score:17},{type:'QUIZ',assessmentId:1,status:'NOT_STARTED'}]}];
    await page.route('**/api/**',r=>{const u=new URL(r.request().url());if(u.pathname.endsWith('/gradebook'))return r.fulfill({json:{courseId:1,columns,students:pageOf(students),columnPage:0,columnSize:20,totalColumns:2,totalColumnPages:1}});if(u.pathname==='/api/courses/1')return r.fulfill({json:{id:1,title:'Course'}});return r.fulfill({json:pageOf([])})});
    await page.goto('/admin/courses/1/gradebook');const table=page.locator('.gradebook');
    const assignmentGroup=table.getByRole('columnheader',{name:'Домашние задания',exact:true});
    const quizGroup=table.getByRole('columnheader',{name:'Тесты',exact:true});
    await expect(assignmentGroup).toBeVisible();
    await expect(quizGroup).toBeVisible();
    await expect(assignmentGroup).toHaveAttribute('scope','colgroup');
    await expect(quizGroup).toHaveAttribute('scope','colgroup');
    await expect(assignmentGroup).toHaveAttribute('colspan','1');
    await expect(quizGroup).toHaveAttribute('colspan','1');
    await expect(table.locator('colgroup')).toHaveCount(3);
    await expect(table.locator('td.tone-pending')).toContainText('Ожидает проверки');
    await expect(table.locator('td.tone-expired')).toContainText('Время истекло');
    await expect(table.locator('td.tone-high')).toContainText('17');
    await expect(table.locator('td.tone-neutral')).toContainText('Не начат');
    await expect(table.locator('td.tone-high')).toHaveCSS('background-color','rgb(220, 252, 231)');
    await table.getByText('SQL',{exact:true}).first().hover();
    await expect(page.getByRole('tooltip')).toContainText('Практика: SQL · Модуль: Модуль 1');
});
