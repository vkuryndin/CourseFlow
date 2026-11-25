import { Video, Users, ClipboardList } from 'lucide-react';
import type { EventContentArg } from '@fullcalendar/core';
import { eventTime } from './event-format';
export function EventView({ arg }: {
    arg: EventContentArg;
}) {
    const p = arg.event.extendedProps, deadline = p.kind === 'assignment', Icon = deadline ? ClipboardList : p.format === 'ONLINE' ? Video : Users;
    const list = arg.view.type.startsWith('list'), week = arg.view.type.startsWith('timeGrid');
    const mins = arg.event.start && arg.event.end ? (+arg.event.end - +arg.event.start) / 60000 : 0;
    const short = week && !deadline && mins < 60;
    return <div className={`calendar-event-content ${deadline ? 'calendar-deadline-content' : ''} ${short ? 'calendar-short-content' : ''} ${list ? 'calendar-list-content' : ''}`}>
  <div className="calendar-event-clock"><Icon size={12} aria-hidden="true"/>{!list && <span>{eventTime(p)}</span>}{deadline && !list && <span className="calendar-deadline-caption">· Дедлайн</span>}</div>
  <span className="calendar-event-name">{arg.event.title}</span>
 </div>;
}
