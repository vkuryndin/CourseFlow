import { Button } from '@mui/material';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Data, optional, all } from '../../shared/api';
import { Heading, State, Paged, Rows, options } from '../../shared/ui';
import { useMyCourseOptions } from './course-options';
export function Quizzes() { const courseOptions = useMyCourseOptions(); const courses = useQuery({ queryKey: ['my-quizzes'], queryFn: async () => { const es = await all('/api/users/me/enrollments', { status: 'ACTIVE' }); const out: Data[] = []; for (const e of es) {
        const mods = await all(`/api/courses/${e.courseId}/modules`);
        for (const m of mods) {
            const q = await optional(`/api/modules/${m.id}/quiz`);
            if (q)
                out.push({ ...q, courseTitle: e.courseTitle, moduleTitle: m.title });
        }
    } return out; }, retry: false }); return <><Heading title="Тестирование"/><State query={courses}><Rows emptyKind="results" emptyText="Нет доступных тестов" rows={courses.data || []} cols={['courseTitle', { key: 'moduleTitle', title: 'Модуль' }, 'title', 'timeLimit', 'maxAttempts']} actions={r => <Button component={Link} to={`/student/quizzes/${r.id}`}>Открыть</Button>}/></State><Heading title="История попыток"/><Paged emptyKind="results" emptyText="Нет попыток тестирования" path="/api/users/me/quiz-attempts" cols={['courseTitle', 'quizTitle', 'attemptNumber', 'status', 'score', 'takenAt']} filters={[{ key: 'courseId', title: 'Курс', options: courseOptions }, { key: 'status', title: 'Статус', options: options(['IN_PROGRESS', 'SUBMITTED', 'EXPIRED']) }]} actions={r => <Button component={Link} to={`/student/quizzes/${r.quizId}/attempts/${r.attemptId}${r.status === 'IN_PROGRESS' ? '' : '/result'}`}>{r.status === 'IN_PROGRESS' ? 'Продолжить' : 'Результат'}</Button>}/></>; }
