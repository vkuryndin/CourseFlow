import { DndContext, closestCenter, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { GripVertical } from 'lucide-react';
import { Data } from '../../shared/api';
function SortNode({ id, children }: {
    id: string;
    children: React.ReactNode;
}) { const s = useSortable({ id }); return <div ref={s.setNodeRef} style={{ transform: CSS.Transform.toString(s.transform), transition: s.transition }} className="sort-node"><button className="grip" aria-label="Переместить" {...s.attributes} {...s.listeners}><GripVertical size={16}/></button><div style={{ flex: 1, minWidth: 0 }}>{children}</div></div>; }
export function SortGroup({ items, move, children }: {
    items: Data[];
    move: (r: Data, p: number) => void;
    children: (r: Data) => React.ReactNode;
}) { const ids = items.map(x => String(x.id)); function end(e: DragEndEvent) { const old = ids.indexOf(String(e.active.id)), next = ids.indexOf(String(e.over?.id)); if (next >= 0 && old >= 0 && old !== next)
    move(items[old], items[next].position); } return <DndContext collisionDetection={closestCenter} onDragEnd={end}><SortableContext items={ids} strategy={verticalListSortingStrategy}>{items.map(x => <SortNode id={String(x.id)} key={x.id}>{children(x)}</SortNode>)}</SortableContext></DndContext>; }
