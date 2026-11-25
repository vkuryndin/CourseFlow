import {type Page} from '@playwright/test';
const paged=(content:unknown[])=>({content,page:0,size:100,totalElements:content.length,totalPages:content.length?1:0,first:true,last:true});
const course={id:1,title:'Сетевая безопасность',status:'ACTIVE',categoryId:2,teacherId:3,duration:30,startDate:'2026-11-01'};
const assignment={id:6,assignmentId:6,courseId:1,courseTitle:course.title,lessonId:5,title:'Практика',description:'Условие работы',dueAt:new Date(Date.now()+86400000).toISOString(),maxScore:100};
const work={id:9,assignmentId:6,studentId:185,content:'Старый ответ',version:0,status:'SUBMITTED',submittedAt:new Date().toISOString()};
async function login(page:Page,role:string){await page.addInitScript(r=>{sessionStorage.setItem('courseflow.session',JSON.stringify({accessToken:'mock',tokenType:'Bearer',expiresAt:new Date(Date.now()+3600000).toISOString(),userId:r==='TEACHER'?3:185,displayName:'Тест',roles:[r],passwordChangeRequired:false}));sessionStorage.setItem('courseflow.role',r)},role)}
export {paged,course,assignment,work,login};
