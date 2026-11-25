// Public module composition.
import {SystemInfoPanel} from './system-info';
import {MonitoringPage} from './monitoring/monitoring-page';
export {custom,delta} from './monitoring/metrics';
export function Monitoring(){return <MonitoringPage SystemInfoPanel={SystemInfoPanel}/>;}
