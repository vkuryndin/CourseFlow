import { Alert, Box, Stack, Typography } from '@mui/material';
import { Activity, Database, Server } from 'lucide-react';
import { label, type Data } from './api';

export function SystemStatus({ status, health }: {status?:Data; health?:Data}) {
    const items = [
        {title:'Приложение', name: status?.application || 'CourseFlow', value:status?.status, Icon:Server},
        {title:'База данных', name:'PostgreSQL', value:status?.database, Icon:Database},
        ...(health ? [{title:'Общее состояние', name:'Actuator', value:health.status, Icon:Activity}] : []),
    ];
    return <><Box role="group" aria-label="Состояние системы" sx={{display:'grid', gridTemplateColumns:{xs:'1fr',sm:`repeat(${items.length}, minmax(0,1fr))`},gap:2}}>
        {items.map(({title,name,value,Icon})=>{
            const ok = value==='UP'||value==='CONNECTED';
            const bad = ['DOWN','DISCONNECTED','OUT_OF_SERVICE'].includes(value);
            const color = ok ? 'success.main' : bad ? 'error.main' : 'warning.main';
            return <Box key={title} sx={{border:1,borderColor:'divider',borderRadius:2,p:2,minWidth:0}}>
                <Stack direction="row" alignItems="center" gap={1.5}><Box sx={{display:'flex',p:1,borderRadius:2,bgcolor:'action.hover',color}}><Icon size={21}/></Box><Box sx={{minWidth:0}}><Typography variant="caption" color="text.secondary">{title}</Typography><Typography variant="subtitle1" fontWeight={600}>{name}</Typography></Box></Stack>
                <Typography sx={{color,mt:1.5}} variant="body2">● {value ? label(value) : 'Нет данных'}</Typography>
            </Box>;
        })}
    </Box>{status?.error && <Alert severity="warning" sx={{mt:2}}>Приложение: {status.error}</Alert>}{health?.error && <Alert severity="warning" sx={{mt:2}}>Actuator: {health.error}</Alert>}</>;
}
