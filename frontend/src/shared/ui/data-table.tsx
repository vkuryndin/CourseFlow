import React, { useLayoutEffect, useRef } from 'react';
import { Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TableSortLabel } from '@mui/material';
import { type EmptyKind } from '../visual';
import { Data } from '../api';
import { rowKey } from '../infinite';
import { names } from '../labels';
import { technicalKey } from './display-field';
import { Status } from './page-state';
import { displayField } from './display-field';
import { Empty } from './page-state';
export type Col = {
    key: string;
    title?: string;
    render?: (row: Data) => React.ReactNode;
};
export const listScroll = new Map<string, number>();
export function Rows({ rows, cols, actions, emptyKind, emptyText, footer, scrollKey, className, sortControl }: {
    rows: Data[];
    cols: (string | Col)[];
    actions?: (r: Data) => React.ReactNode;
    emptyKind?: EmptyKind;
    emptyText?: string;
    footer?: React.ReactNode;
    scrollKey?: string;
    className?: string;
    sortControl?: { keys: string[]; key: string; direction: string; change: (key: string) => void };
}) {
    cols = cols.filter(c => typeof c === 'string' ? !technicalKey(c) : !technicalKey(c.key) || !!c.render);
    const ref = useRef<HTMLDivElement>(null);
    const restored = useRef(false);
    useLayoutEffect(() => { if (!scrollKey || !ref.current || restored.current)
        return; ref.current.scrollTop = listScroll.get(scrollKey) || 0; restored.current = true; }, [scrollKey, rows.length]);
    return rows.length ? <TableContainer component={Paper} ref={ref} className={[footer ? 'infinite-table' : '', className].filter(Boolean).join(' ') || undefined} onScroll={e => { if (scrollKey) {
        listScroll.set(scrollKey, e.currentTarget.scrollTop);
        if (listScroll.size > 200)
            listScroll.delete(listScroll.keys().next().value!);
    } }}><Table size="small" stickyHeader={!!footer}><TableHead><TableRow>{cols.map(c => { const k = typeof c === 'string' ? c : c.key, title = typeof c === 'string' ? names[c] || c : c.title || names[c.key] || c.key; const active = sortControl?.key === k; return <TableCell key={k} sortDirection={active ? (sortControl?.direction === 'DESC' ? 'desc' : 'asc') : false}>{sortControl?.keys.includes(k) ? <TableSortLabel active={active} direction={active && sortControl.direction === 'DESC' ? 'desc' : 'asc'} onClick={() => sortControl.change(k)}>{title}</TableSortLabel> : title}</TableCell>; })}{actions && <TableCell>Действия</TableCell>}</TableRow></TableHead><TableBody>{rows.map((r, i) => <TableRow hover key={scrollKey ? rowKey(r) : `${rowKey(r)}:${i}`}>{cols.map(c => { const k = typeof c === 'string' ? c : c.key; return <TableCell key={k}>{typeof c !== 'string' && c.render ? c.render(r) : k === 'status' ? <Status value={r[k]}/> : displayField(k, r[k])}</TableCell>; })}{actions && <TableCell><Stack direction="row" gap={1} flexWrap="wrap">{actions(r)}</Stack></TableCell>}</TableRow>)}</TableBody></Table>{footer}</TableContainer> : <><Empty kind={emptyKind} text={emptyText}/>{footer}</>;
}
