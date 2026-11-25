import { useEffect, useRef, useState } from 'react';
import { Box, IconButton, InputAdornment, TextField, type OutlinedTextFieldProps } from '@mui/material';
import { Eye, EyeOff } from 'lucide-react';

/** Visibility is local to each input and never persisted. The eye is part of the focus group. */
export function PasswordField({ slotProps, ...props }: Omit<OutlinedTextFieldProps, 'variant'>) {
    const [visible, setVisible] = useState(false);
    const root = useRef<HTMLDivElement>(null);
    useEffect(() => {
        const hide = () => setVisible(false);
        const form = root.current?.closest('form');
        form?.addEventListener('submit', hide, true);
        window.addEventListener('blur', hide);
        return () => { form?.removeEventListener('submit', hide, true); window.removeEventListener('blur', hide); };
    }, []);
    return <Box ref={root} onBlurCapture={e => { if (!root.current?.contains(e.relatedTarget as Node | null)) setVisible(false); }}>
        <TextField {...props} variant="outlined" type={visible ? 'text' : 'password'} slotProps={{...slotProps, input: {
            endAdornment: <InputAdornment position="end"><IconButton type="button" edge="end" aria-label={visible ? 'Скрыть пароль' : 'Показать пароль'} aria-pressed={visible} disabled={props.disabled} onClick={() => setVisible(v => !v)}>{visible ? <EyeOff size={19}/> : <Eye size={19}/>}</IconButton></InputAdornment>
        }}}/>
    </Box>;
}
