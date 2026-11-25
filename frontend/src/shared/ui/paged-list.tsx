import React, { useEffect, useState } from 'react';
import { Alert, Autocomplete, Button, LinearProgress, MenuItem, Stack, TextField } from '@mui/material';
import { Plus, RefreshCw } from 'lucide-react';
import { type EmptyKind } from '../visual';
import { Data, errorText, label, readSession } from '../api';
import { usePaged, uniqueRows, rowKey, LoadMore } from '../infinite';
import { names } from '../labels';
import type { Col } from './data-table';
import { listScroll } from './data-table';
import { technicalKey } from './display-field';
import { Rows } from './data-table';
const listPreferences = new Map<string, {
    filters: Data;
    sort: string;
    direction: string;
}>();
export type Filter = {
    key: string;
    title: string;
    searchable?: boolean;
    options?: {
        value: string;
        label: string;
    }[];
};
type PagedProps = {
    path: string;
    params?: Data;
    cols: (string | Col)[];
    filters?: Filter[];
    actions?: (r: Data) => React.ReactNode;
    create?: () => void;
    sorts?: string[];
    emptyKind?: EmptyKind;
    emptyText?: string;
    createLabel?: string;
    resettable?: boolean;
    headerSort?: boolean;
    tableClassName?: string;
};
export function Paged(props: PagedProps) { return <PagedList key={JSON.stringify([props.path, props.params])} {...props}/>; }
function PagedList({ path, params = {}, cols, filters = [], actions, create, sorts = [], emptyKind, emptyText, createLabel = "Создать", resettable = false, headerSort = false, tableClassName }: PagedProps) {
    const scope = JSON.stringify([readSession()?.userId, path, params]);
    const saved = listPreferences.get(scope);
    const [f, F] = useState<Data>(() => saved?.filters ?? {}), [deb, D] = useState<Data>(() => saved?.filters ?? {}), [sort, T] = useState(() => saved?.sort ?? ''), [direction, V] = useState(() => saved?.direction ?? 'ASC');
    useEffect(() => { listPreferences.set(scope, { filters: f, sort, direction }); if (listPreferences.size > 200)
        listPreferences.delete(listPreferences.keys().next().value!); }, [scope, f, sort, direction]);
    useEffect(() => { const t = setTimeout(() => D(f), 350); return () => clearTimeout(t); }, [f]);
    const queryParams = { ...params, ...Object.fromEntries(Object.entries(deb).filter(([, v]) => v !== '')), ...(sort ? { sortBy: sort, direction } : {}) };
    const q = usePaged(path, queryParams), rows = uniqueRows(q.data?.pages.flatMap(p => p.content) ?? [], rowKey), total = q.data?.pages[0]?.totalElements ?? 0;
    const waiting = JSON.stringify(f) !== JSON.stringify(deb), scrollKey = JSON.stringify([readSession()?.userId, path, queryParams]);
    const resetScroll = (next: Data, nextSort = sort, nextDirection = direction) => { const p = { ...params, ...Object.fromEntries(Object.entries(next).filter(([, v]) => v !== '')), ...(nextSort ? { sortBy: nextSort, direction: nextDirection } : {}) }; listScroll.delete(JSON.stringify([readSession()?.userId, path, p])); };
    const load = () => { if (q.isFetching || waiting)
        return; if (q.isError && !q.isFetchNextPageError)
        void q.refetch();
    else if (q.hasNextPage || q.isFetchNextPageError)
        void q.fetchNextPage({ cancelRefetch: false }); };
    return <Stack gap={2}><Stack direction="row" gap={2} flexWrap="wrap">{filters.map(x => x.searchable ? <Autocomplete key={x.key} size="small" sx={{ minWidth: 240 }} options={x.options || []} value={x.options?.find(o => o.value === f[x.key]) || null} getOptionLabel={o => o.label} isOptionEqualToValue={(a, b) => a.value === b.value} onChange={(_, v) => { const next = { ...f, [x.key]: v?.value || '' }; resetScroll(next); F(next); }} renderInput={p => <TextField {...p} label={x.title}/>}/> : <TextField key={x.key} size="small" sx={{ minWidth: 150 }} select={!!x.options} label={x.title} value={f[x.key] || ''} onChange={e => { const next = { ...f, [x.key]: e.target.value }; resetScroll(next); F(next); }}>{x.options && [<MenuItem key="all" value="">Все</MenuItem>, ...x.options.map(o => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)]}</TextField>)}{sorts.length > 0 && !headerSort && <><TextField select size="small" label="Сортировка" value={sort} onChange={e => { resetScroll(f, e.target.value); T(e.target.value); }} sx={{ minWidth: 150 }}><MenuItem value="">По умолчанию</MenuItem>{sorts.filter(k => !technicalKey(k)).map(k => <MenuItem key={k} value={k}>{names[k] || k}</MenuItem>)}</TextField><TextField select size="small" value={direction} onChange={e => { resetScroll(f, sort, e.target.value); V(e.target.value); }}><MenuItem value="ASC">По возрастанию</MenuItem><MenuItem value="DESC">По убыванию</MenuItem></TextField></>}{resettable && <Button onClick={() => { resetScroll({}, "", "ASC"); F({}); T(""); V("ASC"); }}>Сбросить</Button>}<Button onClick={() => void q.refetch()} disabled={q.isFetching} startIcon={<RefreshCw size={16}/>}>Обновить</Button>{create && <Button variant="contained" startIcon={<Plus size={16}/>} onClick={create}>{createLabel}</Button>}</Stack>
 {waiting || q.isPending ? <LinearProgress /> : q.isError && !q.data ? <Alert severity="error" action={<Button onClick={() => void q.refetch()}>Повторить</Button>}>{errorText(q.error)}</Alert> : <Rows key={scrollKey} scrollKey={scrollKey} rows={rows} cols={cols} actions={actions} className={tableClassName} sortControl={headerSort ? { keys: sorts, key: sort, direction, change: k => { const next = sort === k && direction === "ASC" ? "DESC" : "ASC"; resetScroll(f, k, next); T(k); V(next); } } : undefined} emptyKind={emptyKind} emptyText={emptyText} footer={<LoadMore hasMore={!!q.hasNextPage} busy={q.isFetching} failed={q.isError} error={q.error} load={load} loaded={rows.length} total={total}/>}/>}</Stack>;
}
export const options = (values: string[]) => values.map(value => ({ value, label: label(value) }));
