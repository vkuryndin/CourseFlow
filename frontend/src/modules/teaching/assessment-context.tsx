import { useQuery } from '@tanstack/react-query';
import { Data, get } from '../../shared/api';
import { structure } from '../../shared/structure';
export function useAssessmentContext(courseId: any, assignmentId: any, quizId: any, enabled: boolean) { return useQuery<Data>({ queryKey: ['assessment-context', courseId, assignmentId, quizId], queryFn: async () => { const [c, tree] = await Promise.all([get(`/api/courses/${courseId}`), structure(courseId)]); for (const m of tree) {
        if (quizId && String(m.quiz?.id) === String(quizId))
            return { ...m.quiz, courseTitle: c.title, moduleTitle: m.title };
        for (const l of m.lessons) {
            const x = l.assignments.find(a => String(a.id) === String(assignmentId));
            if (x)
                return { ...x, courseTitle: c.title, moduleTitle: m.title, lessonTitle: l.title };
        }
    } return { courseTitle: c.title }; }, enabled: enabled && !!courseId, retry: false }); }
