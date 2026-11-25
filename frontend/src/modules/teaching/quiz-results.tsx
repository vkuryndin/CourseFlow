import { useEffect, useState } from 'react';
import { Button, Chip, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Data, useGet } from '../../shared/api';
import { structure } from '../../shared/structure';
import { Heading, Paged, State, Empty } from '../../shared/ui';
import { useSelectedCourse } from './course-selection';
import { useStudentNames } from './student-names';
import { CourseSelect } from './course-selection';
import { studentName } from './student-names';
import { attemptStatus } from './attempt-panel';
import { scoreColor } from './attempt-panel';
import { duration } from './attempt-panel';
import { AttemptPanel } from './attempt-panel';
export function QuizResults() { const { courseId } = useParams(), [course, C, choices] = useSelectedCourse(courseId), [quiz, Q] = useState(''), [selected, S] = useState<Data | null>(null); const tree = useQuery({ queryKey: ['structure', course], queryFn: () => structure(course), enabled: !!course, retry: false }), c = useGet(`/api/courses/${course}`, {}, !!course), students = useStudentNames(course); const quizzes = tree.data?.flatMap(m => m.quiz ? [{ ...m.quiz, id: m.quiz.id, title: m.quiz.title, moduleTitle: m.title }] : []) || [], active = quizzes.find(q => String(q.id) === quiz); useEffect(() => { if (tree.isSuccess && !active)
    Q(String(quizzes[0]?.id || '')); }, [course, tree.data, quiz]); return <><Heading title="Результаты тестирования"/><Stack direction="row" gap={2} mb={3} flexWrap="wrap"><CourseSelect value={course} onChange={s => { C(s); Q(''); S(null); }} disabled={!!courseId}/><TextField select size="small" label="Тест" value={active ? quiz : ''} onChange={e => { Q(e.target.value); S(null); }} sx={{ minWidth: 240, maxWidth: '100%' }}><MenuItem value="">Выберите тест</MenuItem>{quizzes.map(q => <MenuItem value={String(q.id)} key={q.id}>{q.moduleTitle} / {q.title}</MenuItem>)}</TextField></Stack>{course ? <State query={tree}>{active ? <><State query={students.q}><span /></State><Paged path={`/api/quizzes/${quiz}/attempts`} cols={[{ key: 'studentName', render: r => studentName(r, students.names) }, 'attemptNumber', { key: 'status', render: r => <Chip size="small" label={attemptStatus(r.status)} color={r.status === 'SUBMITTED' ? 'success' : 'default'}/> }, { key: 'score', render: r => <Typography color={scoreColor(r.score)}>{r.score == null ? '—' : `${r.score}/100`}</Typography> }, 'takenAt', 'finishedAt', { key: 'durationText', render: duration }]} filters={[{ key: 'studentId', title: 'Студент', options: students.options, searchable: true }, { key: 'status', title: 'Статус', options: ['IN_PROGRESS', 'SUBMITTED', 'EXPIRED'].map(value => ({ value, label: attemptStatus(value) })) }]} actions={r => <Button onClick={() => S({ ...r, quizId: quiz, quizTitle: active.title, moduleTitle: active.moduleTitle, courseId: course, courseTitle: c.data?.title, studentName: studentName(r, students.names) })}>Подробнее</Button>}/></> : <Empty kind="results" text="Нет тестов"/>}</State> : <State query={choices}><Empty kind="courses" text="Нет доступных курсов"/></State>}<AttemptPanel selection={selected} close={() => S(null)}/></>; }
