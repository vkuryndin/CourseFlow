import { useState } from 'react';
import { Alert, Button } from '@mui/material';
import { Data } from '../../shared/api';
import { EditPanel, FormSpec, useAction } from '../../shared/forms';
import { Heading, Paged } from '../../shared/ui';
export function Dictionary({ kind }: {
    kind: 'categories' | 'tags';
}) { const [edit, E] = useState<FormSpec | null>(null), act = useAction(), path = '/api/' + kind, schema = kind === 'categories' ? 'CategoryCreateRequest' : 'TagRequest'; const open = (r?: Data) => E({ title: r ? 'Редактирование' : 'Создание', schema, path: r ? `${path}/${r.id}` : path, method: r ? 'PUT' : 'POST', initial: r }); return <><Heading title={kind === 'categories' ? 'Категории' : 'Теги'}/>{act.error && <Alert severity="error">{act.error}</Alert>}<Paged path={path} cols={kind === 'categories' ? ['id', 'name', 'description'] : ['id', 'name']} filters={[{ key: 'q', title: 'Поиск' }]} sorts={['id', 'name']} create={() => open()} actions={r => <><Button onClick={() => open(r)}>Редактировать</Button><Button color="error" disabled={act.busy} onClick={() => void act.run('DELETE', `${path}/${r.id}`, undefined, `Удалить ${kind === 'categories' ? 'категорию' : 'тег'} «${r.name}»?`)}>Удалить</Button></>}/><EditPanel spec={edit} onClose={() => E(null)}/></>; }
