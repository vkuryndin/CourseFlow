import { useState } from 'react';
import { Button } from '@mui/material';
import { Data } from '../../shared/api';
import { Heading, Paged, options } from '../../shared/ui';
import { useMyCourseOptions } from './course-options';
import { AssignmentPanel } from './assignment-panel';
export function Assignments() { const courseOptions = useMyCourseOptions(); const [assignment, A] = useState<Data | null>(null); return <><Heading title="Домашние задания"/><Paged emptyKind="assignments" emptyText="Нет заданий по выбранным условиям" path="/api/users/me/assignments" cols={['courseTitle', 'lessonTitle', 'title', 'dueAt', 'maxScore', 'status', 'score']} filters={[{ key: 'courseId', title: 'Курс', options: courseOptions }, { key: 'status', title: 'Статус', options: options(['NOT_SUBMITTED', 'OVERDUE', 'SUBMITTED', 'GRADED']) }]} actions={r => <Button onClick={() => A(r)}>Открыть</Button>}/><AssignmentPanel assignment={assignment} close={() => A(null)}/></>; }
