import {lazy,useState} from 'react';
import {Button} from '@mui/material';
import {Link} from 'react-router-dom';
import {useAuth} from '../../shared/auth';
import {Data,useGet,all} from '../../shared/api';
import {Heading,State} from '../../shared/ui';
import type {ComponentType} from 'react';
import {useQuery} from '@tanstack/react-query';
import {StudentDashboard} from './student-dashboard';
import {TeacherDashboard} from './teacher-dashboard';
const AdminDashboard = lazy(() => import('./admin-dashboard').then(m => ({default:m.AdminDashboard}))); 
export function DashboardPage({role,GradePanel,AttemptPanel}:{role:string;GradePanel:ComponentType<{row:Data|null;assignment:Data|null;close:()=>void}>;AttemptPanel:ComponentType<{selection:Data|null;close:()=>void}>}){const a=useAuth(),q=useGet(`/api/dashboard/${role}`);const system=useGet('/api/system/status',{},role==='admin');const [pending,P]=useState<Data|null>(null),[attempt,A]=useState<Data|null>(null);const work=useQuery({queryKey:['dashboard-submission',pending?.submissionId],queryFn:async()=>{const rows=await all(`/api/assignments/${pending?.assignmentId}/submissions`);const row=rows.find(r=>r.id===pending?.submissionId);return row?{...row,studentName:pending?.studentName}:null},enabled:!!pending,retry:false});const d=q.data;return <div className="role-dashboard"><Heading title={role==='student'?`Добро пожаловать, ${a.session?.displayName}!`:role==='teacher'?'Кабинет преподавателя':'Обзор платформы'} sub={role==='student'?'Продолжайте обучение и следите за результатами.':role==='teacher'?'Управление учебным процессом':'Администрирование CourseFlow'} action={role!=='student'&&<Button component={Link} variant="contained" to={`/${role}/courses`}>Управление курсами</Button>}/><State query={q}>{d&& (role==='student'?<StudentDashboard d={d}/>:role==='teacher'?<TeacherDashboard d={d} P={P} A={A}/>:<AdminDashboard d={d} system={system}/>)}</State>{pending&&<State query={work}><GradePanel row={work.data||null} assignment={{id:pending.assignmentId,title:pending.assignmentTitle,maxScore:pending.maxScore,courseId:pending.courseId,courseTitle:pending.courseTitle,lessonId:pending.lessonId}} close={()=>P(null)}/></State>}<AttemptPanel selection={attempt} close={()=>A(null)}/></div>}
