import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Alert, Box, Stack, TextField, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { errorText, request, type Session } from '../../shared/api';
import { buildPayload } from '../../shared/forms';
import { PasswordField } from '../../shared/password-field';
import { BusyButton } from '../../shared/ui';
import { clearRecoveryEmail, recoveryEmail } from './recovery-email';

type Props = { token: string; onLogin: (session: Session) => void };

export function ResetPasswordForm({ token: linkToken, onLogin }: Props) {
    const [email, setEmail] = useState(recoveryEmail);
    const [token, setToken] = useState(linkToken);
    const [password, setPassword] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [resetDone, setResetDone] = useState(false);
    const active = useRef(true);
    const submitting = useRef(false);
    useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (submitting.current || resetDone) return;
        setError('');
        submitting.current = true;
        setBusy(true);
        try {
            // The user confirms the email in the visible field. It is not part of the reset API.
            const credentials = buildPayload('LoginRequest', { email: email.trim(), password });
            const body = buildPayload('ResetPasswordRequest', { token, newPassword: password });
            await request('POST', '/api/auth/reset-password', body);
            clearRecoveryEmail();
            if (!active.current) return;
            setResetDone(true);
            setPassword('');
            setToken('');
            try {
                const session = await request<Session>('POST', '/api/auth/login', credentials);
                if (!active.current) return;
                if (!session?.accessToken || !Array.isArray(session.roles) || !session.roles.length) {
                    throw new Error('Не удалось получить сессию');
                }
                onLogin(session);
            } catch (e) {
                if (active.current) setError(errorText(e));
            }
        } catch (e) {
            if (active.current) setError(errorText(e));
        } finally {
            submitting.current = false;
            if (active.current) setBusy(false);
        }
    }

    if (resetDone) return <Stack gap={2}>
        {busy ? <Typography role="status">Пароль изменён. Выполняется вход…</Typography> : <>
            <Alert severity="success">Пароль изменён.</Alert>
            {error && <Alert severity="warning">Автоматический вход не выполнен: {error}. Войдите с новым паролем. Повторное восстановление не требуется.</Alert>}
            <Link to="/login" replace>Войти с новым паролем</Link>
        </>}
    </Stack>;

    return <Box component="form" className="entity-form" onSubmit={submit}>
        <Stack gap={2.5}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField fullWidth size="small" label="Email" name="email" type="email" autoComplete="username" required value={email} onChange={e => setEmail(e.target.value)} disabled={busy} helperText="Укажите email учётной записи, для которой вы получили ссылку." slotProps={{ inputLabel: { shrink: true }, htmlInput: { maxLength: 254 } }}/>
            {!linkToken && <TextField fullWidth size="small" label="Токен" name="token" required value={token} onChange={e => setToken(e.target.value)} disabled={busy} slotProps={{ inputLabel: { shrink: true }, htmlInput: { maxLength: 128 } }}/>}
            <PasswordField fullWidth size="small" label="Новый пароль" name="newPassword" required autoComplete="new-password" value={password} onChange={e => setPassword(e.target.value)} disabled={busy} slotProps={{ inputLabel: { shrink: true }, htmlInput: { maxLength: 72 } }}/>
            <Typography variant="body2" color="text.secondary">После изменения пароля CourseFlow выполнит вход с указанным email.</Typography>
            <BusyButton type="submit" variant="contained" busy={busy}>Сменить пароль</BusyButton>
        </Stack>
    </Box>;
}
