import { useState, type ReactNode } from 'react';
import { IconButton, Menu, MenuItem, ListItemIcon, ListItemText, Divider, Tooltip } from '@mui/material';
import { Pencil, EllipsisVertical, Shield, KeyRound, UserX, UserCheck, Trash2, BookOpen, BookPlus, ArrowLeftRight } from 'lucide-react';
import type { Data } from '../../shared/api';
type Props = { row: Data; busy: boolean; edit: () => void; roles: () => void; toggle: () => void; reset: () => void; remove: () => Promise<unknown>; courses?: () => void; createCourse?: () => void; assign?: () => void };
export function UserActions({ row, busy, edit, roles, toggle, reset, remove, courses, createCourse, assign }: Props) {
    const [anchor, setAnchor] = useState<HTMLElement | null>(null);
    const close = () => setAnchor(null);
    const item = (title: string, icon: ReactNode, action: () => void, disabled = false, danger = false) => <MenuItem key={title} disabled={disabled} onClick={() => { close(); action(); }} sx={danger ? { color: 'error.main' } : undefined}><ListItemIcon sx={danger ? { color: 'error.main' } : undefined}>{icon}</ListItemIcon><ListItemText>{title}</ListItemText></MenuItem>;
    const removeHighlighted = async () => {
        const target = anchor?.closest('tr');
        target?.classList.add('delete-target');
        try { await remove(); } finally { target?.classList.remove('delete-target'); }
    };
    if (row.status === 'DELETED') return null;
    return <><Tooltip title="Редактировать"><IconButton size="small" color="primary" aria-label={`Редактировать ${row.displayName}`} onClick={edit}><Pencil size={17}/></IconButton></Tooltip><Tooltip title="Другие действия"><IconButton size="small" aria-label={`Действия: ${row.displayName}`} aria-haspopup="menu" aria-expanded={!!anchor} onClick={e => setAnchor(e.currentTarget)}><EllipsisVertical size={19}/></IconButton></Tooltip><Menu anchorEl={anchor} open={!!anchor} onClose={close}>
        {courses && item('Курсы преподавателя', <BookOpen size={17}/>, courses)}
        {createCourse && item('Создать курс', <BookPlus size={17}/>, createCourse, row.status !== 'ACTIVE' || busy)}
        {assign && item('Назначить на курс', <ArrowLeftRight size={17}/>, assign, row.status !== 'ACTIVE' || busy)}
        {courses && <Divider/>}
        {item('Изменить роли', <Shield size={17}/>, roles, !!row.systemAdmin || busy)}
        {item('Сбросить пароль', <KeyRound size={17}/>, reset, busy)}
        {item(row.status === 'ACTIVE' ? 'Деактивировать' : 'Активировать', row.status === 'ACTIVE' ? <UserX size={17}/> : <UserCheck size={17}/>, toggle, !!row.systemAdmin || busy)}
        <Divider/>{item('Удалить', <Trash2 size={17}/>, () => void removeHighlighted(), !!row.systemAdmin || busy, true)}
    </Menu></>;
}
