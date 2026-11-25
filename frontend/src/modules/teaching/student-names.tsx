import { Data, useAll } from '../../shared/api';
export function useStudentNames(course: string) { const q = useAll(`/api/courses/${course}/enrollments`, {}, !!course); const names = new Map<string, string>(); for (const r of q.data || [])
    names.set(String(r.studentId), String(r.studentName || 'Имя недоступно')); return { q, names, options: Array.from(names, ([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label, 'ru')) }; }
export const studentName = (r: Data, names: Map<string, string>) => r.studentName || names.get(String(r.studentId)) || 'Имя недоступно';
