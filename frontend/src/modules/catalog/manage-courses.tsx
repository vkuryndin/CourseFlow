import { CourseActions } from './course-actions';
import { useEffect, useState } from 'react';
import { dateOnly } from '../../shared/datetime';
import { Alert, Button, Stack } from '@mui/material';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../shared/auth';
import { useAll } from '../../shared/api';
import { NamedSearch } from '../../shared/server-search';
import { Heading, Paged, options, type Col } from '../../shared/ui';
import { EditPanel, FormSpec, useAction } from '../../shared/forms';
export function ManageCourses() {
    const a = useAuth(), nav = useNavigate(), [search, setSearch] = useSearchParams(), [edit, E] = useState<FormSpec | null>(null), act = useAction(), role = a.role === 'ADMIN' ? 'admin' : 'teacher';
    const isAdmin = role === 'admin', rawTeacher = search.get('teacherId') || '', teacherId = isAdmin && /^[1-9]\d*$/.test(rawTeacher) && Number.isSafeInteger(Number(rawTeacher)) ? Number(rawTeacher) : undefined;
    const teachers = useAll('/api/users', { role:'TEACHER' }, isAdmin);
    const names = new Map((teachers.data || []).map(t => [String(t.id), t.displayName]));
    const create = () => E({title:'Создание курса', submitLabel:'Создать и открыть конструктор', schema:'CreateCourseRequest', method:'POST', path:'/api/courses', initial:{duration:30,startDate:dateOnly(new Date()),teacherId:isAdmin ? teacherId : a.session?.userId}, hidden:isAdmin ? [] : ['teacherId'], after:r => {if(r?.id != null) nav(`/${role}/courses/${r.id}/editor`);}});
    useEffect(() => { if (!isAdmin || search.get('create') !== '1') return; if (teacherId) create(); const next = new URLSearchParams(search); next.delete('create'); setSearch(next,{replace:true}); }, [isAdmin, search, teacherId]);
    const cols: (string | Col)[] = ['title', ...(isAdmin ? [{key:'teacherId',title:'Преподаватель',render:(r:Record<string,any>) => r.teacherId == null ? '—' : names.get(String(r.teacherId)) || (teachers.isPending ? 'Загрузка…' : 'Недоступен')}] : []), 'duration','startDate','status'];
    return <><Heading title={isAdmin ? 'Все курсы' : 'Мои курсы'} sub={isAdmin && teacherId ? `Курсы преподавателя: ${names.get(String(teacherId)) || 'выбранный преподаватель'}` : undefined} action={isAdmin && teacherId ? <Button component={Link} to="/admin/teachers">К преподавателям</Button> : undefined}/>
        {act.error && <Alert severity="error">{act.error}</Alert>}
        {isAdmin && <Stack direction="row" alignItems="center" gap={2} flexWrap="wrap" mb={2}><NamedSearch path="/api/users" field="displayName" params={{role:'TEACHER'}} id={teacherId} onChange={id => { const next = new URLSearchParams(search); if (id) next.set('teacherId',String(id)); else next.delete('teacherId'); next.delete('create'); setSearch(next); }} label="Преподаватель" placeholder="Все преподаватели"/>{teacherId && <Button onClick={() => setSearch({})}>Все преподаватели</Button>}</Stack>}
        {isAdmin && teachers.isError && <Alert severity="warning" action={<Button onClick={() => void teachers.refetch()}>Повторить</Button>}>Не удалось загрузить имена преподавателей.</Alert>}
        <Paged tableClassName={isAdmin ? "admin-course-list" : undefined} path="/api/courses" params={isAdmin ? (teacherId ? {teacherId} : {}) : {teacherId:a.session?.userId}} cols={cols} filters={[{key:'q',title:'Название'},{key:'status',title:'Статус',options:options(['ACTIVE','ARCHIVED'])}]} sorts={['title','startDate','duration']} create={create} actions={r => isAdmin ? <CourseActions course={r} busy={act.busy} archive={() => void act.run('POST',`/api/courses/${r.id}/archive`,undefined,`Архивировать курс «${r.title}»?`)} remove={() => act.run('DELETE',`/api/courses/${r.id}`,undefined,`Удалить курс «${r.title}»?`)}/> : <><Button component={Link} to={`/${role}/courses/${r.id}/editor`}>Конструктор</Button><Button component={Link} to={`/${role}/courses/${r.id}/gradebook`}>Журнал</Button><Button component={Link} to={`/${role}/courses/${r.id}/students`}>Студенты</Button><Button component={Link} to={`/${role}/courses/${r.id}/schedule`}>Расписание</Button><Button color="warning" disabled={r.status === 'ARCHIVED' || act.busy} onClick={() => void act.run('POST',`/api/courses/${r.id}/archive`,undefined,'Архивировать курс?')}>Архивировать</Button><Button color="error" disabled={act.busy} onClick={() => void act.run('DELETE',`/api/courses/${r.id}`,undefined,`Удалить курс «${r.title}»?`)}>Удалить</Button></>}/><EditPanel spec={edit} onClose={() => E(null)}/>
    </>;
}
