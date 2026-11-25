import { Alert, Button, Stack } from '@mui/material';
import { useParams } from 'react-router-dom';
import { useAuth } from '../../shared/auth';
import { Heading, Paged, State, options, Empty } from '../../shared/ui';
import { useAction } from '../../shared/forms';
import { useSelectedCourse } from './course-selection';
import { CourseSelect } from './course-selection';
export function Students() { const { courseId } = useParams(), [course, C, choices] = useSelectedCourse(courseId), a = useAuth(), act = useAction(); return <><Heading title="Студенты курса"/><Stack direction="row" gap={2} mb={3}><CourseSelect value={course} onChange={C} disabled={!!courseId}/></Stack>{act.error && <Alert severity="error">{act.error}</Alert>}{course ? <Paged path={`/api/courses/${course}/enrollments`} cols={['studentName', 'status', 'enrolledAt', 'endedAt']} filters={[{ key: 'status', title: 'Статус', options: options(['ACTIVE', 'WITHDRAWN']) }]} actions={r => a.role === 'ADMIN' && r.status === 'ACTIVE' ? <Button color="error" disabled={act.busy} onClick={() => void act.run('DELETE', `/api/courses/${course}/enrollments/students/${r.studentId}`, undefined, `Прекратить запись студента «${r.studentName}»?`)}>Отчислить</Button> : null}/> : <State query={choices}><Empty kind="courses" text="Нет доступных курсов"/></State>}</>; }
