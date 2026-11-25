import { gradebookLegend } from '../../shared/gradebook-presentation';
import {GradebookTable} from './gradebook-table';
import { mergeGradebook, type GradebookPage } from '../../shared/gradebook-pages';
import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { useParams } from 'react-router-dom';
import { useQueries, useQuery } from '@tanstack/react-query';
import { all, Data, useAll, useGet, label, http, errorText } from '../../shared/api';
import { Heading, State, Rows, Panel, Empty } from '../../shared/ui';
import { useSelectedCourse } from './course-selection';
import { CourseSelect } from './course-selection';
import { GradePanel } from './grade-panel';
import { AttemptPanel } from './attempt-panel';
export function Gradebook() {
    const { courseId } = useParams(), [course, C, choices] = useSelectedCourse(courseId), [rowPages, RP] = useState(1), [columnPages, CP] = useState(1), [filter, F] = useState<Data>({}), [deb, Deb] = useState<Data>({}), [cell, Cell] = useState<Data | null>(null), [grade, G] = useState<Data | null>(null), [attempt, A] = useState<Data | null>(null);
    const scroll = useRef<HTMLDivElement>(null), intent = useRef({ rows: false, cols: false }), [expanded, Expand] = useState(false), [hovered, Hover] = useState(''), size = 20, colSize = 20;
    const courseInfo = useGet(`/api/courses/${course}`, {}, !!course);
    const modules = useAll(`/api/courses/${course}/modules`, {}, !!course);
    const previousCourse = useRef(course);
    useEffect(() => { if (previousCourse.current === course)
        return; previousCourse.current = course; intent.current = { rows: false, cols: false }; RP(1); CP(1); F({}); Deb({}); Cell(null); G(null); A(null); if (scroll.current) {
        scroll.current.scrollTop = 0;
        scroll.current.scrollLeft = 0;
    } }, [course]);
    useEffect(() => { if (JSON.stringify(filter) === JSON.stringify(deb))
        return; const t = setTimeout(() => { intent.current = { rows: false, cols: false }; Deb(filter); RP(1); CP(1); if (scroll.current) {
        scroll.current.scrollTop = 0;
        scroll.current.scrollLeft = 0;
    } }, 350); return () => clearTimeout(t); }, [filter, deb]);
    const waiting = JSON.stringify(filter) !== JSON.stringify(deb), blocks = Array.from({ length: rowPages * columnPages }, (_, i) => ({ page: Math.floor(i / columnPages), columnPage: i % columnPages }));
    const qs = useQueries({ queries: blocks.map(b => ({ queryKey: ['gradebook', course, deb, b.page, b.columnPage], queryFn: async ({ signal }) => (await http.get<GradebookPage>(`/api/courses/${course}/gradebook`, { params: { ...deb, ...b, size, columnSize: colSize }, signal })).data, enabled: !!course && !waiting, retry: false })) });
    const first = qs[0], ready = qs.every(x => !!x.data && !x.isFetching && !x.isError), busy = qs.some(x => x.isFetching), failed = qs.filter(x => x.isError), totalRows = first.data?.students.totalElements ?? 0, totalCols = first.data?.totalColumns ?? 0;
    const moreRows = rowPages < (first.data?.students.totalPages ?? 0), moreCols = columnPages < (first.data?.totalColumnPages ?? 0), canLoad = ready && !waiting;
    const { columns, students } = mergeGradebook(qs.flatMap(x => x.data ? [x.data] : []));
    const nextRows = () => { if (canLoad && moreRows)
        RP(Math.min(rowPages + 1, first.data?.students.totalPages ?? 1)); };
    const nextCols = () => { if (canLoad && moreCols)
        CP(Math.min(columnPages + 1, first.data?.totalColumnPages ?? 1)); };
    const onScroll = (el: HTMLDivElement) => { if (waiting)
        return; if (el.scrollHeight - el.scrollTop - el.clientHeight < 100 && moreRows) {
        if (canLoad)
            nextRows();
        else
            intent.current.rows = true;
    } if (el.scrollWidth - el.scrollLeft - el.clientWidth < 100 && moreCols) {
        if (canLoad)
            nextCols();
        else
            intent.current.cols = true;
    } };
    useEffect(() => { if (!canLoad)
        return; const p = intent.current; intent.current = { rows: false, cols: false }; if (p.rows && moreRows)
        nextRows(); if (p.cols && moreCols)
        nextCols(); }, [canLoad, rowPages, columnPages, moreRows, moreCols]);
    const q = { isLoading: waiting || first.isPending, isError: !first.data && first.isError, error: first.error, refetch: () => Promise.all(qs.map(x => x.refetch())) };
    const details = useQuery({ queryKey: ['grade-cell', cell?.type, cell?.id, cell?.studentId], queryFn: async () => cell?.type === 'ASSIGNMENT' ? (await all(`/api/assignments/${cell.id}/submissions`)).filter(r => r.studentId === cell.studentId) : all(`/api/quizzes/${cell?.id}/attempts`, { studentId: cell?.studentId }), enabled: !!cell, retry: false });
    const change = (k: string, v: string) => { F(old => { const next = { ...old }; if (v)
        next[k] = v;
    else
        delete next[k]; if (k === 'type' && v === 'QUIZ')
        delete next.reviewStatus; return next; }); };
    return <div className={expanded ? 'gradebook-page gradebook-expanded' : 'gradebook-page'}><Heading title="Электронный журнал" action={<Button onClick={() => Expand(!expanded)}>{expanded ? 'Выйти из полноэкранного режима' : 'На весь экран'}</Button>}/><Stack direction="row" gap={2} flexWrap="wrap" mb={2}><CourseSelect value={course} onChange={s => { C(s); intent.current = { rows: false, cols: false }; RP(1); CP(1); F({}); Deb({}); Cell(null); }} disabled={!!courseId}/><TextField size="small" label="Поиск студента" value={filter.q || ''} onChange={e => change('q', e.target.value)}/><TextField size="small" select label="Модуль" value={filter.moduleId || ''} onChange={e => change('moduleId', e.target.value)} sx={{ minWidth: 160 }}><MenuItem value="">Все модули</MenuItem>{modules.data?.map(m => <MenuItem key={m.id} value={String(m.id)}>{m.title}</MenuItem>)}</TextField>{[['type', 'Тип', ['ASSIGNMENT', 'QUIZ']], ['enrollmentStatus', 'Запись', ['ACTIVE', 'WITHDRAWN']], ['reviewStatus', 'Статус задания', ['NOT_SUBMITTED', 'OVERDUE', 'SUBMITTED', 'GRADED']]].map(([k, t, vs]) => <TextField key={String(k)} size="small" select label={String(t)} value={filter[String(k)] || ''} onChange={e => change(String(k), e.target.value)} disabled={k === 'reviewStatus' && filter.type === 'QUIZ'} sx={{ minWidth: k === 'reviewStatus' ? 200 : 150 }}><MenuItem value="">Все</MenuItem>{(vs as string[]).map(v => <MenuItem value={v} key={v}>{k === "reviewStatus" && v === "SUBMITTED" ? "Ожидает проверки" : label(v)}</MenuItem>)}</TextField>)}<Button onClick={() => F({})}>Сбросить</Button></Stack>{course ? <State query={q}><GradebookTable scroll={scroll} onScroll={onScroll} filter={filter} F={F} columns={columns} students={students} hovered={hovered} Hover={Hover} busy={busy} hasFailures={failed.length>0} course={course} courseTitle={courseInfo.data?.title} Cell={Cell}/><Paper className="gradebook-load-bar"><Typography variant="body2" color="text.secondary">Студенты: {students.length} из {totalRows} · Работы: {columns.length} из {totalCols}</Typography><Stack direction="row" gap={1} flexWrap="wrap">{moreRows && <Button disabled={!canLoad} onClick={nextRows}>Загрузить студентов ↓</Button>}{moreCols && <Button disabled={!canLoad} onClick={nextCols}>Загрузить работы →</Button>}{busy && <Typography variant="body2" color="text.secondary">Загрузка…</Typography>}</Stack></Paper><Stack direction="row" gap={2} flexWrap="wrap" className="gradebook-legend">{gradebookLegend.map(([tone, title]) => <Stack key={title} direction="row" gap={1} alignItems="center"><Box className={`gradebook-legend-swatch tone-${tone}`}/><Typography variant="body2">{title}</Typography></Stack>)}</Stack>{failed.length > 0 && <Alert severity="error" action={<Button onClick={() => void Promise.all(failed.map(x => x.refetch()))}>Повторить</Button>}>{errorText(failed[0].error)}</Alert>}</State> : <State query={choices}><Empty kind="courses" text="Нет доступных курсов"/></State>}<Panel open={!!cell && !grade && !attempt} title={cell?.title || 'Результат'} onClose={() => Cell(null)} wide>{cell && <><Typography>{cell.studentName}</Typography><State query={details}><Rows rows={details.data || []} cols={cell.type === 'ASSIGNMENT' ? ['submittedAt', 'status', 'score'] : ['attemptNumber', 'status', 'score', 'finishedAt']} actions={r => <Button onClick={() => cell.type === 'ASSIGNMENT' ? G({ ...r, studentName: cell.studentName }) : A({ ...r, quizId: cell.id, quizTitle: cell.title, courseId: course, courseTitle: cell.courseTitle, moduleTitle: cell.moduleTitle, studentName: cell.studentName })}>Открыть</Button>}/></State></>}</Panel><GradePanel row={grade} assignment={cell} close={() => G(null)}/><AttemptPanel selection={attempt} close={() => A(null)}/></div>;
}
