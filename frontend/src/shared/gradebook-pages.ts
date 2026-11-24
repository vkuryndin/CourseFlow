import type {Data,Page} from './api';
export type GradebookPage={columns:Data[];students:Page<Data>;columnPage:number;columnSize:number;totalColumns:number;totalColumnPages:number};
export function mergeGradebook(pages:GradebookPage[]){
 const columns=new Map<string,Data>(),students=new Map<number,Data>();
 // Row-major order is supplied by the caller. Keep a single student per id and results per assessment type/id.
 for(const p of pages){for(const c of p.columns)columns.set(`${c.type}:${c.id}`,c);
 for(const s of p.students.content){const old=students.get(s.studentId);const results=new Map<string,Data>();for(const r of [...(old?.results||[]),...(s.results||[])])results.set(`${r.type}:${r.assessmentId}`,r);students.set(s.studentId,{...s,results:Array.from(results.values())})}}
 return {columns:Array.from(columns.values()),students:Array.from(students.values())};
}
