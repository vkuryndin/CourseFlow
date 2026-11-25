import React from 'react';
import { Alert, Box, Button, Chip, CircularProgress, LinearProgress, Paper, Stack, Typography } from '@mui/material';
import { emptyImages, type EmptyKind } from '../visual';
import { errorText, label } from '../api';
export function Heading({ title, sub, action }: {
    title: string;
    sub?: string;
    action?: React.ReactNode;
}) { return <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} gap={2} mb={3}><Box><Typography variant="h4">{title}</Typography>{sub && <Typography color="text.secondary">{sub}</Typography>}</Box>{action}</Stack>; }
export function State({ query, children }: {
    query: {
        isLoading: boolean;
        isError: boolean;
        error?: unknown;
        refetch: () => unknown;
    };
    children: React.ReactNode;
}) { if (query.isLoading)
    return <LinearProgress />; if (query.isError)
    return <Alert severity="error" action={<Button onClick={() => query.refetch()}>Повторить</Button>}>{errorText(query.error)}</Alert>; return <>{children}</>; }
export function Empty({ text = 'Нет данных', kind = 'courses', action }: {
    text?: string;
    kind?: EmptyKind;
    action?: React.ReactNode;
}) { return <Paper className="empty visual-empty"><img src={emptyImages[kind]} alt=""/><Typography color="text.secondary">{text}</Typography>{action}</Paper>; }
export function Status({ value }: {
    value: any;
}) { return <Chip size="small" label={label(value)} color={['ACTIVE', 'GRADED', 'SUBMITTED', 'UP'].includes(value) ? 'success' : ['OVERDUE', 'EXPIRED', 'DOWN'].includes(value) ? 'error' : ['IN_PROGRESS', 'INACTIVE'].includes(value) ? 'warning' : 'default'}/>; }
export function BusyButton({ busy, children, ...props }: {
    busy: boolean;
    children: React.ReactNode;
    [k: string]: any;
}) { return <Button variant="contained" {...props} disabled={busy || props.disabled}>{busy ? <CircularProgress size={18} color="inherit"/> : children}</Button>; }
