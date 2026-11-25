import { useState } from 'react';
import { Alert, Button, Paper } from '@mui/material';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { request, useGet, errorText, useRefresh } from '../../shared/api';
import { Heading, State, Paged, Details } from '../../shared/ui';
export function QuizInfo() { const { quizId: id = '' } = useParams(), q = useGet(`/api/quizzes/${id}`), nav = useNavigate(), [error, E] = useState(''), [busy, B] = useState(false), refresh = useRefresh(); async function start() { if (busy)
    return; B(true); try {
    const a = await request('POST', `/api/quizzes/${id}/attempts`);
    await refresh();
    nav(`/student/quizzes/${id}/attempts/${a.id}`);
}
catch (e) {
    E(errorText(e));
}
finally {
    B(false);
} } return <><State query={q}><Heading title={q.data?.title || 'Тест'}/><Paper className="section"><Details data={q.data || {}} keys={['timeLimit', 'maxAttempts']}/>{error && <Alert severity="error">{error}</Alert>}<Button variant="contained" disabled={busy} onClick={() => void start()}>Начать / продолжить попытку</Button></Paper></State><Heading title="Мои попытки"/><Paged emptyKind="results" emptyText="Нет попыток тестирования" path={`/api/quizzes/${id}/attempts/me`} cols={['attemptNumber', 'status', 'score', 'takenAt', 'finishedAt']} actions={r => <Button component={Link} to={`/student/quizzes/${id}/attempts/${r.id}${r.status === 'IN_PROGRESS' ? '' : '/result'}`}>Открыть</Button>}/></>; }
