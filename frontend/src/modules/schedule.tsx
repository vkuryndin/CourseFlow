// Public module composition.
import {AssignmentPanel} from './learning';
import {SchedulePage} from './schedule/schedule-page';
export function Schedule({student=false}:{student?:boolean}){return <SchedulePage student={student} AssignmentPanel={AssignmentPanel}/>;}
