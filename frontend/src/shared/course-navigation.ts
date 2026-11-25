/** Catalog details have a separate namespace from the student's learning routes. */
export const catalogRoot=(inStudentWorkspace:boolean)=>inStudentWorkspace?'/student/catalog':'/catalog';
export const courseDetailsPath=(id:string|number,inStudentWorkspace=false)=>`${inStudentWorkspace?'/student/catalog':''}/courses/${id}`;
export const teacherDetailsPath=(id:string|number,inStudentWorkspace=false)=>`${inStudentWorkspace?'/student/catalog':''}/teachers/${id}`;
export const studentLessonPath=(courseId:string|number,moduleId:string|number,lessonId:string|number)=>`/student/courses/${courseId}/modules/${moduleId}/lessons/${lessonId}`;
export const editorLessonPath=(role:string,courseId:string|number,moduleId:string|number,lessonId:string|number)=>`/${role==='ADMIN'?'admin':'teacher'}/courses/${courseId}/editor?moduleId=${encodeURIComponent(moduleId)}&lessonId=${encodeURIComponent(lessonId)}`;
