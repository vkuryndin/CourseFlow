import { useState } from 'react';
import { Divider, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from '@mui/material';
import { Archive, CalendarDays, EllipsisVertical, Pencil, Table, Trash2, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Data } from '../../shared/api';

export function CourseActions({ course, busy, archive, remove }: {course:Data; busy:boolean; archive:()=>void; remove:()=>Promise<unknown>}) {
    const [anchor, setAnchor] = useState<HTMLElement|null>(null);
    const close = () => setAnchor(null);
    const confirmRemove = async () => {
        const row = anchor?.closest('tr');
        close();
        row?.classList.add('delete-target');
        try { await remove(); } finally { row?.classList.remove('delete-target'); }
    };
    return <><Tooltip title="Действия"><IconButton size="small" aria-label={`Действия: ${course.title}`} aria-haspopup="menu" aria-expanded={!!anchor} onClick={e=>setAnchor(e.currentTarget)}><EllipsisVertical size={19}/></IconButton></Tooltip>
        <Menu anchorEl={anchor} open={!!anchor} onClose={close}>
            {([{title:'Конструктор',path:'editor',Icon:Pencil},{title:'Журнал',path:'gradebook',Icon:Table},{title:'Студенты',path:'students',Icon:Users},{title:'Расписание',path:'schedule',Icon:CalendarDays}]).map(({title,path,Icon})=><MenuItem key={path} component={Link} to={`/admin/courses/${course.id}/${path}`} onClick={close}><ListItemIcon><Icon size={17}/></ListItemIcon><ListItemText>{title}</ListItemText></MenuItem>)}
            <Divider/>
            <MenuItem disabled={busy || course.status==='ARCHIVED'} onClick={()=>{close();archive();}}><ListItemIcon><Archive size={17}/></ListItemIcon><ListItemText>Архивировать</ListItemText></MenuItem>
            <MenuItem disabled={busy} onClick={()=>void confirmRemove()} sx={{color:'error.main'}}><ListItemIcon sx={{color:'error.main'}}><Trash2 size={17}/></ListItemIcon><ListItemText>Удалить</ListItemText></MenuItem>
        </Menu></>;
}
