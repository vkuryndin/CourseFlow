import { catalogRoot } from '../../shared/course-navigation';
import { Button, Paper, Stack, Typography } from '@mui/material';
import { BookOpen } from 'lucide-react';
import { teacher as teacherBanner } from '../../shared/visual';
import { courseCount, initials } from '../../shared/visual-utils';
import { Link, useParams } from 'react-router-dom';
import { Page, useGet } from '../../shared/api';
import { State } from '../../shared/ui';
import { CatalogPage, type TeacherSearchComponent } from './catalog-page';
export function TeacherProfile({ studentWorkspace = false, TeacherSearch }: {
    studentWorkspace?: boolean;
    TeacherSearch: TeacherSearchComponent;
}) { const { teacherId = '' } = useParams(), t = useGet(`/api/teachers/${teacherId}`), courses = useGet<Page>('/api/courses', { teacherId, status: 'ACTIVE', page: 0, size: 1 }, !!teacherId); const name = String(t.data?.displayName || 'Преподаватель'); return <State query={t}>{studentWorkspace && <Button component={Link} to={catalogRoot(true)}>← К каталогу</Button>}<Paper className="teacher-profile"><img className="teacher-banner" src={teacherBanner} alt=""/><div className="teacher-info"><div className="teacher-avatar" aria-hidden="true">{initials(name)}</div><div><Typography variant="h4" component="h1">{name}</Typography><Typography color="text.secondary">Преподаватель CourseFlow</Typography>{courses.data && <Stack direction="row" alignItems="center" gap={1} mt={1}><BookOpen size={17}/><Typography variant="body2">{courseCount(courses.data.totalElements)} в активном каталоге</Typography></Stack>}</div></div></Paper><CatalogPage key={teacherId} teacherId={teacherId} studentWorkspace={studentWorkspace} TeacherSearch={TeacherSearch}/></State>; }
