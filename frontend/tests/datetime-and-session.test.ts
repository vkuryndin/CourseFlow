import {describe,it,expect} from 'vitest';
import {dateOnly,clock,parseLocal,serializeDate,formatDate,durationText} from '../src/shared/datetime';
import {buildPayload,initialValues} from '../src/shared/forms';
import {displayField} from '../src/shared/ui';
import {names} from '../src/shared/labels';
const future=new Date(Date.now()+86400000),end=new Date(+future+65*60000);
const startText=dateOnly(future)+' '+clock(future),endText=dateOnly(end)+' '+clock(end);
const session={startsAt:startText,endsAt:endText,format:'ONLINE',meetingUrl:'https://example.org/meeting'};
describe('Date and session validation',()=>{
 it('formats time in 24-hour format and preserves local date',()=>{expect(formatDate(new Date(2026,10,25,19,52))).toBe('25.11.2026 19:52');expect(formatDate('2026-11-25',false)).toBe('25.11.2026');expect(serializeDate('25.11.2026 19:52',true)).toBe(new Date(2026,10,25,19,52).toISOString());expect(serializeDate('25.11.2026',false)).toBe('2026-11-25')});
 it('rejects impossible dates and 12-hour notation',()=>{expect(parseLocal('31.02.2026 19:52',true)).toBeNull();expect(parseLocal('25.11.2026 07:52 PM',true)).toBeNull();expect(parseLocal('25.11.2026 24:00',true)).toBeNull()});
 it('does not parse format as a date',()=>{expect(displayField('format','ONLINE')).toBe('Онлайн');expect(displayField('format','OFFLINE')).toBe('Очно');expect(displayField('startsAt','bad')).toBe('—');expect(names.moduleTitle).toBe('Модуль')});
 it('checks session URL and date interval',()=>{expect(()=>buildPayload('CreateLessonSessionRequest',{...session,meetingUrl:''})).toThrow(/URL/);expect(()=>buildPayload('CreateLessonSessionRequest',{...session,meetingUrl:'javascript:alert(1)'})).toThrow(/URL/);expect(()=>buildPayload('CreateLessonSessionRequest',{...session,endsAt:startText})).toThrow(/позже/);expect(()=>buildPayload('CreateLessonSessionRequest',{...session,startsAt:'01.01.2000 10:00'})).toThrow(/будущем/);expect(buildPayload('CreateLessonSessionRequest',{...session,format:'OFFLINE'}).meetingUrl).toBeNull();expect(buildPayload('CreateLessonSessionRequest',session).meetingUrl).toBe(session.meetingUrl)});
 it('round-trips date input and computes duration',()=>{const payload=buildPayload('CreateLessonSessionRequest',session);expect(initialValues('CreateLessonSessionRequest',payload).startsAt).toBe(startText);expect(durationText(payload.startsAt,payload.endsAt)).toBe('1 ч 5 мин')});
});
