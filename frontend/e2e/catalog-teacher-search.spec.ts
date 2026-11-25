import {test,expect} from '@playwright/test';

test('public teacher search is paginated on the server and selection filters courses',async({page})=>{
  const requests:{path:string;q:string;page:string;teacher:string;size:string}[]=[];
  const teachers=Array.from({length:24},(_,i)=>({id:i+1,displayName:`Teacher ${String(i+1).padStart(2,'0')}`}));
  const found={id:80,displayName:'Анна Орлова'};
  const pageOf=(content:unknown[],index=0,size=20,total=content.length)=>({content,page:index,size,totalElements:total,totalPages:Math.ceil(total/size),first:index===0,last:(index+1)*size>=total});
  await page.route('**/api/**',route=>{
    const url=new URL(route.request().url()),path=url.pathname,q=url.searchParams.get('q')||'',index=Number(url.searchParams.get('page')||0),size=Number(url.searchParams.get('size')||20);
    requests.push({path,q,page:String(index),teacher:url.searchParams.get('teacherId')||'',size:String(size)});
    if(path==='/api/teachers'){
      const items=q==='орл'?[found]:q==='absent'?[]:teachers;
      return route.fulfill({json:pageOf(items.slice(index*size,(index+1)*size),index,size,items.length)});
    }
    return route.fulfill({json:pageOf([])});
  });
  await page.goto('/');
  const field=page.getByRole('combobox',{name:'Преподаватель'});
  await field.click();
  await expect(page.getByRole('option',{name:'Teacher 01'})).toBeVisible();
  const list=page.getByRole('listbox');
  await list.evaluate(el=>{el.scrollTop=el.scrollHeight;el.dispatchEvent(new Event('scroll',{bubbles:true}))});
  await expect(page.getByRole('option',{name:'Teacher 24'})).toBeAttached();
  await field.fill('орл');
  await expect(page.getByRole('option',{name:'Анна Орлова'})).toBeVisible();
  await page.getByRole('option',{name:'Анна Орлова'}).click();
  await expect(field).toHaveValue('Анна Орлова');
  await expect.poll(()=>requests.some(r=>r.path==='/api/courses'&&r.teacher==='80')).toBe(true);
  await page.getByRole('button',{name:'Сбросить фильтры'}).first().click();
  await expect(field).toHaveValue('');
  await field.click();await field.fill('absent');
  await expect(page.getByText('Преподаватели не найдены')).toBeVisible();
  expect(requests.some(r=>r.path==='/api/teachers'&&r.page==='1')).toBe(true);
  expect(requests.some(r=>r.path==='/api/teachers'&&r.q==='орл')).toBe(true);
  expect(requests.filter(r=>r.path==='/api/courses').every(r=>r.size==='12')).toBe(true);
});
