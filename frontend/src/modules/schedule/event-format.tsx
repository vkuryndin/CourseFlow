import { formatTime } from '../../shared/datetime';
import type { EventInput } from '@fullcalendar/core';
import { Data } from '../../shared/api';
import { type TimedEvent } from '../../shared/schedule-utils';
export type ScheduleEvent = TimedEvent & EventInput & {
    id: string;
    title: string;
    extendedProps: Data;
};
export const sessionLabels = { upcoming: 'Предстоит', ongoing: 'По расписанию идёт сейчас', ended: 'Время занятия истекло', unknown: 'Время не определено' };
export const fullDate = (value: string) => { const d = new Date(value); return Number.isFinite(+d) ? new Intl.DateTimeFormat('ru-RU', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(d) : '—'; };
export function eventTime(p: Data) { return p.kind === 'assignment' ? formatTime(p.dueAt) : `${formatTime(p.startsAt)}–${formatTime(p.endsAt)}`; }
