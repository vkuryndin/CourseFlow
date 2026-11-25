import { Alert, Box, Button, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
export function NotFound({ denied = false }: {
    denied?: boolean;
}) { return <Box sx={{ p: 5, textAlign: 'center' }}><Typography variant="h2">{denied ? '403' : '404'}</Typography><Alert severity={denied ? 'warning' : 'info'} sx={{ my: 2 }}>{denied ? 'Доступ запрещён' : 'Страница не найдена'}</Alert><Button component={Link} to="/catalog">В каталог</Button></Box>; }
