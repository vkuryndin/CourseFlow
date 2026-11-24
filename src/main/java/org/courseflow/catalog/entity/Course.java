package org.courseflow.catalog.entity;

import jakarta.persistence.*;
import org.courseflow.content.entity.Module;
import org.courseflow.identity.entity.User;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.review.entity.CourseReview;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    public long getVersion() {
        return version;
    }

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    // Course duration in calendar days
    @Column(nullable = false)
    private Integer duration;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status = CourseStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "course_tag",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_course_tag_course_tag",
                    columnNames = {"course_id", "tag_id"}
            )
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(
            mappedBy = "course",
            fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST
    )
    @OrderBy("position ASC, id ASC")
    private List<Module> modules = new ArrayList<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    private List<Enrollment> enrollments = new ArrayList<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC, id DESC")
    private List<CourseReview> reviews = new ArrayList<>();

    protected Course() {
    }

    public Course(
            String title,
            String description,
            Category category,
            User teacher,
            Integer duration,
            LocalDate startDate
    ) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.teacher = teacher;
        this.duration = duration;
        this.startDate = startDate;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public User getTeacher() {
        return teacher;
    }

    public Integer getDuration() {
        return duration;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public Set<Tag> getTags() {
        return Set.copyOf(tags);
    }

    public List<Module> getModules() {
        return List.copyOf(modules);
    }

    public List<Enrollment> getEnrollments() {
        return List.copyOf(enrollments);
    }

    public List<CourseReview> getReviews() {
        return List.copyOf(reviews);
    }

    public void addTag(Tag tag) {
        this.tags.add(tag);
    }

    public void removeTag(Tag tag) {
        this.tags.removeIf(
                existing -> existing.getId().equals(tag.getId())
        );
    }

    public Module addModule(
            String title,
            String description,
            Integer position
    ) {
        Module module = new Module(
                this,
                title,
                description,
                position
        );

        modules.add(module);

        return module;
    }

    public void archive() {
        this.status = CourseStatus.ARCHIVED;
    }

    public void updateDetails(
            String newTitle,
            String newDescription,
            Category newCategory,
            User newTeacher,
            int newDuration,
            LocalDate newStartDate
    ) {
        this.title = newTitle;
        this.description = newDescription;
        this.category = newCategory;
        this.teacher = newTeacher;
        this.duration = newDuration;
        this.startDate = newStartDate;
    }

    public void reassignTeacher(User newTeacher) {
        this.teacher = newTeacher;
    }

}
