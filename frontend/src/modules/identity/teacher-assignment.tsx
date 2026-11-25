import { useState } from 'react';
import { Alert, Button, Stack, Typography } from '@mui/material';
import { Data, useGet } from '../../shared/api';
import { ServerSearch } from '../../shared/server-search';
import { useAction } from '../../shared/forms';
import { Panel, State } from '../../shared/ui';

type CourseChoice = { id: number; title: string; teacherId: number; version: number };
type UserChoice = { id: number; displayName: string; email: string; roles: string[]; status: string; systemAdmin: boolean; version: number };
export function TeacherAssignment({ teacher, close }: { teacher: Data; close: () => void }) {
    const [course, setCourse] = useState<CourseChoice | null>(null);
    const current = useGet(`/api/users/${course?.teacherId}`, {}, !!course?.teacherId);
    const act = useAction();
    const same = course && String(course.teacherId) === String(teacher.id);
    return <Panel open title={`Назначение на курс: ${teacher.displayName}`} onClose={() => {if (!act.busy) close();}} wide><Stack gap={2}>
        <Typography color="text.secondary">Выберите существующий курс. Его текущий преподаватель будет заменён; остальные данные курса сохранятся.</Typography>
        <ServerSearch<CourseChoice> path="/api/courses" label="Курс" value={course} onChange={setCourse} name={c => c.title} disabled={act.busy} fullWidth/>
        {course && <State query={current}><Typography>Текущий преподаватель: {current.data?.displayName || 'Недоступен'}</Typography></State>}
        {same && <Alert severity="info">Этот преподаватель уже назначен на курс.</Alert>}
        {act.error && <Alert severity="error">{act.error}</Alert>}
        <Stack direction="row" gap={2}><Button variant="contained" disabled={!course || same || !Number.isInteger(course.version) || act.busy || current.isLoading || current.isError} onClick={async () => { if (!course || same) return; if (await act.run('PUT', `/api/courses/${course.id}/teacher`, {teacherId: teacher.id, version: course.version}, `Назначить «${teacher.displayName}» преподавателем курса «${course.title}» вместо «${current.data?.displayName || 'текущего преподавателя'}»?`)) close(); }}>Назначить</Button><Button onClick={close} disabled={act.busy}>Отмена</Button></Stack>
    </Stack></Panel>;
}
export function GrantTeacherRole({ close }: { close: () => void }) {
    const [user, setUser] = useState<UserChoice | null>(null);
    const act = useAction();
    const eligible = user && Array.isArray(user.roles) && !user.roles.includes('TEACHER') && !user.systemAdmin && user.status !== 'DELETED' && Number.isInteger(user.version);
    return <Panel open title="Назначение роли преподавателя" onClose={() => {if (!act.busy) close();}} wide><Stack gap={2}>
        <Typography color="text.secondary">Выберите существующую учётную запись. Все её текущие роли сохранятся.</Typography>
        <ServerSearch<UserChoice> path="/api/users" label="Пользователь" value={user} onChange={setUser} name={u => `${u.displayName} · ${u.email}`} disabled={act.busy} fullWidth/>
        {user && !eligible && <Alert severity="info">Этому пользователю нельзя назначить роль преподавателя или роль уже назначена.</Alert>}
        {act.error && <Alert severity="error">{act.error}</Alert>}
        <Stack direction="row" gap={2}><Button variant="contained" disabled={!eligible || act.busy} onClick={async () => { if (!user || !eligible) return; if (await act.run('PUT', `/api/users/${user.id}/roles`, {roles: [...user.roles, 'TEACHER'], version: user.version}, `Назначить роль преподавателя пользователю «${user.displayName}»?`)) close(); }}>Назначить роль</Button><Button onClick={close} disabled={act.busy}>Отмена</Button></Stack>
    </Stack></Panel>;
}
