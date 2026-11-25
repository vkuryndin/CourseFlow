import { useState } from 'react';
import { Alert, Paper, Stack, Typography } from '@mui/material';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { GraduationCap } from 'lucide-react';
import { hero } from '../../shared/visual';
import { useAuth } from '../../shared/auth';
import { Data, Session } from '../../shared/api';
import { EntityForm } from '../../shared/forms';
import { Heading } from '../../shared/ui';
export function AuthPage({ kind }: {
    kind: string;
}) {
    const location = useLocation();
    return <AuthPageContent key={`${kind}:${location.key}`} kind={kind}/>;
}
function AuthPageContent({ kind }: {
    kind: string;
}) { const a = useAuth(), nav = useNavigate(), [search] = useSearchParams(), [success, S] = useState(false); const map: Record<string, [
    string,
    string,
    string
]> = { login: ['Вход в систему', 'LoginRequest', 'login'], register: ['Регистрация', 'RegistrationRequest', 'register'], forgot: ['Восстановление пароля', 'ForgotPasswordRequest', 'forgot-password'], reset: ['Новый пароль', 'ResetPasswordRequest', 'reset-password'], change: ['Обязательная смена пароля', 'ChangePasswordRequest', 'change-password'] }; const [title, schema, path] = map[kind]; const token = search.get('token') || ''; const after = (r: Data) => { if (kind === 'login') {
    const s = r as Session;
    a.login(s);
    const next = search.get('next');
    nav(s.passwordChangeRequired ? '/change-password' : next && /^\/(?!\/)/.test(next) && !next.includes('\\') ? next : `/${s.roles.includes('ADMIN') ? 'admin' : s.roles.includes('TEACHER') ? 'teacher' : 'student'}`, { replace: true });
}
else if (kind === 'change') {
    void a.logout().finally(() => nav('/login', { replace: true }));
}
else
    S(true); }; return <div className="auth-wrap"><div className="auth-art"><div className="auth-brand"><GraduationCap size={46} aria-hidden="true"/><Typography variant="h3">CourseFlow</Typography></div><Typography variant="h4">Ваш путь к новым знаниям</Typography><Typography>Учитесь, преподавайте и развивайтесь в едином образовательном пространстве.</Typography><img className="auth-illustration" src={hero} alt=""/></div><Paper className="auth-form"><Heading title={title}/>{success ? <Alert severity="success">{kind === 'forgot' ? 'Если учётная запись существует, запрос на восстановление принят.' : 'Операция выполнена.'} <Link to="/login">Войти</Link></Alert> : <EntityForm spec={{ schema, method: 'POST', path: '/api/auth/' + path, title, initial: { token }, submitLabel: kind === 'login' ? 'Войти' : kind === 'register' ? 'Зарегистрироваться' : kind === 'forgot' ? 'Отправить запрос' : 'Сменить пароль', hidden: kind === 'reset' && token ? ['token'] : [], after }}/>}<Stack mt={3} gap={1}>{kind === 'login' && <><Link to="/forgot-password">Забыли пароль?</Link><Link to="/register">Зарегистрироваться</Link></>}{kind !== 'login' && <Link to="/login">Войти</Link>}<Link to="/catalog">← Каталог курсов</Link></Stack></Paper></div>; }
