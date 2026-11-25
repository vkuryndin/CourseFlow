import { useEffect, useState } from 'react';
import { CalendarDays, Pencil } from 'lucide-react';
import { formatDate } from '../../shared/datetime';
import { Button, MenuItem, Stack, TextField, Typography, Alert, Paper, FormControlLabel, Switch } from '@mui/material';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import listPlugin from '@fullcalendar/list';
import ruLocale from '@fullcalendar/core/locales/ru';
import { useLocation, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../shared/auth';
import { all, Data, label } from '../../shared/api';
import { structure } from '../../shared/structure';
import { Heading, State, Panel, Empty } from '../../shared/ui';
import { EditPanel, FormSpec, useAction } from '../../shared/forms';
import type { ComponentType } from 'react';
import { visibleHours, type CalendarRange } from '../../shared/schedule-utils';
import { useStudentAssignments, assignmentStatus } from '../../shared/student-assignments';
import type { ScheduleEvent } from './event-format';
import { EventView } from './event-view';
import { EventCard } from './event-card';
export function SchedulePage({ student = false, AssignmentPanel }: {
    student?: boolean;
    AssignmentPanel: ComponentType<{assignment:Data|null;close:()=>void}>;
}) {
    const { courseId } = useParams(), a = useAuth(), location = useLocation(), role = student ? 'STUDENT' : location.pathname.startsWith('/admin') ? 'ADMIN' : 'TEACHER';
    const [course, C] = useState(courseId || ''), [item, I] = useState<Data | null>(null), [edit, E] = useState<FormSpec | null>(null), [assignment, A] = useState<Data | null>(null), [range, R] = useState<CalendarRange | null>(null), [detailed, D] = useState(false), [fullDay, F] = useState(false), [now, N] = useState(Date.now());
    useEffect(() => { C(courseId || ''); I(null); }, [courseId]);
    useEffect(() => { const t = window.setInterval(() => N(Date.now()), 30000); return () => window.clearInterval(t); }, []);
    const act = useAction(), statuses = useStudentAssignments(course || undefined, student);
    const byId = new Map((statuses.data || []).map(r => [String(r.assignmentId), r]));
    const courses = useQuery({ queryKey: ['calendar-courses', student, role, a.session?.userId], queryFn: async () => student ? (await all('/api/users/me/enrollments', { status: 'ACTIVE' })).map(r => ({ id: r.courseId, title: r.courseTitle, status: r.courseStatus })) : all('/api/courses', role === 'TEACHER' ? { teacherId: a.session?.userId } : {}), retry: false });
    const events = useQuery({ queryKey: ['calendar-events', course, courses.data?.map(r => r.id), role, a.session?.userId], queryFn: async () => {
            const out: ScheduleEvent[] = [];
            for (const c of courses.data || []) {
                if (course && String(c.id) !== course)
                    continue;
                for (const m of await structure(c.id)) {
                    for (const l of m.lessons) {
                        const context = { courseId: c.id, moduleId: m.id, lessonId: l.id, courseTitle: c.title, courseStatus: c.status, moduleTitle: m.title, lessonTitle: l.title };
                        for (const s of l.sessions)
                            out.push({ id: 's' + s.id, title: l.title, start: s.startsAt, end: s.endsAt, backgroundColor: s.format === 'ONLINE' ? '#E0E7FF' : '#DCFCE7', borderColor: s.format === 'ONLINE' ? '#4F46E5' : '#16A34A', textColor: s.format === 'ONLINE' ? '#3730A3' : '#166534', extendedProps: { ...s, ...context, kind: 'session' } });
                        // No end: FullCalendar uses a one-second rendering default; a deadline itself has no duration.
                        for (const x of l.assignments)
                            out.push({ id: 'a' + x.id, title: x.title, start: x.dueAt, backgroundColor: '#FEF3C7', borderColor: '#D97706', textColor: '#78350F', extendedProps: { ...x, ...context, kind: 'assignment' } });
                    }
                }
            }
            return out;
        }, enabled: !!courses.data, retry: false });
    const hours = visibleHours(events.data || [], range, fullDay), current = item && ((events.data || []).find(e => e.id === (item.kind === 'session' ? 's' : 'a') + item.id)?.extendedProps || item), status = current?.kind === 'assignment' ? assignmentStatus(byId.get(String(current.id))) : undefined;
    const close = () => I(null);
    return <><Heading title="Расписание" sub="Занятия и сроки сдачи домашних заданий"/>
 <Stack direction="row" gap={2} mb={2} flexWrap="wrap" alignItems="center"><TextField select size="small" label="Курс" value={course} onChange={e => { C(e.target.value); I(null); }} disabled={!!courseId} sx={{ minWidth: 230 }}><MenuItem value="">Все курсы</MenuItem>{courses.data?.map(c => <MenuItem value={String(c.id)} key={c.id}>{c.title}</MenuItem>)}</TextField><Button onClick={() => { void courses.refetch(); void events.refetch(); if (student)
        void statuses.refetch(); }}>Обновить</Button><TextField select size="small" label="Масштаб" value={detailed ? 'detail' : 'compact'} onChange={e => D(e.target.value === 'detail')} sx={{ minWidth: 145 }}><MenuItem value="compact">Компактный</MenuItem><MenuItem value="detail">Подробный</MenuItem></TextField><FormControlLabel control={<Switch checked={fullDay} onChange={(_, checked) => F(checked)} size="small"/>} label="Все 24 часа"/></Stack>
 {act.error && <Alert severity="error">{act.error}</Alert>}{student && statuses.isError && <Alert severity="warning" action={<Button onClick={() => void statuses.refetch()}>Повторить</Button>}>Статусы заданий недоступны, расписание сохранено.</Alert>}
 <State query={courses}><State query={events}>{events.data?.length === 0 && <Empty kind="schedule" text="В выбранных курсах пока нет занятий и сроков сдачи"/>}
 <Paper className={`section calendar calendar-grid ${detailed ? 'calendar-detailed' : 'calendar-compact'}`}>
 <FullCalendar plugins={[dayGridPlugin, timeGridPlugin, listPlugin]} locale={ruLocale} initialView={window.innerWidth < 700 ? 'listWeek' : 'timeGridWeek'} headerToolbar={{ left: 'prev,next today', center: 'title', right: 'timeGridWeek,dayGridMonth,listWeek' }} buttonText={{ today: 'Сегодня', week: 'Неделя', month: 'Месяц', list: 'Список' }} events={events.data || []} eventClick={e => I(e.event.extendedProps)} height="auto" stickyHeaderDates nowIndicator allDaySlot={false} firstDay={1} slotDuration="00:30:00" slotLabelInterval="01:00" slotEventOverlap={false} slotMinTime={hours.min} slotMaxTime={hours.max} scrollTime={hours.min} scrollTimeReset={false} defaultTimedEventDuration="00:00:01" forceEventDuration={false} eventMinHeight={30} eventShortHeight={36} slotLabelFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }} eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }} displayEventEnd datesSet={arg => R(prev => prev && +prev.start === +arg.start && +prev.end === +arg.end ? prev : { start: arg.start, end: arg.end })} eventClassNames={arg => { const deadline = arg.event.extendedProps.kind === 'assignment', start = arg.event.start, late = deadline && start && start.getHours() * 60 + start.getMinutes() > 1440 - (detailed ? 30 : 50); return [deadline ? 'calendar-deadline' : 'calendar-session', ...(late ? ['calendar-late-deadline'] : [])]; }} eventContent={arg => <EventView arg={arg}/>} eventDidMount={arg => { const p = arg.event.extendedProps; const detail = [p.kind === 'assignment' ? 'Срок сдачи задания' : label(p.format), arg.event.title, p.courseTitle, p.moduleTitle, p.kind === 'assignment' ? formatDate(p.dueAt) : `${formatDate(p.startsAt)} — ${formatDate(p.endsAt)}`].filter(Boolean).join('\n'); arg.el.title = detail; arg.el.setAttribute('aria-label', detail.replace(/\n/g, ', ')); }}/>
 </Paper></State></State>
 <Stack direction="row" flexWrap="wrap" gap={2} className="schedule-legend"><Typography color="primary">● Онлайн</Typography><Typography color="success.main">● Очно</Typography><Typography color="warning.main">● Дедлайн</Typography><Typography color="text.secondary" variant="body2">{hours.min.slice(0, 5)}–24:00 · местное время</Typography></Stack>
 <Panel open={!!current && !edit && !assignment} title={current?.kind === 'session' ? 'Информация о занятии' : 'Домашнее задание'} onClose={close}>
 {current && <><EventCard item={current} student={student} role={role} now={now} status={status} openAssignment={() => A({ ...current, ...byId.get(String(current.id)), assignmentId: current.id })} close={close}/>{!student && current.courseStatus === 'ACTIVE' && <Stack direction="row" gap={1} flexWrap="wrap" sx={{ pt: 2, borderTop: '1px solid #e2e8f0' }}>
 <Button variant="outlined" disabled={act.busy} startIcon={<CalendarDays size={16}/>} onClick={() => E({ title: current.kind === 'session' ? 'Перенос занятия' : 'Продление срока', schema: current.kind === 'session' ? 'RescheduleLessonSessionRequest' : 'ExtendAssignmentDeadlineRequest', method: 'PATCH', path: `/api/lessons/${current.lessonId}/${current.kind === 'session' ? 'sessions' : 'assignments'}/${current.id}/${current.kind === 'session' ? 'time' : 'deadline'}`, initial: current, after: close })}>{current.kind === 'session' ? 'Перенести' : 'Продлить срок'}</Button>
 {current.kind === 'session' && <><Button variant="outlined" disabled={act.busy} startIcon={<Pencil size={16}/>} onClick={() => E({ title: 'Редактирование занятия', schema: 'CreateLessonSessionRequest', method: 'PUT', path: `/api/lessons/${current.lessonId}/sessions/${current.id}`, initial: current, after: close })}>Редактировать</Button><Button color="error" disabled={act.busy} onClick={() => void act.run('DELETE', `/api/lessons/${current.lessonId}/sessions/${current.id}`, undefined, `Удалить занятие «${current.lessonTitle}» (${formatDate(current.startsAt)})?`).then(ok => { if (ok)
                close(); })}>Удалить занятие</Button></>}
 </Stack>}</>}
 </Panel><EditPanel spec={edit} onClose={() => E(null)}/><AssignmentPanel assignment={assignment ? { ...assignment, ...byId.get(String(assignment.assignmentId)) } : null} close={() => A(null)}/></>;
}
