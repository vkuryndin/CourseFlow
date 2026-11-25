import type {RefObject,Dispatch,SetStateAction} from 'react';
import {Button,Paper,Typography,Table,TableBody,TableCell,TableContainer,TableHead,TableRow,Tooltip} from '@mui/material';
import {type Data,label} from '../../shared/api';
import {gradebookColumnTitle, gradebookTone, groupGradebookColumns} from '../../shared/gradebook-presentation';
type Props={scroll:RefObject<HTMLDivElement>;onScroll:(el:HTMLDivElement)=>void;filter:Data;F:Dispatch<SetStateAction<Data>>;columns:Data[];students:Data[];hovered:string;Hover:(s:string)=>void;busy:boolean;hasFailures:boolean;course:string;courseTitle?:string;Cell:(data:Data)=>void};
const keyOf = (c:Data) => `${c.type}-${c.id}`;
const groupStart = (columns:Data[], i:number) => i === 0 || columns[i-1].type !== columns[i].type;
export function GradebookTable({scroll,onScroll,filter,F,columns,students,hovered,Hover,busy,hasFailures,course,courseTitle,Cell}:Props){
    const groups = groupGradebookColumns(columns);
    return <TableContainer component={Paper} ref={scroll} className="gradebook-scroll" onScroll={e => onScroll(e.currentTarget)}>
        <Table stickyHeader size="small" className="gradebook gradebook-redesign" sx={{tableLayout:"fixed",minWidth:205 + columns.length * 178,width:"100%"}} aria-label="Электронный журнал">
            <colgroup><col className="gradebook-student-width"/></colgroup>
            {groups.map((g,i) => <colgroup key={`${g.type}-${i}`}><col span={g.count} className="gradebook-assessment-width"/></colgroup>)}
            <TableHead><TableRow><TableCell rowSpan={2} className="sticky-student" scope="col" sortDirection={filter.sortBy === 'studentName' ? (filter.direction === 'DESC' ? 'desc' : 'asc') : false}><Button size="small" onClick={() => F(old => ({...old,sortBy:'studentName',direction:old.direction === 'ASC' ? 'DESC':'ASC'}))}>Студент {filter.sortBy === 'studentName' ? (filter.direction === 'ASC' ? '↑':'↓'):'↕'}</Button></TableCell>
            {groups.map((g,i) => <TableCell key={`${g.type}-${i}`} component="th" role="columnheader" colSpan={g.count} align="center" scope="colgroup" className={`gradebook-group ${g.type === 'QUIZ'?'quiz-column':'assignment-column'}`}>{g.title}</TableCell>)}</TableRow>
            <TableRow>{columns.map((c,i) => <TableCell key={keyOf(c)} scope="col" align="center" className={`${c.type === 'QUIZ'?'quiz-column':'assignment-column'} gradebook-work ${groupStart(columns,i)?'gradebook-group-start':''} ${hovered === keyOf(c)?'gradebook-header-highlight':''}`}>
                <Tooltip title={`${c.title}${c.moduleTitle ? ` · Модуль: ${c.moduleTitle}`:''}`} placement="top"><Typography component="span" variant="body2" fontWeight={600} className="gradebook-work-title" tabIndex={0}>{gradebookColumnTitle(c)}</Typography></Tooltip><Typography variant="caption" display="block" color="text.secondary">{c.maxScore ?? '—'} б.</Typography>
            </TableCell>)}</TableRow></TableHead>
            <TableBody>{students.map(s => <TableRow key={s.studentId}><TableCell component="th" scope="row" className="sticky-student">{s.studentName}</TableCell>{columns.map((c,i) => {
                const v = s.results.find((r:Data) => r.assessmentId === c.id && r.type === c.type);
                const status = v?.status === 'SUBMITTED' && c.type === 'ASSIGNMENT' ? 'Ожидает проверки' : v ? label(v.status) : 'Нет результата';
                const text = v ? v.score ?? status : busy ? '…' : '—';
                return <TableCell key={keyOf(c)} align="center" className={`gradebook-result tone-${gradebookTone(c,v)} ${groupStart(columns,i)?'gradebook-group-start':''} ${hovered === keyOf(c)?'gradebook-column-highlight':''}`} onMouseEnter={() => Hover(keyOf(c))} onMouseLeave={() => Hover('')}>
                    <Button size="small" className="gradebook-cell-button" disabled={!v && (busy || hasFailures)} title={`${c.title} · ${status} · максимум ${c.maxScore ?? '—'}`} onFocus={() => Hover(keyOf(c))} onBlur={() => Hover('')} onClick={() => Cell({...c,courseId:course,courseTitle,studentId:s.studentId,studentName:s.studentName,result:v})}>{text}</Button>
                </TableCell>;
            })}</TableRow>)}</TableBody>
        </Table>
    </TableContainer>;
}
