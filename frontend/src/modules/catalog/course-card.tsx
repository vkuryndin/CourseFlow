import { courseDetailsPath } from '../../shared/course-navigation';
import { useEffect, useRef, useState } from 'react';
import { Button, Card, CardContent, Rating, Stack, Typography, Tooltip } from '@mui/material';
import { coverFor } from '../../shared/visual';
import { reviewCount, dayCount } from '../../shared/visual-utils';
import { Link } from 'react-router-dom';
import { Data, useGet } from '../../shared/api';
function CourseDescription({ value }: {
    value: string;
}) { const ref = useRef<HTMLParagraphElement>(null), [clipped, C] = useState(false); useEffect(() => { const el = ref.current; if (!el)
    return; const check = () => C(el.scrollHeight > el.clientHeight + 1); check(); const ro = new ResizeObserver(check); ro.observe(el); return () => ro.disconnect(); }, [value]); return <Tooltip title={clipped ? value : ''} enterDelay={300} placement="top" slotProps={{ tooltip: { sx: { maxWidth: 440, maxHeight: 400, overflow: 'auto', whiteSpace: 'pre-wrap', fontSize: '0.875rem' } } }}><Typography component="p" ref={ref} tabIndex={clipped ? 0 : undefined} className="clamp" mt={1}>{value}</Typography></Tooltip>; }
export function CourseCard({ course, studentWorkspace = false }: {
    course: Data;
    studentWorkspace?: boolean;
}) { const category = useGet(`/api/categories/${course.categoryId}`, {}, course.categoryId != null); const r = useGet(`/api/courses/${course.id}/reviews/rating`), t = useGet(`/api/teachers/${course.teacherId}`); return <Card className="course-card"><div className="cover course-cover"><img src={coverFor(category.data?.name)} alt="" loading="lazy"/></div><CardContent><Typography variant="h6" component={Link} to={courseDetailsPath(course.id, studentWorkspace)}>{course.title}</Typography><Typography variant="body2" color="text.secondary">{t.data?.displayName || 'Преподаватель'} · {dayCount(course.duration)}</Typography><CourseDescription value={course.description || ''}/><Stack direction="row" alignItems="center" gap={1} mt={2}><Rating size="small" readOnly precision={0.1} value={r.data?.averageRating || 0}/><Typography variant="caption">{reviewCount(r.data?.reviewCount || 0)}</Typography></Stack><Button component={Link} to={courseDetailsPath(course.id, studentWorkspace)} sx={{ mt: 1 }}>Подробнее →</Button></CardContent></Card>; }
