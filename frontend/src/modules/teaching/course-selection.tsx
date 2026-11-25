import { NamedSearch } from '../../shared/server-search';
import { useEffect, useState } from 'react';
import { Box } from '@mui/material';
import { useAuth } from '../../shared/auth';
import { useAll } from '../../shared/api';
export function useSelectedCourse(explicit?: string) {
    const a = useAuth(), key = `courseflow.selectedCourse.${a.session?.userId}.${a.role}`;
    const params = a.role === 'TEACHER' ? { teacherId: a.session?.userId } : {};
    const choices = useAll('/api/courses', params, !explicit);
    const [course, C] = useState(explicit || '');
    useEffect(() => { if (explicit) {
        C(explicit);
        return;
    } if (!choices.isSuccess)
        return; const rows = choices.data || []; let saved = ''; try {
        saved = sessionStorage.getItem(key) || '';
    }
    catch { } ; const available = (id: string) => rows.some(r => String(r.id) === id); C(old => available(old) ? old : available(saved) ? saved : String((rows.find(r => r.status === 'ACTIVE') || rows[0])?.id || '')); }, [explicit, choices.data, choices.isSuccess, key]);
    useEffect(() => { if (course)
        try {
            sessionStorage.setItem(key, course);
        }
        catch { } }, [course, key]);
    return [course, C, choices] as const;
}
export function CourseSelect({ value, onChange, disabled = false }: {
    value: string;
    onChange: (s: string) => void;
    disabled?: boolean;
}) { const a = useAuth(); return <Box sx={{ width: { xs: '100%', sm: 320 }, maxWidth: '100%' }}><NamedSearch fullWidth path="/api/courses" field="title" label="Курс" id={value} onChange={v => onChange(String(v ?? ''))} disabled={disabled} params={a.role === 'TEACHER' ? { teacherId: a.session?.userId } : {}} placeholder="Выберите курс"/></Box>; }
