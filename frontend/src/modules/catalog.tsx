// Public module composition.
import {TeacherSearch} from './teacher-search';
import {CatalogPage} from './catalog/catalog-page';
import {TeacherProfile} from './catalog/teacher-profile';
export {CoursePublic} from './catalog/course-details';
export {ManageCourses} from './catalog/manage-courses';
export function Catalog(props:{teacherId?:string;studentWorkspace?:boolean}){return <CatalogPage {...props} TeacherSearch={TeacherSearch}/>;}
export function TeacherPublic(props:{studentWorkspace?:boolean}){return <TeacherProfile {...props} TeacherSearch={TeacherSearch}/>;}
