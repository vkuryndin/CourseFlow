import {describe,it,expect} from 'vitest';
import {visibleHours,sessionState} from '../src/shared/schedule-utils';
import {catalogRoot,courseDetailsPath,teacherDetailsPath,studentLessonPath,editorLessonPath} from '../src/shared/course-navigation';
const local=(day:number,hour:number,minute=0)=>new Date(2026,10,day,hour,minute);
const week={start:local(23,0),end:local(30,0)};
const event=(start:Date,end?:Date)=>({start:start.toISOString(),...(end?{end:end.toISOString()}: {})});
describe('UI-070–072 time grid',()=>{
 it('defaults to 08:00–24:00',()=>expect(visibleHours([],week,false)).toEqual({min:'08:00:00',max:'24:00:00'}));
 it('includes an early session only in its own week',()=>{expect(visibleHours([event(local(24,6,30),local(24,7,30)),event(local(15,1))],week,false).min).toBe('06:00:00')});
 it('includes an early deadline',()=>expect(visibleHours([event(local(24,4,30))],week,false).min).toBe('04:00:00'));
 it('preserves late deadlines',()=>expect(visibleHours([event(local(29,23,59))],week,false).max).toBe('24:00:00'));
 it('includes early hours when a session spans midnight',()=>expect(visibleHours([event(local(24,23),local(25,1))],week,false).min).toBe('00:00:00'));
 it('does not expand for a session that ended at the start of the week',()=>expect(visibleHours([event(local(22,1),local(23,0))],week,false).min).toBe('08:00:00'));
 it('includes a session beginning before the visible week but ending inside it',()=>expect(visibleHours([event(local(22,23),local(23,3))],week,false).min).toBe('00:00:00'));
 it('does not expand for next week',()=>expect(visibleHours([event(local(30,1))],week,false).min).toBe('08:00:00'));
 it('allows explicit full day',()=>expect(visibleHours([],week,true).min).toBe('00:00:00'));
 it('ignores invalid times',()=>expect(visibleHours([{start:'bad'}],week,false).min).toBe('08:00:00'));
});
describe('UI-064 status is derived from scheduled times',()=>{
 const a=local(24,10).toISOString(),b=local(24,11).toISOString();
 it('distinguishes before, during and after including boundaries',()=>{expect(sessionState(a,b,+local(24,9))).toBe('upcoming');expect(sessionState(a,b,+local(24,10))).toBe('ongoing');expect(sessionState(a,b,+local(24,10,30))).toBe('ongoing');expect(sessionState(a,b,+local(24,11))).toBe('ended')});
 it('handles invalid intervals',()=>{expect(sessionState('bad',b,0)).toBe('unknown');expect(sessionState(b,a,0)).toBe('unknown')});
});
describe('UI-050–053,065,069 workspace navigation',()=>{
 it('keeps public routes unchanged',()=>{expect(catalogRoot(false)).toBe('/catalog');expect(courseDetailsPath(4)).toBe('/courses/4');expect(teacherDetailsPath(8)).toBe('/teachers/8')});
 it('uses non-conflicting student catalog routes',()=>{expect(catalogRoot(true)).toBe('/student/catalog');expect(courseDetailsPath(4,true)).toBe('/student/catalog/courses/4');expect(teacherDetailsPath(8,true)).toBe('/student/catalog/teachers/8');expect(studentLessonPath(4,2,3)).toBe('/student/courses/4/modules/2/lessons/3')});
 it('opens exact lesson in appropriate management workspace',()=>{expect(editorLessonPath('ADMIN',4,2,3)).toBe('/admin/courses/4/editor?moduleId=2&lessonId=3');expect(editorLessonPath('TEACHER',4,2,3)).toBe('/teacher/courses/4/editor?moduleId=2&lessonId=3')});
});
