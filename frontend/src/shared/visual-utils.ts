/** Russian cardinal plural forms. Non-integer values use the genitive singular. */
export function plural(n:number, forms:readonly [string,string,string]):string {
  const i=new Intl.PluralRules('ru').select(n);
  return i==='one'?forms[0]:i==='few'||i==='other'?forms[1]:forms[2];
}
export const counted=(n:number, forms:readonly [string,string,string])=>`${n} ${plural(n,forms)}`;
export const reviewCount=(n:number)=>counted(n,['отзыв','отзыва','отзывов']);
export const courseCount=(n:number)=>counted(n,['курс','курса','курсов']);
export const dayCount=(n:number)=>counted(n,['день','дня','дней']);
export type CoverKind='programming'|'database'|'infrastructure'|'analytics'|'education';
export function coverKind(category:unknown):CoverKind {
  const s=String(category??'').toLowerCase();
  if(/database|postgres|mysql|\bsql\b|баз[аы] данн|базы|субд/.test(s))return 'database';
  if(/devops|kubernetes|docker|cloud|network|infra|администр|инфраструкт|облач|сет[ие]/.test(s))return 'infrastructure';
  if(/analytics|data science|machine learning|artificial intelligence|\bai\b|аналит|анализ данн|машинн|искусственн/.test(s))return 'analytics';
  if(/program|java|python|javascript|software|frontend|backend|web|разработ|программ|код|тестирован/.test(s))return 'programming';
  return 'education';
}
export function initials(name:string):string{return name.trim().split(/\s+/).filter(Boolean).slice(0,2).map(x=>Array.from(x)[0]?.toLocaleUpperCase('ru')||'').join('')||'?' }
