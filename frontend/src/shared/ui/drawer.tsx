import React from 'react';
import { Drawer, IconButton, Typography } from '@mui/material';
import { X } from 'lucide-react';
export function Panel({ open, title, onClose, children, wide = false }: {
    open: boolean;
    title: string;
    onClose: () => void;
    children: React.ReactNode;
    wide?: boolean;
}) { return <Drawer anchor="right" open={open} onClose={onClose} className={`entity-drawer ${wide ? 'entity-drawer-wide' : ''}`} PaperProps={{ className: 'entity-drawer-paper' }}><div className="entity-drawer-header"><Typography variant="h5" sx={{ overflowWrap: 'anywhere' }}>{title}</Typography><IconButton onClick={onClose} aria-label="Закрыть"><X /></IconButton></div><div className="entity-drawer-body">{children}</div></Drawer>; }
