import {test,expect} from '@playwright/test';
import {pageOf} from './fixtures/pagination';
test('category server search loads additional pages and filters courses by selected ID',async({page})=>{
 const requests:URL[]=[];const categories=Array.from({length:26},(_,i)=>({id:i+1,name:`Category ${String(i+1).padStart(2,'0')}`}));
 await page.route('**/api/**',r=>{const u=new URL(r.request().url());requests.push(u);const p=Number(u.searchParams.get('page')||0),size=Number(u.searchParams.get('size')||20),q=u.searchParams.get('q')||'';
 if(u.pathname==='/api/categories'){const rows=q==='баз'?[{id:88,name:'Базы данных'}]:categories;return r.fulfill({json:pageOf(rows.slice(p*size,(p+1)*size),p,size,rows.length)})}return r.fulfill({json:pageOf([])})});
 await page.goto('/');const field=page.getByRole('combobox',{name:'Категория'});await field.click();await expect(page.getByRole('option',{name:'Category 01'})).toBeVisible();await page.getByRole('listbox').evaluate(el=>{el.scrollTop=el.scrollHeight;el.dispatchEvent(new Event('scroll',{bubbles:true}))});await expect(page.getByRole('option',{name:'Category 26'})).toBeAttached();await field.fill('баз');await page.getByRole('option',{name:'Базы данных'}).click();await expect.poll(()=>requests.some(u=>u.pathname==='/api/courses'&&u.searchParams.get('categoryId')==='88')).toBe(true);await page.getByRole('button',{name:'Сбросить фильтры'}).first().click();await expect(field).toHaveValue('');expect(requests.some(u=>u.pathname==='/api/categories'&&u.searchParams.get('page')==='1')).toBe(true);
});
