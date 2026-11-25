import { Chip, Paper, Stack, Typography } from '@mui/material';
import { Data, useGet, label } from '../../shared/api';
import { durationText } from '../../shared/datetime';
import { State, Rows, Details, Panel } from '../../shared/ui';
import { useAssessmentContext } from './assessment-context';
export const attemptStatus = (v: any) => v === 'SUBMITTED' ? 'Завершён' : label(v);
export const duration = (r: Data) => durationText(r.takenAt, r.finishedAt);
export const scoreColor = (v: any) => v == null ? 'text.secondary' : v >= 85 ? 'success.main' : v >= 70 ? 'warning.main' : 'error.main';
export function AttemptPanel({ selection, close }: {
    selection: Data | null;
    close: () => void;
}) { const q = useGet(selection ? `/api/quizzes/${selection.quizId}/attempts/${selection.id}/manage` : '', {}, !!selection), context = useAssessmentContext(selection?.courseId, null, selection?.quizId, !!selection && !selection?.moduleTitle), a: Data = { ...context.data, ...selection, ...q.data?.attempt }; return <Panel open={!!selection} title="Результат попытки" onClose={close} wide><State query={q}>{q.data && <Stack gap={2}><Paper variant="outlined" className="review-context"><Typography variant="h5">{selection?.studentName || 'Имя недоступно'}</Typography><Typography fontWeight={600}>{selection?.quizTitle || context.data?.title || 'Тест'}</Typography><Details data={a} keys={['courseTitle', 'moduleTitle', 'attemptNumber', 'takenAt', 'expiresAt', 'finishedAt']}/><Typography color="text.secondary">Продолжительность: {duration(a)}</Typography></Paper><Stack direction="row" alignItems="center" gap={2}><Typography variant="h5" color={scoreColor(a.score)}>{a.score == null ? '—' : `${a.score}/100`}</Typography><Chip label={attemptStatus(a.status)} size="small"/></Stack><Typography variant="h6">Выбранные ответы</Typography><Rows rows={q.data.selectedAnswers || []} cols={[{ key: 'questionText', title: 'Вопрос' }, { key: 'optionText', title: 'Выбранный ответ' }]}/></Stack>}</State></Panel>; }
