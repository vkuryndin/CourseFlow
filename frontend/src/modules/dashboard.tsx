// Public module composition.
import {GradePanel,AttemptPanel} from './teaching';
import {DashboardPage} from './dashboard/dashboard-page';
export function Dashboard({role}:{role:string}){return <DashboardPage role={role} GradePanel={GradePanel} AttemptPanel={AttemptPanel}/>;}
