import React from 'react';
import { Paper, Typography } from '@mui/material';
import { BookOpen, Users, ClipboardCheck, ListChecks, GraduationCap, Activity, Cpu, MemoryStick, Database, Clock, ShieldCheck, Mail, HardDrive, TriangleAlert, ChartNoAxesColumn } from 'lucide-react';
type StatTrend = readonly (number | null | undefined)[];
function statIcon(k: string) { const t = k.toLowerCase(); if (/ошиб|отклон|ожида/.test(t))
    return TriangleAlert; if (/rate limiter/.test(t))
    return ShieldCheck; if (/парол|письм|smtp/.test(t))
    return Mail; if (/cpu/.test(t))
    return Cpu; if (/heap|памят/.test(t))
    return MemoryStick; if (/postgres|соединен|^активные$|^свободные$|^максимум$/.test(t))
    return Database; if (/диск/.test(t))
    return HardDrive; if (/время|паузы/.test(t))
    return Clock; if (/пользовател|студент/.test(t))
    return Users; if (/кур[с]|запис/.test(t))
    return BookOpen; if (/задани|проверке|работ/.test(t))
    return ClipboardCheck; if (/тест|попыт/.test(t))
    return ListChecks; if (/успеваем|прогресс/.test(t))
    return GraduationCap; if (/http|запрос|поток|5xx|4xx/.test(t))
    return Activity; return ChartNoAxesColumn; }
export function Sparkline({ values }: {
    values: StatTrend;
}) { const finite = values.filter((v): v is number => typeof v === 'number' && Number.isFinite(v)); if (finite.length < 2)
    return null; const min = Math.min(...finite), max = Math.max(...finite), span = max - min; let pen = false, path = ''; values.forEach((v, i) => { if (typeof v !== 'number' || !Number.isFinite(v)) {
    pen = false;
    return;
} const x = 3 + i * 154 / Math.max(1, values.length - 1), y = span ? 35 - (v - min) / span * 30 : 20; path += `${pen ? 'L' : 'M'}${x.toFixed(2)} ${y.toFixed(2)} `; pen = true; }); return <svg className="stat-spark" viewBox="0 0 160 40" preserveAspectRatio="none" role="img" aria-label="Динамика за период наблюдения"><path d="M3 35H157" stroke="currentColor" strokeOpacity=".14"/><path d={path} stroke="currentColor" strokeWidth="2" fill="none" vectorEffect="non-scaling-stroke" strokeLinecap="round" strokeLinejoin="round"/></svg>; }
export function Stats({ items, trends = {} }: {
    items: [
        string,
        React.ReactNode
    ][];
    trends?: Record<string, StatTrend>;
}) { return <div className="stats visual-stats">{items.map(([k, v], i) => { const Icon = statIcon(k); return <Paper className={`stat visual-stat accent-${i % 4}`} key={k}><div className="stat-top"><div className="stat-text"><Typography color="text.secondary" variant="body2">{k}</Typography><Typography variant="h4">{v ?? '—'}</Typography></div><span className="stat-icon" aria-hidden="true"><Icon size={23}/></span></div>{trends[k] && <Sparkline values={trends[k]}/>}</Paper>; })}</div>; }
export function ProgressRing({ value }: {
    value: number | null | undefined;
}) { const valid = typeof value === 'number' && Number.isFinite(value), v = valid ? Math.max(0, Math.min(100, value)) : 0; return <svg className="progress-ring" viewBox="0 0 96 96" role="img" aria-label={valid ? `Прогресс ${v}%` : 'Прогресс неизвестен'}><circle cx="48" cy="48" r="39" fill="none" stroke="#E0E7FF" strokeWidth="8"/>{valid && <circle cx="48" cy="48" r="39" fill="none" stroke="#4F46E5" strokeWidth="8" strokeLinecap="round" pathLength="100" strokeDasharray={`${v} 100`} transform="rotate(-90 48 48)"/>}<text x="48" y="53" textAnchor="middle" fontSize="17" fontWeight="700" fill="#312E81">{valid ? `${Math.round(v)}%` : '—'}</text></svg>; }
