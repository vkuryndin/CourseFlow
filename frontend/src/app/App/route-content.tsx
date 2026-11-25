import { Component, Suspense, type ReactNode } from 'react';
import { Alert, Box, Button, CircularProgress, Typography } from '@mui/material';
import { useLocation } from 'react-router-dom';

type BoundaryProps = { children: ReactNode; location: string };
type BoundaryState = { failed: boolean; location: string };

class RouteErrorBoundary extends Component<BoundaryProps, BoundaryState> {
    state: BoundaryState = { failed: false, location: this.props.location };

    static getDerivedStateFromError(): Partial<BoundaryState> {
        return { failed: true };
    }

    static getDerivedStateFromProps(props: BoundaryProps, state: BoundaryState): Partial<BoundaryState> | null {
        return props.location !== state.location ? { failed: false, location: props.location } : null;
    }

    render() {
        if (this.state.failed) {
            return <Alert severity="error" action={<Button color="inherit" onClick={() => window.location.reload()}>Обновить страницу</Button>}>
                Не удалось открыть страницу. Попробуйте обновить её или перейти в другой раздел.
            </Alert>;
        }
        return this.props.children;
    }
}

// The route guard and workspace/public navigation stay outside this boundary.
// Do not key by pathname: successful route transitions keep normal React identity.
export function RouteContent({ children }: { children: ReactNode }) {
    const { pathname, search } = useLocation();
    return <RouteErrorBoundary location={pathname + search}>
        <Suspense fallback={<Box role="status" sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 2, minHeight: 160 }}>
            <CircularProgress size={24}/><Typography color="text.secondary">Загрузка страницы…</Typography>
        </Box>}>
            {children}
        </Suspense>
    </RouteErrorBoundary>;
}
