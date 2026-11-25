import { Box } from '@mui/material';
import { BookOpen, HelpCircle } from 'lucide-react';
import { Data, useGet } from '../../shared/api';
import { Module } from '../../shared/structure';
import type { Node } from './editor-node';
export function QuizTree({ quiz, module, node }: {
    quiz: Data;
    module: Module;
    node: (n: Node, i: typeof BookOpen) => React.ReactNode;
}) { const q = useGet<Data[]>(`/api/quizzes/${quiz.id}/questions/manage`); return <Box pl={2}>{q.data?.map((x, i) => <Box key={x.id}>{node({ kind: 'question', data: { ...x, ordinal: i + 1 }, m: module, q: quiz }, HelpCircle)}</Box>)}</Box>; }
