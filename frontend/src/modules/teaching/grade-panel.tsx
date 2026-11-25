import { Chip, Paper, Stack, Typography } from '@mui/material';
import { Data, label } from '../../shared/api';
import { Details, Panel } from '../../shared/ui';
import { EntityForm } from '../../shared/forms';
import { useAssessmentContext } from './assessment-context';
export function GradePanel({ row, assignment, close }: {
    row: Data | null;
    assignment: Data | null;
    close: () => void;
}) {
    const context = useAssessmentContext(assignment?.courseId, assignment?.id, null, !!row && !assignment?.description), a: Data = { ...context.data, ...assignment };
    return <Panel open={!!row} title="Проверка работы" onClose={close} wide>{row && assignment && <Stack gap={2}><Paper variant="outlined" className="review-context"><Typography variant="h5">{row.studentName || 'Имя недоступно'}</Typography><Chip size="small" label={row.status === 'SUBMITTED' ? 'На проверке' : label(row.status)} color={row.status === 'SUBMITTED' ? 'warning' : row.status === 'GRADED' ? 'success' : 'default'}/><Details data={{ ...a, submittedAt: row.submittedAt }} keys={['courseTitle', 'moduleTitle', 'lessonTitle', 'submittedAt', 'dueAt']}/></Paper><Typography variant="h6">{a.title}</Typography><Paper variant="outlined" className="review-instruction"><Typography fontWeight={700}>Условие задания</Typography><Typography sx={{ whiteSpace: 'pre-wrap' }}>{a.description || (context.isFetching ? 'Загрузка…' : 'Описание недоступно')}</Typography><Typography mt={1} fontWeight={600}>Максимальный балл: {a.maxScore ?? '—'}</Typography></Paper><Typography variant="h6">Ответ студента</Typography><Paper variant="outlined" className="review-context"><Typography sx={{ whiteSpace: 'pre-wrap' }}>{row.content}</Typography></Paper><Typography variant="h6">Оценивание</Typography><EntityForm key={`${row.id}:${row.version}`} spec={{ title: 'Оценка', schema: 'GradeSubmissionRequest', method: 'PUT', path: `/api/assignments/${assignment.id}/submissions/${row.id}/grade`, initial: row, maxScore: a.maxScore, submitLabel: 'Сохранить оценку' }} onDone={close} onCancel={close}/></Stack>}</Panel>;
}
