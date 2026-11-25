import { useAll } from '../../shared/api';
export function useMyCourseOptions() { const courses = useAll('/api/users/me/enrollments'); return courses.data?.map(c => ({ value: String(c.courseId), label: String(c.courseTitle) })) || []; }
