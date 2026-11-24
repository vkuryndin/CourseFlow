import {version as reactVersion, type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Alert,Box,Button,Chip,LinearProgress,Paper,Stack,Typography} from '@mui/material';
import {Database,Server,Cpu,Mail,ShieldCheck,Layers,RefreshCw} from 'lucide-react';
import {get,errorText} from '../shared/api';
import {formatDate} from '../shared/datetime';
import packageInfo from '../../package.json';

type Maybe<T> = T | null;
type SystemInfo = {
  snapshot: {
    collectedAt:string; cacheSeconds:number;
    application:{name:string;version:Maybe<string>;springBootVersion:Maybe<string>;hibernateVersion:Maybe<string>;activeProfiles:string[];defaultProfiles:string[];schemaManagement:Maybe<string>;buildTime:Maybe<string>;gitCommit:Maybe<string>};
    runtime:{hostName:Maybe<string>;javaVersion:Maybe<string>;javaVendor:Maybe<string>;jvmName:Maybe<string>;osName:string;osVersion:string;architecture:string;availableProcessors:number;totalMemoryBytes:Maybe<number>;freeMemoryBytes:Maybe<number>;jvmStartedAt:string;uptimeSeconds:number;timeZone:string};
    database:{status:'CONNECTED'|'PARTIAL'|'UNAVAILABLE';product:Maybe<string>;version:Maybe<string>;driver:Maybe<string>;driverVersion:Maybe<string>;serverAddress:Maybe<string>;serverPort:Maybe<number>;databaseName:Maybe<string>;databaseUser:Maybe<string>;schema:Maybe<string>;sizeBytes:Maybe<number>;tableCount:Maybe<number>;queryTimeMillis:Maybe<number>};
    pool:{name:Maybe<string>;minimumIdle:Maybe<number>;maximumSize:Maybe<number>;connectionTimeoutMillis:Maybe<number>;active:Maybe<number>;idle:Maybe<number>;pending:Maybe<number>};
    mail:{host:Maybe<string>;port:Maybe<number>;startTlsEnabled:Maybe<boolean>;startTlsRequired:Maybe<boolean>;sslEnabled:Maybe<boolean>;status:'NOT_CHECKED'};
  };
  serverEndpoint:{localAddress:string;localPort:number};
  accessTokenLifetimeSeconds:Maybe<number>;
};
const show=(v:unknown):ReactNode=>v==null||v===''?'—':typeof v==='boolean'?(v?'Да':'Нет'):typeof v==='number'?v.toLocaleString('ru-RU'):String(v);
const bytes=(n:number|null|undefined)=>n==null||!Number.isFinite(n)||n<0?'—':n>=1073741824?`${(n/1073741824).toLocaleString('ru-RU',{maximumFractionDigits:2})} ГиБ`:`${(n/1048576).toLocaleString('ru-RU',{maximumFractionDigits:2})} МиБ`;
const time=(n:number|null|undefined)=>n==null||n<0?'—':`${Math.floor(n/86400)?Math.floor(n/86400)+' д. ':''}${Math.floor(n/3600)%24} ч ${Math.floor(n/60)%60} мин`;
const millis=(n:number|null|undefined)=>n==null?'—':`${n.toLocaleString('ru-RU')} мс`;
const dbStates={CONNECTED:'Подключена',PARTIAL:'Часть сведений недоступна',UNAVAILABLE:'Недоступна'};
function Section({title,icon,rows,extra}:{title:string;icon:ReactNode;rows:[string,unknown][];extra?:ReactNode}){
  return <Paper className="section" sx={{minWidth:0}}><Stack direction="row" alignItems="center" gap={1.5} mb={2}><Box sx={{p:1,borderRadius:2,bgcolor:'#EEF2FF',color:'primary.main',display:'flex'}}>{icon}</Box><Typography variant="h6">{title}</Typography></Stack><Box component="dl" sx={{m:0,display:'grid',gridTemplateColumns:{xs:'1fr',sm:'minmax(120px, 1fr) minmax(0, 1fr)'},columnGap:2,rowGap:1.2}}>{rows.map(([name,v])=><Box key={name} sx={{display:'contents'}}><Typography component="dt" variant="body2" color="text.secondary">{name}</Typography><Typography component="dd" variant="body2" sx={{m:0,fontWeight:500,overflowWrap:'anywhere'}}>{show(v)}</Typography></Box>)}</Box>{extra}</Paper>
}
export function SystemInfoPanel({paused,diskFree,diskTotal}:{paused:boolean;diskFree?:number;diskTotal?:number}){
  const info=useQuery({queryKey:['api','/api/system/info'],queryFn:()=>get<SystemInfo>('/api/system/info'),retry:false,staleTime:60000,gcTime:0,refetchOnWindowFocus:false,refetchInterval:paused?false:60000,refetchIntervalInBackground:false});
  const data=info.data;
  if(!data)return <Stack gap={2}>{info.isFetching?<LinearProgress/>:<Alert severity="warning">{info.isError?errorText(info.error):'Диагностические данные пока недоступны'}</Alert>}<Button startIcon={<RefreshCw size={16}/>} disabled={info.isFetching} onClick={()=>void info.refetch()} sx={{alignSelf:'flex-start'}}>Повторить запрос сведений</Button></Stack>;
  const {application:a,runtime:r,database:d,pool:p,mail:m}=data.snapshot;
  const total=r.totalMemoryBytes,free=r.freeMemoryBytes;
  const used=total!=null&&free!=null&&total>0&&free>=0&&free<=total?total-free:null;
  const percent=used!=null&&total!=null&&total>0?used/total*100:null;
  const diskUsed=diskTotal!=null&&diskFree!=null&&diskTotal>0&&diskFree>=0&&diskFree<=diskTotal?diskTotal-diskFree:null;
  const diskPercent=diskUsed!=null&&diskTotal!=null&&diskTotal>0?diskUsed/diskTotal*100:null;
  const poolPercent=p.active!=null&&p.maximumSize!=null&&p.maximumSize>0&&p.active>=0?Math.min(100,p.active/p.maximumSize*100):null;
  return <Stack gap={2}>
    <Stack direction="row" alignItems="center" flexWrap="wrap" gap={1.5}><Chip size="small" color={d.status==='CONNECTED'?'success':d.status==='PARTIAL'?'warning':'error'} label={`PostgreSQL: ${dbStates[d.status]??'Неизвестно'}`}/><Typography variant="body2" color="text.secondary">Снимок: {formatDate(data.snapshot.collectedAt)} · кэш {data.snapshot.cacheSeconds} сек.</Typography><Button startIcon={<RefreshCw size={16}/>} disabled={info.isFetching} onClick={()=>void info.refetch()}>Обновить сведения</Button></Stack>
    {info.isFetching&&<LinearProgress/>}{info.isError&&<Alert severity="warning">Не удалось обновить сведения. Показан предыдущий снимок. {errorText(info.error)}</Alert>}
    <Alert severity="info">Локальные адреса относятся к сетевому соединению backend и PostgreSQL. В Docker это могут быть адреса контейнеров, а не публичные IP серверов. «—» означает, что параметр недоступен.</Alert>
    <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',lg:'repeat(2,minmax(0,1fr))'},gap:2}}>
      <Section title="PostgreSQL" icon={<Database size={23}/>} rows={[["Состояние",dbStates[d.status]],["СУБД",d.product],["Версия PostgreSQL",d.version],["IP сервера PostgreSQL",d.serverAddress],["Порт PostgreSQL",d.serverPort==null?null:String(d.serverPort)],["Имя базы данных",d.databaseName],["Пользователь БД",d.databaseUser],["Текущая схема",d.schema],["Размер базы данных",bytes(d.sizeBytes)],["Видимые таблицы в схеме",d.tableCount],["Время диагностического запроса",millis(d.queryTimeMillis)],["JDBC-драйвер",d.driver],["Версия JDBC-драйвера",d.driverVersion]]}/>
      <Section title="CourseFlow Backend" icon={<Server size={23}/>} rows={[["Приложение",a.name],["Версия сборки",a.version],["Spring Boot",a.springBootVersion],["Hibernate ORM",a.hibernateVersion],["Управление схемой (ddl-auto)",a.schemaManagement],["Активные профили",a.activeProfiles?.length?a.activeProfiles.join(', '):'Не заданы'],["Профили по умолчанию",a.defaultProfiles?.join(', ')],["Имя хоста",r.hostName],["Локальный IP backend",data.serverEndpoint.localAddress],["Локальный порт backend",String(data.serverEndpoint.localPort)],["Дата сборки",a.buildTime?formatDate(a.buildTime):null],["Git commit",a.gitCommit]]}/>
      <Section title="Серверная среда и JVM" icon={<Cpu size={23}/>} rows={[["Java",r.javaVersion],["Поставщик Java",r.javaVendor],["JVM",r.jvmName],["Операционная система",r.osName],["Версия ОС",r.osVersion],["Архитектура",r.architecture],["Доступные процессоры",r.availableProcessors],["Память всего",bytes(total)],["Память свободная",bytes(free)],["Старт JVM",formatDate(r.jvmStartedAt)],["Время работы JVM",time(r.uptimeSeconds)],["Часовой пояс backend",r.timeZone],["Диск всего (Actuator)",bytes(diskTotal)],["Диск свободно (Actuator)",bytes(diskFree)]]} extra={<>{percent!=null&&<Box mt={2}><Typography variant="caption">Память: использовано {bytes(used)} ({percent.toLocaleString('ru-RU',{maximumFractionDigits:1})}%)</Typography><LinearProgress variant="determinate" value={percent} sx={{height:8,borderRadius:4,mt:1}}/><Typography variant="caption" color="text.secondary">Память по данным JVM/ОС; в контейнере зависит от лимитов.</Typography></Box>}{diskPercent!=null&&<Box mt={2}><Typography variant="caption">Диск: использовано {bytes(diskUsed)} ({diskPercent.toLocaleString('ru-RU',{maximumFractionDigits:1})}%)</Typography><LinearProgress variant="determinate" value={diskPercent} sx={{height:8,borderRadius:4,mt:1}}/><Typography variant="caption" color="text.secondary">Данные файловой системы, которую наблюдает Actuator.</Typography></Box>}</>}/>
      <Section title="HikariCP" icon={<Layers size={23}/>} rows={[["Имя пула",p.name],["Минимум свободных",p.minimumIdle],["Максимальный размер",p.maximumSize],["Таймаут получения соединения",millis(p.connectionTimeoutMillis)],["Активные соединения",p.active],["Свободные соединения",p.idle],["Ожидающие потоки",p.pending]]} extra={poolPercent!=null?<Box mt={2}><Typography variant="caption">Загрузка пула на момент снимка: {poolPercent.toLocaleString('ru-RU',{maximumFractionDigits:1})}%</Typography><LinearProgress variant="determinate" value={poolPercent} sx={{height:8,borderRadius:4,mt:1}}/></Box>:undefined}/>
      <Section title="Почтовый сервис (SMTP)" icon={<Mail size={23}/>} rows={[["Сервер",m.host],["Порт",m.port==null?null:String(m.port)],["STARTTLS включён",m.startTlsEnabled],["STARTTLS обязателен",m.startTlsRequired],["SSL включён",m.sslEnabled],["Проверка соединения",'Не выполнялась']]} extra={<Typography variant="caption" color="text.secondary" sx={{display:'block',mt:2}}>Результаты фактических отправок доступны во вкладке «Метрики CourseFlow».</Typography>}/>
      <Section title="Авторизация и frontend" icon={<ShieldCheck size={23}/>} rows={[["Полный срок жизни текущего JWT",time(data.accessTokenLifetimeSeconds)],["React",reactVersion],["Версия npm-пакета",packageInfo.version],["Версия патча интерфейса",'1.2.3']]} extra={<Typography variant="caption" color="text.secondary" sx={{display:'block',mt:2}}>Пароли, ключи JWT и полные строки подключения не передаются.</Typography>}/>
    </Box>
  </Stack>;
}
