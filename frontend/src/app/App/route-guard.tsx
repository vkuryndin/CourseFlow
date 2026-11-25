import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';
export function Guard({ role, change = false }: {
    role?: string;
    change?: boolean;
}) { const a = useAuth(), loc = useLocation(); if (!a.session)
    return <Navigate to={'/login?next=' + encodeURIComponent(loc.pathname + loc.search)} replace/>; if (a.session.passwordChangeRequired && !change)
    return <Navigate to="/change-password" replace/>; if (role && !a.session.roles.includes(role))
    return <Navigate to="/403" replace/>; return <Outlet />; }
