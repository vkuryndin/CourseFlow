import { get, Data, errorText } from '../../shared/api';
export const custom: Record<string, string> = { 'courseflow.auth.rate_limited': 'Срабатывания Rate Limiter', 'courseflow.password.reset.requests': 'Запросы восстановления пароля', 'courseflow.password.reset.accepted': 'Принято на обработку', 'courseflow.password.reset.rejected': 'Отклонено исполнителем', 'courseflow.password.reset.processing_failed': 'Ошибки обработки', 'courseflow.password.reset.mail_sent': 'Письма отправлены', 'courseflow.password.reset.mail_failed': 'Ошибки SMTP' };
const standard = ['process.uptime', 'process.cpu.usage', 'system.cpu.usage', 'jvm.memory.used', 'jvm.memory.max', 'jvm.memory.committed', 'jvm.threads.live', 'jvm.threads.peak', 'jvm.gc.pause', 'http.server.requests', 'hikaricp.connections.active', 'hikaricp.connections.idle', 'hikaricp.connections.pending', 'hikaricp.connections.max', 'disk.free', 'disk.total'];
export type Metric = {
    name: string;
    baseUnit?: string;
    description?: string;
    measurements: {
        statistic: string;
        value: number;
    }[];
    availableTags?: {
        tag: string;
        values: string[];
    }[];
};
export type Sample = {
    at: number;
    values: Record<string, Metric>;
    status: Data;
    health: Data;
    errors: string[];
};
export const value = (m: Metric | undefined, stat = 'VALUE') => m?.measurements.find(x => x.statistic === stat)?.value;
export const delta = (a: number | undefined, b: number | undefined) => a == null || b == null || a < b ? null : a - b;
export const mb = (x: number | undefined) => x == null || x < 0 ? '—' : `${(x / 1048576).toFixed(1)} МБ`;
export const pct = (x: number | undefined) => x == null ? '—' : `${(x * 100).toFixed(1)}%`;
export const num = (x: number | undefined) => x == null ? '—' : x.toLocaleString('ru-RU', { maximumFractionDigits: 2 });
export const uptime = (x: number | undefined) => x == null ? '—' : `${Math.floor(x / 3600)} ч ${Math.floor(x / 60) % 60} мин`;
export async function collect(names: string[]): Promise<Sample> { const values: Record<string, Metric> = {}, errors: string[] = []; const [status, health] = await Promise.all([get('/api/system/status').catch(e => ({ error: errorText(e) })), get('/actuator/health').catch(e => ({ error: errorText(e) }))]); const available = new Set(names); const jobs = [...standard, ...Object.keys(custom)].filter(n => available.has(n)).map(n => ({ key: n, name: n, tag: n.startsWith('jvm.memory.') ? 'area:heap' : undefined })); if (available.has('http.server.requests'))
    for (const outcome of ['SUCCESS', 'CLIENT_ERROR', 'SERVER_ERROR', 'REDIRECTION', 'INFORMATIONAL', 'UNKNOWN'])
        jobs.push({ key: 'http.' + outcome, name: 'http.server.requests', tag: 'outcome:' + outcome }); for (let i = 0; i < jobs.length; i += 5)
    await Promise.all(jobs.slice(i, i + 5).map(async (j) => { try {
        values[j.key] = await get<Metric>('/actuator/metrics/' + encodeURIComponent(j.name), j.tag ? { tag: j.tag } : undefined);
    }
    catch (e) {
        if (!j.key.startsWith('http.'))
            errors.push(`${j.key}: ${errorText(e)}`);
    } })); return { at: Date.now(), values, status, health, errors }; }
