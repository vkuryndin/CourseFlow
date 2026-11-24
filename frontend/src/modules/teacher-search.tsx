import {ServerSearch,type SearchItem} from '../shared/server-search';
export type PublicTeacher=SearchItem&{displayName:string};
export function TeacherSearch({value,onChange}:{value:PublicTeacher|null;onChange:(value:PublicTeacher|null)=>void}){return <ServerSearch<PublicTeacher> path="/api/teachers" label="Преподаватель" value={value} onChange={onChange} name={t=>t.displayName} placeholder="Все преподаватели" loadingText="Загрузка преподавателей…" emptyText="Преподаватели не найдены" errorText="Не удалось загрузить преподавателей"/>}
