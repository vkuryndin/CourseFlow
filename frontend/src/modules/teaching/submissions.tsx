import { useEffect, useState } from 'react';
import { Button, MenuItem, Stack, TextField } from '@mui/material';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../shared/auth';
import { Data, useGet } from '../../shared/api';
import { structure } from '../../shared/structure';
import { Heading, Paged, State, Empty } from '../../shared/ui';
import { useSelectedCourse } from './course-selection';
import { useStudentNames } from './student-names';
import { CourseSelect } from './course-selection';
import { studentName } from './student-names';
import { GradePanel } from './grade-panel';
export function Submissions() {
    const { courseId } = useParams(), [course, C, choices] = useSelectedCourse(courseId), [assignmentId, A] = useState(''), [row, R] = useState<Data | null>(null);
    const tree = useQuery({ queryKey: ['structure', course], queryFn: () => structure(course), enabled: !!course, retry: false }), c = useGet(`/api/courses/${course}`, {}, !!course), auth = useAuth(), dashboard = useGet('/api/dashboard/teacher', {}, auth.role === 'TEACHER'), students = useStudentNames(course);
    const assignments = tree.data?.flatMap(m => m.lessons.flatMap(l => l.assignments.map(a => ({ ...a, id: a.id, title: a.title, lessonTitle: l.title, moduleTitle: m.title, courseTitle: c.data?.title, courseId: course })))) || [], selected = assignments.find(a => String(a.id) === assignmentId);
    useEffect(() => { if (tree.isSuccess && !selected && (auth.role !== 'TEACHER' || dashboard.isSuccess || dashboard.isError)) {
        const pending = dashboard.data?.pendingSubmissions?.find((r: Data) => String(r.courseId) === course && assignments.some(x => String(x.id) === String(r.assignmentId)));
        A(String(pending?.assignmentId || assignments[0]?.id || ''));
    } }, [course, tree.data, assignmentId, dashboard.isSuccess, dashboard.isError, dashboard.data]);
    return <><Heading title="Проверка домашних заданий"/><Stack direction="row" gap={2} mb={3} flexWrap="wrap"><CourseSelect value={course} onChange={s => { C(s); A(''); R(null); }} disabled={!!courseId}/><TextField select size="small" label="Задание" value={selected ? assignmentId : ''} onChange={e => { A(e.target.value); R(null); }} sx={{ minWidth: 240, maxWidth: '100%' }}><MenuItem value="">Выберите задание</MenuItem>{assignments.map(a => <MenuItem key={a.id} value={String(a.id)}>{a.moduleTitle} / {a.title}</MenuItem>)}</TextField></Stack>{course ? <State query={tree}>{selected ? <Paged path={`/api/assignments/${assignmentId}/submissions`} cols={[{ key: 'studentName', render: r => studentName(r, students.names) }, 'submittedAt', 'status', 'score', 'feedback']} filters={[{ key: 'status', title: 'Статус', options: [{ value: 'SUBMITTED', label: 'На проверке' }, { value: 'GRADED', label: 'Проверено' }] }]} actions={r => <Button onClick={() => R({ ...r, studentName: studentName(r, students.names) })}>Проверить</Button>}/> : <Empty kind="assignments" text="Нет заданий"/>}</State> : <State query={choices}><Empty kind="courses" text="Нет доступных курсов"/></State>}<GradePanel row={row} assignment={selected || null} close={() => R(null)}/></>;
}
