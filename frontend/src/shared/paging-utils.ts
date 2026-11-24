import type {Data,Page} from './api';
export function nextPage<T>(last:Page<T>){return last.last||last.page+1>=last.totalPages?undefined:last.page+1}
export function uniqueRows<T>(rows:T[],key:(r:T)=>string|number):T[]{return Array.from(new Map(rows.map(r=>[key(r),r])).values())}
export function rowKey(r:Data):string {for(const k of ['id','enrollmentId','submissionId','attemptId','assignmentId','quizId','courseId','studentId'])if(r[k]!=null)return `${k}:${r[k]}`;return JSON.stringify(r)}
