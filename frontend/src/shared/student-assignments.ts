import {useAll} from './api';
import type {Data} from './api';
export const assignmentLabels:Record<string,string>={NOT_SUBMITTED:'Не сдано',OVERDUE:'Просрочено',SUBMITTED:'На проверке',GRADED:'Проверено'};
export const assignmentColor=(status?:string):'default'|'error'|'warning'|'success'=>status==='OVERDUE'?'error':status==='SUBMITTED'?'warning':status==='GRADED'?'success':'default';
export function useStudentAssignments(courseId?:string,enabled=true){return useAll('/api/users/me/assignments',courseId?{courseId}:undefined,enabled)}
export function assignmentStatus(row?:Data):string|undefined{return row&&typeof row.status==='string'&&Object.hasOwn(assignmentLabels,row.status)?row.status:undefined}
