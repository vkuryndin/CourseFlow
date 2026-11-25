import { lazy, Suspense, useEffect, useState } from 'react';
import { Alert, Box, Button, Checkbox, FormControlLabel, IconButton, InputAdornment, MenuItem, Slider, Stack, TextField, Typography } from '@mui/material';
import { Eye, EyeOff } from 'lucide-react';
import { AccountRoles } from './account-fields';
import { useForm, Controller } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Data, errorText, label, request, useRefresh, safeUrl } from '../api';
import { names } from '../labels';
import { BusyButton, Panel } from '../ui';
import { parseLocal } from '../datetime';
import { DateField } from '../date-field';
import { NamedSearch } from '../server-search';
import { schema } from './schema';
import { initialValues } from './schema';
import { buildPayload } from './schema';
const RichEditor = lazy(() => import('../rich-editor').then(m => ({ default: m.RichEditor })));
export type FormSpec = {
    schema: string;
    path: string;
    method: string;
    title: string;
    initial?: Data;
    hidden?: string[];
    after?: (r: Data) => void;
    rich?: boolean;
    maxScore?: number;
    confirm?: string;
    submitLabel?: string;
    disabled?: boolean;
    requiredRole?: string;
};
export function EntityForm({ spec, onDone, onCancel }: {
    spec: FormSpec;
    onDone?: () => void;
    onCancel?: () => void;
}) { const s = schema(spec.schema); const [err, E] = useState(''), [busy, B] = useState(false), [showPassword, setShowPassword] = useState(false); const accountForm = spec.schema === 'AdminCreateUserRequest'; const refresh = useRefresh(); const form = useForm<Data>({ defaultValues: initialValues(spec.schema, spec.initial || {}), resolver: zodResolver(z.object({}).passthrough()) }); const sessionForm = spec.schema === 'CreateLessonSessionRequest'; const sessionFormat = form.watch('format'); useEffect(() => { if (sessionForm && sessionFormat === 'OFFLINE')
    form.setValue('meetingUrl', '', { shouldValidate: false }); }, [sessionForm, sessionFormat, form.setValue]); const submit = form.handleSubmit(async (values) => { if (busy || spec.disabled)
    return; E(''); form.clearErrors(); if (sessionForm || spec.schema === 'RescheduleLessonSessionRequest') {
    const start = parseLocal(String(values.startsAt || ''), true), end = parseLocal(String(values.endsAt || ''), true);
    let invalid = false;
    const mark = (key: string, message: string) => { invalid = true; form.setError(key, { type: 'manual', message }); };
    if (!start)
        mark('startsAt', 'Введите корректные дату и время');
    else if (+start <= Date.now())
        mark('startsAt', 'Начало занятия должно быть в будущем');
    if (!end)
        mark('endsAt', 'Введите корректные дату и время');
    else if (start && +end <= +start)
        mark('endsAt', 'Окончание должно быть позже начала');
    if (sessionForm && values.format === 'ONLINE') {
        const url = String(values.meetingUrl || '').trim();
        if (!url)
            mark('meetingUrl', 'Для онлайн-занятия укажите URL встречи');
        else if (!/^https?:\/\//i.test(url) || !safeUrl(url))
            mark('meetingUrl', 'Введите корректную ссылку http:// или https://');
    }
    if (invalid)
        return;
} B(true); try {
    const body = buildPayload(spec.schema, values);
    if (accountForm && new TextEncoder().encode(body.password).length > 72) throw new Error("Пароль не должен превышать 72 байта UTF-8");
    if (spec.requiredRole && !body.roles?.includes(spec.requiredRole)) throw new Error(`Обязательная роль: ${label(spec.requiredRole)}`);
    if (spec.maxScore != null && body.score > spec.maxScore)
        throw new Error('Оценка выше максимального балла');
    if (spec.confirm && !window.confirm(spec.confirm))
        return;
    const r = await request(spec.method, spec.path, body);
    await refresh();
    spec.after?.(r);
    onDone?.();
}
catch (e) {
    E(errorText(e));
}
finally {
    B(false);
} }); function cancel() { if (!form.formState.isDirty || window.confirm('Закрыть без сохранения изменений?'))
    onCancel?.(); } useEffect(() => { const warn = (e: BeforeUnloadEvent) => { if (form.formState.isDirty) {
    e.preventDefault();
    e.returnValue = '';
} }; window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn); }, [form.formState.isDirty]); return <Suspense fallback={<Box role="status" sx={{py:3}}><Typography color="text.secondary">Загрузка редактора…</Typography></Box>}><Box component="form" className={accountForm ? "entity-form account-create-form" : "entity-form"} onSubmit={submit} noValidate><Stack gap={2.5}>{err && <Alert severity="error">{err}</Alert>}{accountForm && <Box><Typography color="text.secondary" variant="body2">{spec.requiredRole === "TEACHER" ? "Создание учётной записи преподавателя CourseFlow" : "Создание новой учётной записи CourseFlow"}</Typography><Typography variant="subtitle1" fontWeight={600} mt={2}>Основная информация</Typography></Box>}{Object.entries(s.properties as Data).sort(([a], [b]) => accountForm ? ['displayName','email','password','passwordChangeRequired','roles'].indexOf(a) - ['displayName','email','password','passwordChangeRequired','roles'].indexOf(b) : 0).filter(([k]) => k !== 'version' && !spec.hidden?.includes(k) && !(sessionForm && k === 'meetingUrl' && sessionFormat === 'OFFLINE')).map(([k, p]) => <Controller key={k} name={k} control={form.control} render={({ field, fieldState }) => { const title = names[k] || k;
    if (accountForm && k === 'roles') return <AccountRoles value={field.value || []} onChange={field.onChange} requiredRole={spec.requiredRole}/>;
    if (accountForm && k === 'password') return <Stack gap={1}><Typography variant="subtitle1" fontWeight={600}>Безопасность</Typography><TextField {...field} value={field.value ?? ''} fullWidth size="small" label="Пароль" required type={showPassword ? 'text' : 'password'} autoComplete="new-password" helperText="От 8 до 72 символов, не более 72 байт UTF-8." error={!!fieldState.error} slotProps={{htmlInput:{maxLength:p.maxLength},input:{endAdornment:<InputAdornment position="end"><IconButton aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'} onClick={() => setShowPassword(v => !v)} onMouseDown={e => e.preventDefault()} edge="end">{showPassword ? <EyeOff size={19}/> : <Eye size={19}/>}</IconButton></InputAdornment>}}}/></Stack>;
    if (accountForm && k === 'passwordChangeRequired') return <Box><FormControlLabel label={title} control={<Checkbox checked={!!field.value} onChange={(_, checked) => field.onChange(checked)}/>}/><Typography variant="caption" color="text.secondary" display="block">При первом входе пользователь должен будет установить новый пароль, если эта настройка включена.</Typography></Box>;
    if (p.format === 'date-time' || p.format === 'date')
    return <DateField value={field.value ?? ''} onChange={field.onChange} onBlur={field.onBlur} title={title} required={!!s.required?.includes(k)} withTime={p.format === 'date-time'} error={!!fieldState.error} helperText={fieldState.error?.message}/>; if (k === 'roles')
    return <Box><Typography>{title}</Typography>{['STUDENT', 'TEACHER', 'ADMIN'].map(r => <FormControlLabel key={r} label={label(r)} control={<Checkbox checked={(field.value || []).includes(r)} onChange={(_, b) => field.onChange(b ? [...field.value, r] : field.value.filter((x: string) => x !== r))}/>}/>)}</Box>; if (k === 'options')
    return <Stack gap={1}><Typography>{title}</Typography>{(field.value || []).map((o: Data, i: number) => <Stack direction="row" alignItems="center" key={i}><TextField fullWidth label={`Вариант ${i + 1}`} value={o.text} onChange={e => field.onChange(field.value.map((x: Data, j: number) => j === i ? { ...x, text: e.target.value } : x))}/><Checkbox checked={o.correct} title="Правильный ответ" onChange={(_, b) => field.onChange(field.value.map((x: Data, j: number) => j === i ? { ...x, correct: b } : x))}/><Button disabled={field.value.length <= 2} onClick={() => field.onChange(field.value.filter((_: Data, j: number) => i !== j))}>−</Button></Stack>)}<Button disabled={field.value.length >= 10} onClick={() => field.onChange([...field.value, { text: '', correct: false }])}>Добавить вариант</Button></Stack>; if (k === 'content' && spec.rich)
    return <RichEditor value={field.value} onChange={field.onChange}/>; if (p.type === 'boolean')
    return <FormControlLabel label={title} control={<Checkbox checked={!!field.value} onChange={(_, b) => field.onChange(b)}/>}/>; if (k === 'categoryId' || k === 'teacherId')
    return <NamedSearch path={k === 'categoryId' ? '/api/categories' : '/api/teachers'} field={k === 'categoryId' ? 'name' : 'displayName'} id={field.value} onChange={field.onChange} label={title} fullWidth required={!!s.required?.includes(k)} error={!!fieldState.error} helperText={fieldState.error?.message} placeholder="Выберите"/>; if (k === 'score' && spec.maxScore != null)
    return <Stack gap={1}><TextField {...field} value={field.value ?? ''} type="number" label={`Балл (из ${spec.maxScore})`} required size="small" slotProps={{ htmlInput: { min: 0, max: spec.maxScore, step: 1 } }}/><Slider aria-label="Выбрать балл" min={0} max={Math.max(1, spec.maxScore)} step={1} value={Math.max(0, Math.min(spec.maxScore, Number(field.value) || 0))} onChange={(_, v) => field.onChange(v)} disabled={spec.maxScore === 0} valueLabelDisplay="auto"/><Typography variant="caption" color="text.secondary">Введите балл или выберите его на шкале.</Typography></Stack>; const list = p.enum?.map((v: string) => ({ value: v, label: label(v) })); const type = k.toLowerCase().includes('password') ? 'password' : k === 'email' ? 'email' : p.type === 'integer' || p.type === 'number' ? 'number' : 'text'; return <TextField {...field} value={field.value ?? ''} fullWidth size="small" label={accountForm && k === "displayName" ? "Имя и фамилия" : title} required={s.required?.includes(k) || (sessionForm && k === 'meetingUrl' && sessionFormat === 'ONLINE')} error={!!fieldState.error} helperText={fieldState.error?.message || (sessionForm && k === 'meetingUrl' ? 'Ссылка на онлайн-встречу (https://...)' : undefined)} select={!!list} type={list ? 'text' : type} multiline={!list && ['description', 'bio', 'content', 'feedback', 'comment', 'text'].includes(k)} minRows={k === 'description' ? 5 : 3} slotProps={{ inputLabel: { shrink: true }, htmlInput: { maxLength: p.maxLength, min: p.minimum, max: k === 'score' && spec.maxScore != null ? spec.maxScore : p.maximum } }}>{list && [<MenuItem key="empty" value="">Выберите</MenuItem>, ...list.map((o: Data) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)]}</TextField>; }}/>)}<Stack className="entity-form-actions" direction="row" gap={2}><BusyButton busy={busy} disabled={spec.disabled} type="submit">{spec.submitLabel || 'Сохранить'}</BusyButton>{onCancel && <Button disabled={busy} onClick={cancel}>Отмена</Button>}</Stack></Stack></Box></Suspense>; }
export function EditPanel({ spec, onClose }: {
    spec: FormSpec | null;
    onClose: () => void;
}) { return <Panel open={!!spec} title={spec?.title || ''} onClose={() => { if (window.confirm('Закрыть панель? Несохранённые изменения будут потеряны.'))
    onClose(); }} wide>{spec && <EntityForm key={spec.method + spec.path} spec={spec} onCancel={onClose} onDone={onClose}/>}</Panel>; }
