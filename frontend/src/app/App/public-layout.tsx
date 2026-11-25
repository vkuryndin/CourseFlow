import { Button, Stack } from '@mui/material';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { GraduationCap } from 'lucide-react';
import { useAuth } from '../auth';
export function PublicLayout() { const a = useAuth(), loc = useLocation(); return <><header className="public-header"><Link className="brand" to="/catalog"><GraduationCap size={30}/> CourseFlow</Link><Stack direction="row" gap={1}>{!['/', '/catalog'].includes(loc.pathname.replace(/\/$/, '') || '/') && <Button component={Link} to="/catalog">Каталог</Button>}{a.session ? <Button variant="contained" component={Link} to={'/' + (a.role || 'STUDENT').toLowerCase()}>Личный кабинет</Button> : <><Button component={Link} to="/login">Войти</Button><Button variant="contained" component={Link} to="/register">Регистрация</Button></>}</Stack></header><main className="public-main"><Outlet /></main><footer className="footer">CourseFlow · Learning Management Platform</footer></>; }
