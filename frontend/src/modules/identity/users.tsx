import { useState } from 'react';
import { Alert, Box, Button, Stack, Tooltip, Typography } from '@mui/material';
import { ShieldCheck, UserPlus } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { Data, label } from '../../shared/api';
import { EditPanel, FormSpec, useAction } from '../../shared/forms';
import { Heading, Paged, options, type Filter } from '../../shared/ui';
import { UserActions } from './user-actions';
import { GrantTeacherRole, TeacherAssignment } from './teacher-assignment';

export function Users({teachersOnly = false}: {teachersOnly?: boolean}) {
    const [edit, E] = useState<FormSpec | null>(null), [assignment, setAssignment] = useState<Data | null>(null), [grant, setGrant] = useState(false);
    const act = useAction(), nav = useNavigate();
    const form = (r: Data, roles = false) => E({ title: roles ? `Назначение ролей: ${r.displayName}` : `Редактирование пользователя: ${r.displayName}`, schema: roles ? 'UpdateUserRolesRequest' : 'UpdateUserRequest', method: 'PUT', path: `/api/users/${r.id}${roles ? '/roles' : ''}`, initial: r });
    const filters: Filter[] = [{key:'q',title:'Имя или email'}, ...(!teachersOnly ? [{key:'role',title:'Роль',options:options(['STUDENT','TEACHER','ADMIN'])}] : []), {key:'status',title:'Статус',options:options(['ACTIVE','INACTIVE','DELETED'])}];
    const courseLink = (r: Data, create = false) => `/admin/courses?teacherId=${encodeURIComponent(String(r.id))}${create ? '&create=1' : ''}`;
    return <><Heading title={teachersOnly ? 'Преподаватели' : 'Пользователи'} sub={teachersOnly ? 'Управление преподавателями и их курсами' : 'Управление учётными записями'} action={teachersOnly ? <Button variant="outlined" startIcon={<UserPlus size={17}/>} onClick={() => setGrant(true)}>Назначить роль преподавателя</Button> : undefined}/>
        {act.error && <Alert severity="error">{act.error}</Alert>}
        <Paged path="/api/users" params={teachersOnly ? {role:'TEACHER'} : {}} tableClassName={teachersOnly ? "admin-accounts admin-teachers" : "admin-accounts"} headerSort resettable createLabel={teachersOnly ? 'Создать преподавателя' : 'Создать пользователя'}
            cols={[
                {key:'displayName', title:teachersOnly ? 'Преподаватель' : 'Имя', render:r => <Box><Typography variant="body2" fontWeight={600}>{r.displayName}</Typography>{r.systemAdmin && <Stack direction="row" gap={0.5} alignItems="center"><ShieldCheck size={14}/><Typography variant="caption" color="text.secondary">Системный администратор</Typography></Stack>}</Box>},
                {key:'email', render:r => <Tooltip title={r.email || ''}><Typography variant="body2" className="account-email" tabIndex={0}>{r.email || '—'}</Typography></Tooltip>},
                {key:'roles', render:r => <div className="account-roles">{(r.roles || []).map((role:string) => <span key={role} className={`account-role role-${role.toLowerCase()}`}>{label(role)}</span>)}</div>},
                {key:'status', render:r => <span className={`account-status status-${String(r.status).toLowerCase()}`}><span aria-hidden="true"/>{label(r.status)}</span>},
                ...(teachersOnly ? [{key:'courses',title:'Курсы',render:(r:Data) => r.status === 'DELETED' ? '—' : <Link to={courseLink(r)}>Открыть курсы</Link>}] : [])
            ]}
            filters={filters} sorts={['displayName','email']} create={() => E({title:teachersOnly ? 'Создание преподавателя' : 'Создание пользователя', submitLabel:teachersOnly ? 'Создать преподавателя' : 'Создать пользователя', schema:'AdminCreateUserRequest', method:'POST', path:'/api/users', initial:{roles:[teachersOnly ? 'TEACHER' : 'STUDENT'],passwordChangeRequired:true}, requiredRole:teachersOnly ? 'TEACHER' : undefined})}
            actions={r => <UserActions row={r} busy={act.busy} edit={() => form(r)} roles={() => form(r,true)} toggle={() => void act.run('POST',`/api/users/${r.id}/${r.status === 'ACTIVE' ? 'deactivate' : 'activate'}`,undefined,`${r.status === 'ACTIVE' ? 'Деактивировать' : 'Активировать'} пользователя «${r.displayName}»?`)} reset={() => void act.run('POST',`/api/users/${r.id}/password-reset`,undefined,`Запросить сброс пароля для «${r.displayName}»?`)} remove={() => act.run('DELETE',`/api/users/${r.id}`,undefined,`Удалить пользователя «${r.displayName}»?`)} courses={teachersOnly ? () => nav(courseLink(r)) : undefined} createCourse={teachersOnly ? () => nav(courseLink(r,true)) : undefined} assign={teachersOnly ? () => setAssignment(r) : undefined}/>}/>
        <EditPanel spec={edit} onClose={() => E(null)}/>{assignment && <TeacherAssignment teacher={assignment} close={() => setAssignment(null)}/>} {grant && <GrantTeacherRole close={() => setGrant(false)}/>}
    </>;
}
