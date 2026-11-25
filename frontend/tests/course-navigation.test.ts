import {describe,it,expect} from 'vitest';
import {catalogRoot,courseDetailsPath,teacherDetailsPath,studentLessonPath,editorLessonPath} from '../src/shared/course-navigation';
describe('Workspace navigation',()=>{
 it('keeps public routes unchanged',()=>{expect(catalogRoot(false)).toBe('/catalog');expect(courseDetailsPath(4)).toBe('/courses/4');expect(teacherDetailsPath(8)).toBe('/teachers/8')});
 it('uses non-conflicting student catalog routes',()=>{expect(catalogRoot(true)).toBe('/student/catalog');expect(courseDetailsPath(4,true)).toBe('/student/catalog/courses/4');expect(teacherDetailsPath(8,true)).toBe('/student/catalog/teachers/8');expect(studentLessonPath(4,2,3)).toBe('/student/courses/4/modules/2/lessons/3')});
 it('opens exact lesson in appropriate management workspace',()=>{expect(editorLessonPath('ADMIN',4,2,3)).toBe('/admin/courses/4/editor?moduleId=2&lessonId=3');expect(editorLessonPath('TEACHER',4,2,3)).toBe('/teacher/courses/4/editor?moduleId=2&lessonId=3')});
});
