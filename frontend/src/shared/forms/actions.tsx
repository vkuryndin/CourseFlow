import { useState } from 'react';
import { errorText, request, useRefresh } from '../api';
import { useDeleteConfirm } from '../confirm';
export function useAction() { const [error, E] = useState(''), [busy, B] = useState(false); const refresh = useRefresh(), ask = useDeleteConfirm(); async function run(method: string, path: string, data?: unknown, confirm?: string) { if (busy)
    return false; B(true); E(''); try {
    if (confirm && !(method === 'DELETE' ? await ask(confirm) : window.confirm(confirm)))
        return false;
    await request(method, path, data);
    await refresh();
    return true;
}
catch (e) {
    E(errorText(e));
    return false;
}
finally {
    B(false);
} } return { run, error, busy, clear: () => E('') }; }
