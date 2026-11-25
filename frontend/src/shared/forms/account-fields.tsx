import { Box, Checkbox, FormControlLabel, Stack, Typography } from '@mui/material';
import { GraduationCap, Presentation, ShieldCheck } from 'lucide-react';
import { label } from '../api';
const roles = [
    {key:'STUDENT', icon:GraduationCap, description:'Обучение на курсах, выполнение заданий и тестов'},
    {key:'TEACHER', icon:Presentation, description:'Создание курсов, обучение и оценивание студентов'},
    {key:'ADMIN', icon:ShieldCheck, description:'Управление пользователями и платформой'}
] as const;
export function AccountRoles({value, onChange, requiredRole}: {value: string[]; onChange:(roles:string[])=>void; requiredRole?:string}) {
    return <Stack gap={1}><Typography variant="subtitle1" fontWeight={600}>Роли пользователя</Typography><Box className="account-role-options">{roles.map(({key, icon:Icon, description}) => <FormControlLabel key={key} className="account-role-option" control={<Checkbox checked={value.includes(key)} disabled={requiredRole === key} onChange={(_, checked) => onChange(checked ? [...value,key] : value.filter(x => x !== key))}/>} label={<Stack direction="row" alignItems="center" gap={1.5}><Icon size={21} color="#4f46e5"/><Box><Typography variant="body2" fontWeight={600}>{label(key)}{requiredRole === key ? ' (обязательная)' : ''}</Typography><Typography variant="caption" color="text.secondary">{description}</Typography></Box></Stack>}/>)}</Box><Typography variant="caption" color="text.secondary">Пользователь может иметь несколько ролей одновременно.</Typography></Stack>;
}
