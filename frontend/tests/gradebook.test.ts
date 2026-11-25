import {describe,it,expect} from 'vitest';
import {mergeGradebook,type GradebookPage} from '../src/shared/gradebook-pages';
const page=(content:any[],index=0,totalPages=2)=>({content,page:index,size:20,totalElements:totalPages*20,totalPages,first:index===0,last:index+1>=totalPages});
const block=(row:number,col:number):GradebookPage=>({columnPage:col,columnSize:20,totalColumns:2,totalColumnPages:2,columns:[{id:1,type:col?'QUIZ':'ASSIGNMENT'}],students:page([{studentId:row+1,studentName:`Student ${row+1}`,results:[{assessmentId:1,type:col?'QUIZ':'ASSIGNMENT',score:row+col}]}],row)});
describe('Gradebook page merging',()=>{
it('keeps separate assessments even if quiz and assignment IDs overlap',()=>{const m=mergeGradebook([block(0,0),block(0,1),block(1,0),block(1,1)]);expect(m.columns).toHaveLength(2);expect(m.students.map(s=>s.studentId)).toEqual([1,2]);expect(m.students.every(s=>s.results.length===2)).toBe(true);expect(m.students[1].results[1].score).toBe(2)});
it('does not duplicate existing students or results after refresh',()=>{const m=mergeGradebook([block(0,0),block(0,0)]);expect(m.students).toHaveLength(1);expect(m.students[0].results).toHaveLength(1)});
});

import {groupGradebookColumns, gradebookColumnTitle, gradebookTone} from '../src/shared/gradebook-presentation';
describe('Gradebook presentation',()=>{
    it('groups contiguous types without reordering columns or losing module distinctions',()=>{
        const columns=[{type:'ASSIGNMENT',moduleId:1},{type:'ASSIGNMENT',moduleId:2},{type:'QUIZ',moduleId:1},{type:'ASSIGNMENT',moduleId:3}];
        expect(groupGradebookColumns(columns)).toEqual([{type:'ASSIGNMENT',title:'Домашние задания',count:2},{type:'QUIZ',title:'Тесты',count:1},{type:'ASSIGNMENT',title:'Домашние задания',count:1}]);
        expect(columns.map(c=>c.moduleId)).toEqual([1,2,1,3]);
    });
    it('only strips known repeated prefixes from the visible title',()=>{
        expect(gradebookColumnTitle({title:'Практика: Ввод и браузер'})).toBe('Ввод и браузер');
        expect(gradebookColumnTitle({title:'Проверка знаний: SQL'})).toBe('SQL');
        expect(gradebookColumnTitle({title:'Контрольная работа: SQL'})).toBe('Контрольная работа: SQL');
    });
    it('uses score relative to maximum and preserves zero',()=>{
        const c={type:'ASSIGNMENT',maxScore:20};
        expect([17,14,13,0].map(score=>gradebookTone(c,{score,status:'GRADED'}))).toEqual(['high','medium','low','low']);
    });
    it('distinguishes unstarted, overdue, pending and expired without introducing statuses',()=>{
        const assignment={type:'ASSIGNMENT',maxScore:100},quiz={type:'QUIZ',maxScore:100};
        expect(gradebookTone(assignment,{status:'OVERDUE'})).toBe('low');
        expect(gradebookTone(assignment,{status:'SUBMITTED'})).toBe('pending');
        expect(gradebookTone(quiz,{status:'NOT_STARTED'})).toBe('neutral');
        expect(gradebookTone(quiz,{status:'EXPIRED'})).toBe('expired');
        expect(gradebookTone(quiz,undefined)).toBe('neutral');
    });
});
