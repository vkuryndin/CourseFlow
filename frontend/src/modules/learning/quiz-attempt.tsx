import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, FormControlLabel, LinearProgress, Paper, Radio, Stack, Typography } from '@mui/material';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../shared/auth';
import { Data, request, useGet, label, errorText } from '../../shared/api';
import { Heading, State } from '../../shared/ui';
export function QuizAttempt() { const { quizId: id = '', attemptId: attempt = '' } = useParams(), a = useAuth(), nav = useNavigate(), base = `/api/quizzes/${id}/attempts/${attempt}`, info = useGet(base), qs = useGet<Data[]>(`/api/quizzes/${id}/questions`, {}, info.data?.status === 'IN_PROGRESS'), key = `courseflow.quiz.${a.session?.userId}.${id}.${attempt}`, [answers, A] = useState<Record<string, number[]>>(() => { try {
    return JSON.parse(sessionStorage.getItem(key) || '{}');
}
catch {
    return {};
} }), [index, I] = useState(0), [now, N] = useState(Date.now()), [busy, B] = useState(false), [error, E] = useState(''); useEffect(() => { const t = setInterval(() => N(Date.now()), 1000); return () => clearInterval(t); }, []); useEffect(() => { sessionStorage.setItem(key, JSON.stringify(answers)); }, [answers, key]); const remaining = Math.max(0, Math.ceil((Date.parse(info.data?.expiresAt || '') - now) / 1000)), questions = qs.data || [], q = questions[index], answered = questions.filter(q => answers[q.id]?.length).length; useEffect(() => { if (info.data && info.data.status !== 'IN_PROGRESS') {
    sessionStorage.removeItem(key);
    nav(`/student/quizzes/${id}/attempts/${attempt}/result`, { replace: true });
} }, [info.data?.status]); async function finish() { if (busy || !remaining || !window.confirm(`Завершить тест? Без ответа: ${questions.length - answered}`))
    return; B(true); E(''); try {
    await request('POST', base + '/submit', { answers: questions.filter(x => answers[x.id]?.length).map(x => ({ questionId: x.id, optionIds: answers[x.id] })) });
    sessionStorage.removeItem(key);
    nav(`/student/quizzes/${id}/attempts/${attempt}/result`, { replace: true });
}
catch (e) {
    E(errorText(e));
    void info.refetch();
}
finally {
    B(false);
} } return <State query={info}><Heading title="Прохождение теста" sub={`Попытка ${info.data?.attemptNumber ?? '—'}`} action={<Typography variant="h4" color={remaining < 60 ? 'error' : 'primary'}>{Number.isFinite(remaining) ? `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')}` : '—'}</Typography>}/>{remaining === 0 ? <Alert severity="warning" action={<Button onClick={() => { sessionStorage.removeItem(key); void info.refetch(); nav(`/student/quizzes/${id}/attempts/${attempt}/result`); }}>Результат</Button>}>Время истекло. Отправка ответов недоступна.</Alert> : <State query={qs}>{q && <><Typography color="text.secondary">Отвечено: {answered} из {questions.length}</Typography><LinearProgress variant="determinate" value={100 * answered / questions.length}/><Paper className="section"><Typography color="text.secondary">Вопрос {index + 1} из {questions.length} · {label(q.type)}</Typography><Typography variant="h5" my={3}>{q.text}</Typography><Stack gap={1}>{q.options.map((o: Data) => <FormControlLabel key={o.id} label={o.text} control={q.type === 'SINGLE_CHOICE' ? <Radio checked={(answers[q.id] || []).includes(o.id)} onChange={() => A({ ...answers, [q.id]: [o.id] })}/> : <Checkbox checked={(answers[q.id] || []).includes(o.id)} onChange={(_, b) => A({ ...answers, [q.id]: b ? [...(answers[q.id] || []), o.id] : (answers[q.id] || []).filter(v => v !== o.id) })}/>}/>)}</Stack><Stack direction="row" justifyContent="space-between" mt={3}><Button disabled={!index} onClick={() => I(index - 1)}>Назад</Button><Button disabled={index === questions.length - 1} variant="contained" onClick={() => I(index + 1)}>Далее</Button></Stack></Paper><Stack direction="row" gap={1} flexWrap="wrap">{questions.map((x, i) => <Button key={x.id} variant={i === index ? 'contained' : 'outlined'} color={i !== index && answers[x.id]?.length ? 'success' : 'primary'} onClick={() => I(i)}>{i + 1}</Button>)}</Stack>{error && <Alert severity="error">{error}</Alert>}<Button sx={{ mt: 3 }} variant="contained" disabled={busy} onClick={() => void finish()}>Завершить тестирование</Button></>}</State>}</State>; }
