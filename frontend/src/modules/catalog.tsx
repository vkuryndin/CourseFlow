import type { CatalogWorkspace } from '../shared/course-navigation';
// Public module composition.
import {TeacherSearch} from './teacher-search';
import {CatalogPage} from './catalog/catalog-page';
import {TeacherProfile} from './catalog/teacher-profile';
export {CoursePublic} from './catalog/course-details';
export {ManageCourses} from './catalog/manage-courses';
export function Catalog(props:{teacherId?:string;workspace?:CatalogWorkspace}){return <CatalogPage {...props} TeacherSearch={TeacherSearch}/>;}
export function TeacherPublic(props:{workspace?:CatalogWorkspace}){return <TeacherProfile {...props} TeacherSearch={TeacherSearch}/>;}
