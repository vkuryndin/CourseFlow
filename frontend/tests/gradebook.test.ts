import {describe,it,expect} from 'vitest';
import {mergeGradebook,type GradebookPage} from '../src/shared/gradebook-pages';
const page=(content:any[],index=0,totalPages=2)=>({content,page:index,size:20,totalElements:totalPages*20,totalPages,first:index===0,last:index+1>=totalPages});
const block=(row:number,col:number):GradebookPage=>({columnPage:col,columnSize:20,totalColumns:2,totalColumnPages:2,columns:[{id:1,type:col?'QUIZ':'ASSIGNMENT'}],students:page([{studentId:row+1,studentName:`Student ${row+1}`,results:[{assessmentId:1,type:col?'QUIZ':'ASSIGNMENT',score:row+col}]}],row)});
describe('Gradebook page merging',()=>{
it('keeps separate assessments even if quiz and assignment IDs overlap',()=>{const m=mergeGradebook([block(0,0),block(0,1),block(1,0),block(1,1)]);expect(m.columns).toHaveLength(2);expect(m.students.map(s=>s.studentId)).toEqual([1,2]);expect(m.students.every(s=>s.results.length===2)).toBe(true);expect(m.students[1].results[1].score).toBe(2)});
it('does not duplicate existing students or results after refresh',()=>{const m=mergeGradebook([block(0,0),block(0,0)]);expect(m.students).toHaveLength(1);expect(m.students[0].results).toHaveLength(1)});
});
