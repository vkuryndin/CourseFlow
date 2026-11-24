package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.dashboard.dto.UpcomingSession;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class UpcomingSessions {
    private final EntityManager entityManager;

    public UpcomingSessions(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<UpcomingSession> forStudent(Long studentId, Instant now, int limit) {
        return find("""
                AND EXISTS (SELECT e.id FROM Enrollment e WHERE e.course.id = c.id
                    AND e.student.id = :studentId AND e.status = :enrollmentStatus)
                """, Map.of("studentId", studentId, "enrollmentStatus", EnrollmentStatus.ACTIVE), now, limit);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<UpcomingSession> forTeacher(Long teacherId, Instant now, int limit) {
        return find(" AND c.teacher.id = :teacherId ", Map.of("teacherId", teacherId), now, limit);
    }

    private List<UpcomingSession> find(String scope, Map<String, Object> parameters, Instant now, int limit) {
        var query = entityManager.createQuery(UpcomingSessionRow.SELECT + """
                WHERE c.status = :courseStatus AND s.endsAt > :now
                """ + scope + " ORDER BY s.startsAt ASC, s.id ASC", UpcomingSessionRow.class)
                .setParameter("courseStatus", CourseStatus.ACTIVE)
                .setParameter("now", now);
        parameters.forEach((name, value) -> query.setParameter(name, value));
        return query.setMaxResults(limit).getResultList().stream()
                .map(UpcomingSessionRow::toResponse).toList();
    }
}
