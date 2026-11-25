import type { CatalogWorkspace } from '../../shared/course-navigation';
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, MenuItem, Stack, TextField, Typography, LinearProgress } from '@mui/material';
import { hero } from '../../shared/visual';
import { useInfiniteQuery } from '@tanstack/react-query';
import { Page, get, errorText } from '../../shared/api';
import { Empty } from '../../shared/ui';
import type { ComponentType } from 'react';
import { ServerSearch, type SearchItem } from '../../shared/server-search';
import { CourseCard } from './course-card';
type PublicTeacher = SearchItem & {displayName:string};
export type TeacherSearchComponent = ComponentType<{value:PublicTeacher|null;onChange:(value:PublicTeacher|null)=>void}>;
type Category = SearchItem & {
    name: string;
};
export function CatalogPage({ teacherId, workspace = false, TeacherSearch }: {
    teacherId?: string;
    workspace?: CatalogWorkspace;
    TeacherSearch: TeacherSearchComponent;
}) {
    const [q, Q] = useState(''), [search, Search] = useState(''), [category, C] = useState<Category | null>(null), [teacher, T] = useState<PublicTeacher | null>(null), [sort, S] = useState(''), [teacherReset, bumpTeacherReset] = useState(0);
    const reset = () => { Q(''); Search(''); C(null); T(null); S(''); bumpTeacherReset(n => n + 1); };
    useEffect(() => { const t = setTimeout(() => Search(q.trim()), 350); return () => clearTimeout(t); }, [q]);
    const params = { status: 'ACTIVE', q: search || undefined, categoryId: category?.id || undefined, teacherId: teacherId || teacher?.id || undefined, size: 12, ...(sort ? { sortBy: sort.split(':')[0], direction: sort.split(':')[1] } : {}) };
    const data = useInfiniteQuery({ queryKey: ['catalog', params], initialPageParam: 0, queryFn: ({ pageParam }) => get<Page>('/api/courses', { ...params, page: pageParam }), getNextPageParam: last => last.last || last.page + 1 >= last.totalPages ? undefined : last.page + 1, retry: false });
    const sentinel = useRef<HTMLDivElement>(null);
    const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = data;
    useEffect(() => { const el = sentinel.current; if (!el || !hasNextPage || isFetchingNextPage || isFetchNextPageError || typeof IntersectionObserver === 'undefined')
        return; const observer = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting))
        void fetchNextPage({ cancelRefetch: false }); }, { rootMargin: '240px' }); observer.observe(el); return () => observer.disconnect(); }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage, search, category, teacher, teacherId, sort]);
    const courses = Array.from(new Map((data.data?.pages.flatMap(p => p.content) || []).map(c => [c.id, c])).values());
    const total = data.data?.pages[0]?.totalElements || 0;
    return <>{!teacherId && !workspace ? <div className="hero catalog-hero"><div className="hero-copy"><Typography variant="overline" color="primary">Образовательная платформа</Typography><Typography variant="h3">Начните учиться сегодня</Typography><Typography>Найдите курс, который поможет развить новые навыки.</Typography><TextField className="hero-search" label="Поиск курса" value={q} onChange={e => Q(e.target.value)}/></div><img className="hero-art" src={hero} alt=""/></div> : <><Typography variant="h5" component={teacherId ? "h2" : "h1"} mb={2}>{teacherId ? "Курсы преподавателя" : "Каталог курсов"}</Typography><TextField size="small" label={teacherId ? "Поиск по курсам" : "Поиск курса"} value={q} onChange={e => Q(e.target.value)} sx={{ width: { xs: "100%", sm: 360 } }}/></>}
  <Stack direction="row" gap={2} flexWrap="wrap" my={3}><ServerSearch<Category> key={`category-${teacherReset}`} path="/api/categories" label="Категория" value={category} onChange={C} name={c => c.name} placeholder="Все категории" loadingText="Загрузка категорий…" emptyText="Категории не найдены" errorText="Не удалось загрузить категории"/>{!teacherId && <TeacherSearch key={teacherReset} value={teacher} onChange={T}/>}<TextField select size="small" label="Сортировка" value={sort} onChange={e => S(e.target.value)} sx={{ minWidth: 160 }}><MenuItem value="">По умолчанию</MenuItem><MenuItem value="title:ASC">Название: А–Я</MenuItem><MenuItem value="title:DESC">Название: Я–А</MenuItem><MenuItem value="startDate:ASC">Дата начала: раньше</MenuItem><MenuItem value="startDate:DESC">Дата начала: позже</MenuItem><MenuItem value="duration:ASC">Длительность: по возрастанию</MenuItem><MenuItem value="duration:DESC">Длительность: по убыванию</MenuItem></TextField><Button onClick={reset}>Сбросить фильтры</Button></Stack>
  {data.isPending ? <LinearProgress /> : data.isError && !data.data ? <Alert severity="error" action={<Button onClick={() => void data.refetch()}>Повторить</Button>}>{errorText(data.error)}</Alert> : <>{courses.length ? <><Typography color="text.secondary" variant="body2">Курсы: показано {courses.length} из {total}</Typography><div className="cards catalog-cards">{courses.map(c => <CourseCard key={c.id} course={c} workspace={workspace}/>)}</div></> : <Empty kind="courses" text="Курсы не найдены" action={<Button onClick={reset}>Сбросить фильтры</Button>}/>}<div className="catalog-load" ref={sentinel}>{isFetchingNextPage ? <><LinearProgress /><Typography variant="body2" color="text.secondary">Загрузка курсов…</Typography></> : hasNextPage ? <>{isFetchNextPageError && <Alert severity="error">Не удалось загрузить следующую страницу.</Alert>}<Button variant="outlined" onClick={() => void fetchNextPage({ cancelRefetch: false })}>{isFetchNextPageError ? 'Повторить загрузку' : 'Показать ещё'}</Button></> : courses.length > 0 ? <Typography variant="body2" color="text.secondary">Все курсы загружены</Typography> : null}</div></>}
  </>;
}
