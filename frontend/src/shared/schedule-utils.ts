export type CalendarRange={start:Date;end:Date};
export type TimedEvent={start:string;end?:string;extendedProps?:{kind?:string}};
/** Keep all events intersecting the visible dates, including overnight sessions. */
export function visibleHours(events:TimedEvent[],range:CalendarRange|null,fullDay:boolean):{min:string;max:string}{
  let min=fullDay?0:8;
  for(const event of events){
    const start=new Date(event.start),end=new Date(event.end??event.start);
    if(!Number.isFinite(+start)||!Number.isFinite(+end))continue;
    if(range&&(+start>=+range.end||(+end>+start?+end<=+range.start:+start<+range.start)))continue;
    const effectiveStart=range&&start<range.start?range.start:start;
    min=Math.min(min,effectiveStart.getHours());
    // For an interval crossing midnight, include early hours on every visible day it overlaps.
    if(end>start){
      const midnight=new Date(effectiveStart.getFullYear(),effectiveStart.getMonth(),effectiveStart.getDate()+1);
      if(midnight<end&&(!range||midnight<range.end))min=0;
    }
  }
  return {min:`${String(min).padStart(2,'0')}:00:00`,max:'24:00:00'};
}
export function sessionState(start:string,end:string,now:number):'upcoming'|'ongoing'|'ended'|'unknown'{
  const a=Date.parse(start),b=Date.parse(end);
  if(!Number.isFinite(a)||!Number.isFinite(b)||b<=a)return 'unknown';
  return now<a?'upcoming':now<b?'ongoing':'ended';
}
