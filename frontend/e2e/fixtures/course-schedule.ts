import {type Page} from '@playwright/test';
const pageOf=(content:unknown[])=>({content,page:0,size:100,totalElements:content.length,totalPages:content.length?1:0,first:true,last:true});
const monday=new Date();
monday.setDate(monday.getDate()-(monday.getDay()+6)%7);
monday.setHours(0,0,0,0);
const at=(offset:number,h:number,m=0)=>{const d=new Date(monday);d.setDate(d.getDate()+offset);d.setHours(h,m,0,0);return d.toISOString()};
const course={id:1,title:'Сетевая безопасность',description:'Учебный курс',categoryId:2,teacherId:3,duration:30,startDate:'2026-11-01',status:'ACTIVE'};
const module={id:4,title:'Модель сети',position:1,description:'Основы'};
const lesson={id:5,title:'Адреса и маршруты',position:1,content:'<p>Материалы</p>'};
const assignment={id:6,lessonId:5,title:'Практика: маршрутизация',description:'Полный текст задания',dueAt:at(2,21),maxScore:100,version:0};
const late={...assignment,id:7,title:'Последний дедлайн',dueAt:at(4,23,59)};
const session={id:8,lessonId:5,startsAt:at(1,10),endsAt:at(1,11,30),format:'OFFLINE',version:0};
const earlier={...session,id:9,startsAt:at(3,6,30),endsAt:at(3,7)};
const overlap={...session,id:10,startsAt:at(1,10,15),endsAt:at(1,10,45)};
async function setup(page:Page,role?:string){
 if(role)await page.addInitScript(r=>{sessionStorage.setItem('courseflow.session',JSON.stringify({accessToken:'mock',tokenType:'Bearer',expiresAt:new Date(Date.now()+3600000).toISOString(),userId:r==='TEACHER'?3:1,displayName:'Test',roles:[r],passwordChangeRequired:false}));sessionStorage.setItem('courseflow.role',r)},role);
 await page.route('**/api/**',r=>{const p=new URL(r.request().url()).pathname;
 if(p.endsWith('/quiz')||p.endsWith('/reviews/me')||p.endsWith('/submissions/me'))return r.fulfill({status:404,json:{message:'Not found'}});
 const data:Record<string,unknown>={'/api/courses':pageOf([course]),'/api/courses/1':course,'/api/teachers/3':{id:3,displayName:'Преподаватель теста'},'/api/categories/2':{id:2,name:'Сети'},'/api/courses/1/modules':pageOf([module]),'/api/courses/1/modules/4/lessons':pageOf([lesson]),'/api/courses/1/modules/4/lessons/5':lesson,'/api/lessons/5/assignments':pageOf([assignment,late]),'/api/lessons/5/sessions':pageOf([session,earlier,overlap]),'/api/users/me/assignments':pageOf([{...assignment,assignmentId:6,status:'OVERDUE',courseId:1,courseTitle:course.title,lessonTitle:lesson.title},{...late,assignmentId:7,status:'GRADED',score:90,courseId:1}]),'/api/users/me/enrollments':pageOf([{courseId:1,courseTitle:course.title,courseStatus:'ACTIVE',status:'ACTIVE'}]),'/api/courses/1/enrollments/me':{status:'ACTIVE'},'/api/courses/1/reviews/rating':{averageRating:0,reviewCount:0}};
 return r.fulfill({json:data[p]??pageOf([])})});
}
export {pageOf,monday,at,course,module,lesson,assignment,late,session,earlier,overlap,setup};
