
package org.courseflow.learning.entity;

import jakarta.persistence.*;
import org.courseflow.catalog.entity.Course;
import org.courseflow.identity.entity.User;

import java.time.Instant;

@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollments_student_course",
                columnNames = {"student_id", "course_id"}
        )
)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    protected Enrollment() {
    }

    public Enrollment(User student, Course course) {
        this.student = student;
        this.course = course;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public void withdraw(Instant now) {
        if (status == EnrollmentStatus.ACTIVE) {
            this.status = EnrollmentStatus.WITHDRAWN;
            this.endedAt = now;
        }
    }

    public void reactivate() {
        this.status = EnrollmentStatus.ACTIVE;
        this.endedAt = null;
    }
}
