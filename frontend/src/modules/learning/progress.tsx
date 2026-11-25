import { LoadMore, uniqueRows, nextPage } from '../../shared/infinite';
import { useState } from 'react';
import { Button, LinearProgress, Paper, Typography } from '@mui/material';
import { Link, useParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Data, Page, http } from '../../shared/api';
import { structure } from '../../shared/structure';
import { Heading, State, Rows, Stats, Empty } from '../../shared/ui';
import { AssignmentPanel } from './assignment-panel';
export function Progress() {
    const { courseId } = useParams(), q = useInfiniteQuery({ queryKey: ['student-progress', courseId], initialPageParam: 0, queryFn: async ({ pageParam, signal }) => (await http.get<Page>('/api/users/me/progress', { params: { courseId, includeDetails: !!courseId, page: courseId ? 0 : pageParam, size: 20, detailPage: courseId ? pageParam : 0, detailSize: 20 }, signal })).data, getNextPageParam: last => { if (!courseId)
            return nextPage(last); const c = last.content[0]; return c && (c.detailPage + 1) * c.detailSize < Math.max(c.totalAssignments ?? 0, c.totalQuizzes ?? 0) ? c.detailPage + 1 : undefined; }, retry: false });
    const pages = q.data?.pages ?? [], allCourses = uniqueRows(pages.flatMap(p => p.content), c => c.courseId), courses = courseId ? allCourses.map(c => ({ ...c, assignments: uniqueRows<Data>(pages.flatMap(p => p.content.find(x => x.courseId === c.courseId)?.assignments ?? []), x => x.assignmentId), quizzes: uniqueRows<Data>(pages.flatMap(p => p.content.find(x => x.courseId === c.courseId)?.quizzes ?? []), x => x.quizId) })) : allCourses;
    const state = { ...q, isError: q.isError && !q.data };
    const load = () => { if (q.isFetching)
        return; if (q.isError && !q.isFetchNextPageError)
        void q.refetch();
    else if (q.hasNextPage || q.isFetchNextPageError)
        void q.fetchNextPage({ cancelRefetch: false }); };
    const tree = useQuery({ queryKey: ['structure', courseId], queryFn: () => structure(courseId!), enabled: !!courseId, retry: false });
    const [drawer, D] = useState<Data | null>(null);
    return <><Heading title="Моя успеваемость" sub="Прогресс обучения и полученные оценки"/><State query={state}>{courses.map((c: Data) => <Paper className="section" key={c.courseId}><Typography variant="h5">{c.courseTitle}</Typography><Stats items={[["Прогресс", `${c.progressPercent}%`], ['Успеваемость', c.performancePercent == null ? 'Нет оценок' : `${c.performancePercent}%`], ['Выполнено', `${c.completedAssessments} / ${c.totalAssessments}`]]}/><LinearProgress variant="determinate" value={c.progressPercent || 0}/>{!courseId ? <Button component={Link} to={`/student/progress/${c.courseId}`}>Подробные результаты →</Button> : <><Typography variant="h6" mt={3}>Задания</Typography><Rows rows={(c.assignments || []).map((x: Data) => ({ ...x, moduleTitle: tree.data?.find(m => m.lessons.some(l => l.assignments.some(a => a.id === x.assignmentId)))?.title || '—' }))} cols={[{ key: 'moduleTitle', title: 'Модуль' }, 'title', 'maxScore', 'score', 'status', 'dueAt']} actions={r => <Button onClick={() => { const x = tree.data?.flatMap(m => m.lessons).flatMap(l => l.assignments).find(a => a.id === r.assignmentId); D({ ...r, ...x, courseId: c.courseId, courseTitle: c.courseTitle, assignmentId: r.assignmentId }); }}>Подробнее</Button>}/><Typography variant="h6" mt={3}>Тесты</Typography><Rows rows={(c.quizzes || []).map((x: Data) => ({ ...x, moduleTitle: tree.data?.find(m => m.quiz?.id === x.quizId)?.title || '—' }))} cols={[{ key: 'moduleTitle', title: 'Модуль' }, 'title', 'bestScore', 'attemptsUsed', 'maxAttempts', 'status']} actions={r => <Button component={Link} to={`/student/quizzes/${r.quizId}`}>Попытки</Button>}/><Typography variant="body2" color="text.secondary">Задания: {c.assignments?.length ?? 0} из {c.totalAssignments} · Тесты: {c.quizzes?.length ?? 0} из {c.totalQuizzes}</Typography></>}</Paper>)}{!courses.length && <Empty kind="results" text="Нет результатов обучения"/>}<LoadMore hasMore={!!q.hasNextPage} busy={q.isFetching} failed={q.isError} error={q.error} load={load} loaded={courseId ? undefined : courses.length} total={courseId ? undefined : pages[0]?.totalElements} label="курсов"/></State><AssignmentPanel assignment={drawer} close={() => D(null)}/></>;
}
