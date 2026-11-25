import type { Data } from './api';
export const gradebookLegend = [
    ['high','85–100%'], ['medium','70–84%'], ['low','Менее 70% / просрочено'],
    ['pending','Ожидает проверки'], ['expired','Время истекло'], ['neutral','Нет результата']
] as const;
export function groupGradebookColumns(columns: readonly Data[]) {
    const groups: {type:string; title:string; count:number}[] = [];
    for (const c of columns) {
        const type = String(c.type), last = groups.at(-1);
        if (last?.type === type) last.count++;
        else groups.push({type, title:type === 'QUIZ' ? 'Тесты' : 'Домашние задания', count:1});
    }
    return groups;
}
export function gradebookColumnTitle(column: Data) {
    const full = String(column.title || 'Без названия');
    return full.replace(/^(?:Практика|Проверка знаний|Домашнее задание|Задание|Тест)\s*:\s*/iu,'').trim() || full;
}
export function gradebookTone(column: Data, result?: Data) {
    if (result?.score != null && Number.isFinite(Number(result.score)) && Number(column.maxScore) > 0) {
        const percent = Number(result.score) * 100 / Number(column.maxScore);
        return percent >= 85 ? 'high' : percent >= 70 ? 'medium' : 'low';
    }
    if (result?.status === 'OVERDUE') return 'low';
    if (result?.status === 'SUBMITTED' && column.type === 'ASSIGNMENT') return 'pending';
    if (result?.status === 'EXPIRED') return 'expired';
    return 'neutral';
}
