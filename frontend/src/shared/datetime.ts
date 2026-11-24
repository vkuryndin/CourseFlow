// Local wall-clock presentation; backend instants remain ISO 8601 UTC.
const pad=(n:number)=>String(n).padStart(2,'0');
export const dateOnly=(d:Date)=>`${pad(d.getDate())}.${pad(d.getMonth()+1)}.${d.getFullYear()}`;
export const clock=(d:Date)=>`${pad(d.getHours())}:${pad(d.getMinutes())}`;
export function parseLocal(value:string,withTime:boolean):Date|null{
  const m=value.trim().match(withTime?/^(\d{2})\.(\d{2})\.(\d{4}) (\d{2}):(\d{2})$/:/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if(!m)return null;
  const [,dd,mm,yyyy,hh='00',min='00']=m;const y=+yyyy,mo=+mm,day=+dd,h=+hh,mi=+min;
  if(y<1000||mo<1||mo>12||day<1||day>31||h>23||mi>59)return null;
  const d=new Date(y,mo-1,day,h,mi,0,0);
  return d.getFullYear()===y&&d.getMonth()===mo-1&&d.getDate()===day&&d.getHours()===h&&d.getMinutes()===mi?d:null;
}
export function formatDate(value:unknown,withTime=true):string{
  if(value==null||value==='')return '—';
  if(typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value)){const [y,m,d]=value.split('-');const local=`${d}.${m}.${y}`;return parseLocal(local,false)?local:'—'}
  if(typeof value!=='string'&&typeof value!=='number'&&!(value instanceof Date))return '—';
  const d=value instanceof Date?value:new Date(value);return Number.isFinite(+d)?dateOnly(d)+(withTime?' '+clock(d):''):'—';
}
export function formatTime(value:unknown):string{if(value==null||value==='')return '—';const d=new Date(value as string|number);return Number.isFinite(+d)?clock(d):'—'}
export function inputDate(value:unknown,withTime:boolean):string{
  if(typeof value==='string'&&parseLocal(value,withTime))return value;
  const result=formatDate(value,withTime);return result==='—'?'':result;
}
export function serializeDate(value:string,withTime:boolean):string{
  const d=parseLocal(value,withTime);if(!d)throw new Error(withTime?'Введите корректные дату и время (ДД.ММ.ГГГГ ЧЧ:ММ, 24 часа)':'Введите корректную дату (ДД.ММ.ГГГГ)');
  return withTime?d.toISOString():`${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
}
export function durationText(start:unknown,end:unknown):string{const minutes=Math.round((Date.parse(String(end))-Date.parse(String(start)))/60000);return Number.isFinite(minutes)&&minutes>=0?[Math.floor(minutes/60)?`${Math.floor(minutes/60)} ч`:'',minutes%60?`${minutes%60} мин`:''].filter(Boolean).join(' ')||'0 мин':'—'}
