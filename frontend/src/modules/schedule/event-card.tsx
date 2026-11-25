import { Video, Users, ClipboardList, CalendarDays, BookOpen, FolderOpen, FileText, ArrowRight } from 'lucide-react';
import { durationText, formatDate, formatTime } from '../../shared/datetime';
import { Box, Button, Stack, Typography, Paper, Chip } from '@mui/material';
import { Link } from 'react-router-dom';
import { Data, safeUrl, label } from '../../shared/api';
import { studentLessonPath, editorLessonPath } from '../../shared/course-navigation';
import { sessionState } from '../../shared/schedule-utils';
import { assignmentLabels, assignmentColor } from '../../shared/student-assignments';
import { sessionLabels } from './event-format';
import { fullDate } from './event-format';
import { eventTime } from './event-format';
export function EventCard({ item, student, role, now, status, openAssignment, close }: {
    item: Data;
    student: boolean;
    role: string;
    now: number;
    status?: string;
    openAssignment: () => void;
    close: () => void;
}) {
    const deadline = item.kind === 'assignment', Icon = deadline ? ClipboardList : item.format === 'ONLINE' ? Video : Users;
    const state = sessionState(item.startsAt, item.endsAt, now), url = safeUrl(item.meetingUrl), endDate = deadline ? item.dueAt : item.endsAt, startDate = deadline ? item.dueAt : item.startsAt;
    const sameDay = formatDate(startDate, false) === formatDate(endDate, false);
    const coursePath = student ? `/student/courses/${item.courseId}` : `/${role === 'ADMIN' ? 'admin' : 'teacher'}/courses/${item.courseId}/editor`;
    const lessonPath = student ? studentLessonPath(item.courseId, item.moduleId, item.lessonId) : editorLessonPath(role, item.courseId, item.moduleId, item.lessonId);
    const assignmentPath = lessonPath + `&assignmentId=${encodeURIComponent(item.id)}`;
    return <div className="schedule-detail-card">
  <Paper elevation={0} className={`schedule-detail-hero ${deadline ? 'deadline' : item.format === 'ONLINE' ? 'online' : 'offline'}`}>
   <Stack direction="row" gap={2} alignItems="center"><div className="schedule-detail-icon"><Icon size={26}/></div><Box minWidth={0}><Typography variant="overline">{deadline ? 'Домашнее задание' : `${label(item.format)} · Занятие`}</Typography><Typography variant="h6" sx={{ overflowWrap: 'anywhere' }}>{deadline ? item.title : item.lessonTitle}</Typography></Box></Stack>
   <Stack direction="row" gap={1} mt={2} flexWrap="wrap">{deadline ? (student ? <Chip size="small" color={assignmentColor(status)} label={status ? assignmentLabels[status] : 'Статус работы недоступен'}/> : <Chip size="small" color={Date.parse(item.dueAt) <= now ? 'warning' : 'default'} label={Date.parse(item.dueAt) <= now ? 'Срок истёк' : 'Срок ещё не истёк'}/>) : <Chip size="small" color={state === 'ongoing' ? 'success' : state === 'upcoming' ? 'primary' : 'default'} label={sessionLabels[state]}/>}</Stack>
  </Paper>
  <Paper variant="outlined" className="schedule-date-card"><CalendarDays size={25}/><Box minWidth={0}><Typography variant="caption" color="text.secondary">{deadline ? 'Срок сдачи' : 'Дата занятия'}</Typography><Typography fontWeight={600}>{fullDate(startDate)}</Typography><Typography variant="h5">{deadline ? formatTime(startDate) : sameDay ? eventTime(item) : `${formatDate(startDate)} — ${formatDate(endDate)}`}</Typography><Typography variant="body2" color="text.secondary">{deadline ? `Максимальный балл: ${item.maxScore ?? '—'}` : `Продолжительность: ${durationText(item.startsAt, item.endsAt)}`}</Typography></Box></Paper>
  {deadline && <><Typography variant="h6">Что нужно сделать</Typography><Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{item.description || 'Описание отсутствует'}</Typography></>}
  <Typography variant="h6">Учебная программа</Typography>
  <div className="schedule-context"><BookOpen size={19}/><div><Typography variant="caption" color="text.secondary">Курс</Typography><Typography component={Link} to={coursePath} onClick={close} color="primary" sx={{ display: 'block', fontWeight: 600 }}>{item.courseTitle}</Typography></div></div>
  <div className="schedule-context"><FolderOpen size={19}/><div><Typography variant="caption" color="text.secondary">Модуль</Typography><Typography>{item.moduleTitle}</Typography></div></div>
  <div className="schedule-context"><FileText size={19}/><div><Typography variant="caption" color="text.secondary">Урок</Typography><Typography>{item.lessonTitle}</Typography></div></div>
  <Stack gap={1} mt={1} alignItems="stretch">
   {deadline ? (student ? <Button variant="contained" onClick={openAssignment} endIcon={<ArrowRight size={17}/>}>Открыть задание</Button> : <Button variant="contained" component={Link} to={assignmentPath} onClick={close}>Открыть задание</Button>) : item.format === 'ONLINE' && url && <Button href={url} target="_blank" rel="noopener noreferrer" variant="contained" startIcon={<Video size={17}/>}>Подключиться к занятию</Button>}
   <Button variant={deadline ? 'outlined' : 'contained'} component={Link} to={lessonPath} onClick={close} startIcon={<BookOpen size={17}/>}>{student ? 'Перейти к материалам урока' : 'Открыть урок в конструкторе'}</Button>
   <Button component={Link} to={coursePath} onClick={close}>Перейти к курсу</Button>
  </Stack>
 </div>;
}
