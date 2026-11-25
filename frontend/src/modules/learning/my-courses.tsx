import { courseDetailsPath } from '../../shared/course-navigation';
import { Alert, Button } from '@mui/material';
import { Link } from 'react-router-dom';
import { useGet, label } from '../../shared/api';
import { Heading, Paged, options } from '../../shared/ui';
import { useAction } from '../../shared/forms';
function CourseTeacher({ id }: {
    id: number;
}) { const c = useGet(`/api/courses/${id}`); const teacherId = c.data?.teacherId; const t = useGet(`/api/teachers/${teacherId}`, {}, !!teacherId); return teacherId != null && t.data?.displayName ? <Link to={`/student/catalog/teachers/${teacherId}`}>{t.data.displayName}</Link> : <span>{c.isLoading || t.isLoading ? 'Загрузка…' : 'Недоступен'}</span>; }
export function MyCourses() { const act = useAction(); return <><Heading title="Мои курсы"/>{act.error && <Alert severity="error">{act.error}</Alert>}<Paged emptyKind="courses" emptyText="Вы пока не записаны на курсы" path="/api/users/me/enrollments" cols={['courseTitle', { key: 'teacherName', render: r => <CourseTeacher id={r.courseId}/> }, 'courseStatus', { key: 'status', title: 'Статус записи', render: r => label(r.status) }, 'enrolledAt', 'endedAt']} filters={[{ key: 'status', title: 'Статус записи', options: options(['ACTIVE', 'WITHDRAWN']) }]} actions={r => <><Button component={Link} to={r.status === 'ACTIVE' ? `/student/courses/${r.courseId}` : courseDetailsPath(r.courseId, true)}>{r.status === 'ACTIVE' ? 'Продолжить' : 'Карточка'}</Button>{r.status === 'ACTIVE' && <Button color="error" disabled={act.busy} onClick={() => void act.run('DELETE', `/api/courses/${r.courseId}/enrollments/me`, undefined, `Прекратить обучение на курсе «${r.courseTitle}»?`)}>Прекратить</Button>}</>}/></>; }
