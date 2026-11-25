import { Data, safeUrl } from '../api';
import { names } from '../labels';
import contract from '../contract.json';
import { inputDate, serializeDate } from '../datetime';
const schemas = contract.schemas as unknown as Record<string, Data>;
export const schema = (n: string) => schemas[n];
export function initialValues(n: string, initial: Data) { const s = schema(n); return Object.fromEntries(Object.entries(s.properties as Data).map(([k, p]) => { let v = initial[k] ?? (p.type === 'boolean' ? false : p.type === 'array' ? [] : p.enum?.[0] ?? ''); if ((p.format === 'date-time' || p.format === 'date') && v)
    v = inputDate(v, p.format === 'date-time'); if (k === 'options' && v.length === 0)
    v = [{ text: '', correct: true }, { text: '', correct: false }]; return [k, v]; })); }
export function buildPayload(n: string, values: Data) { const s = schema(n), out: Data = {}; for (const [k, p] of Object.entries(s.properties as Data)) {
    let v = values[k];
    if (v === '' || v == null) {
        if (!s.required?.includes(k)) {
            if (p.type === 'string' && k !== 'version')
                out[k] = null;
            continue;
        }
        throw new Error(`${names[k] || k}: обязательное поле`);
    }
    if (['integer', 'number'].includes(p.type)) {
        v = Number(v);
        if (!Number.isFinite(v) || (p.type === 'integer' && !Number.isInteger(v)))
            throw new Error(`${names[k] || k}: некорректное число`);
        if (p.minimum != null && v < p.minimum || p.maximum != null && v > p.maximum)
            throw new Error(`${names[k] || k}: значение вне допустимого диапазона`);
    }
    if (typeof v === 'string') {
        if (s.required?.includes(k) && !v.trim())
            throw new Error(`${names[k] || k}: обязательное поле`);
        if (p.maxLength != null && v.length > p.maxLength || p.minLength != null && v.length < p.minLength)
            throw new Error(`${names[k] || k}: недопустимая длина`);
        if (p.format === 'date-time' || p.format === 'date')
            v = serializeDate(v, p.format === 'date-time');
    }
    if (k === 'roles' && v.length === 0)
        throw new Error('Выберите хотя бы одну роль');
    if (k === 'options') {
        v = v.map((o: Data) => ({ text: o.text.trim(), correct: !!o.correct }));
        if (v.length < 2 || v.length > 10 || v.some((o: Data) => !o.text || o.text.length > 2000) || !v.some((o: Data) => o.correct) || values.type === 'SINGLE_CHOICE' && v.filter((o: Data) => o.correct).length !== 1)
            throw new Error('Проверьте варианты ответов: 2–10, допустимое число правильных ответов и непустой текст');
    }
    out[k] = v;
} if (n === 'CreateLessonSessionRequest' || n === 'RescheduleLessonSessionRequest') {
    const start = Date.parse(out.startsAt), end = Date.parse(out.endsAt);
    if (start <= Date.now())
        throw new Error('Начало занятия должно быть в будущем');
    if (end <= start)
        throw new Error('Окончание занятия должно быть позже начала');
    if (n === 'CreateLessonSessionRequest') {
        if (out.format === 'ONLINE') {
            if (!out.meetingUrl?.trim())
                throw new Error('Для онлайн-занятия укажите URL встречи');
            const url = out.meetingUrl.trim();
            if (!/^https?:\/\//i.test(url) || !safeUrl(url))
                throw new Error('Укажите корректный URL встречи с http:// или https://');
            out.meetingUrl = url;
        }
        else if (out.format === 'OFFLINE')
            out.meetingUrl = null;
    }
} return out; }
