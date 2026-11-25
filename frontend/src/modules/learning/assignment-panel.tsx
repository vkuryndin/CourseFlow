import { Alert, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { Data, optional } from '../../shared/api';
import { State, Panel, Details } from '../../shared/ui';
import { EntityForm } from '../../shared/forms';
export function AssignmentPanel({ assignment, close }: {
    assignment: Data | null;
    close: () => void;
}) { const id = assignment?.assignmentId; const own = useQuery({ queryKey: ['own-submission', id], queryFn: () => optional(`/api/assignments/${id}/submissions/me`), enabled: !!id, retry: false }); return <Panel open={!!assignment} title={assignment?.title || 'Задание'} onClose={close} wide>{assignment && <><Typography sx={{ whiteSpace: 'pre-wrap' }}>{assignment.description}</Typography><Details data={assignment} keys={['courseTitle', 'lessonTitle', 'dueAt', 'maxScore', 'status']}/><State query={own}>{own.data ? <><Typography variant="h6">Ваш ответ</Typography><Typography sx={{ whiteSpace: 'pre-wrap' }}>{own.data.content}</Typography><Details data={own.data} keys={['status', 'score', 'feedback', 'submittedAt']}/></> : Date.parse(assignment.dueAt) <= Date.now() || assignment.status === 'OVERDUE' ? <Alert severity="warning">Срок сдачи истёк</Alert> : <EntityForm spec={{ title: 'Сдать работу', schema: 'CreateSubmissionRequest', method: 'POST', path: `/api/assignments/${id}/submissions`, confirm: 'Отправить работу на проверку?' }}/>}</State></>}</Panel>; }
