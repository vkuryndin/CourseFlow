package org.courseflow.content.api;

import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.Module;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.ModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public content lookups for enrollment, assignments, assessments and reports. */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class ContentStructure {
    private final LessonRepository lessons;
    private final ModuleRepository modules;

    public ContentStructure(LessonRepository lessons, ModuleRepository modules) {
        this.lessons = lessons;
        this.modules = modules;
    }

    public boolean hasModules(Long courseId) { return modules.existsByCourse_Id(courseId); }

    public boolean hasLessons(Long moduleId) { return lessons.existsByModule_Id(moduleId); }

    public Lesson requireLesson(Long lessonId) {
        return lessons.findById(lessonId).orElseThrow(() ->
                new ResourceNotFoundException("Lesson not found: " + lessonId));
    }

    public Module requireModule(Long moduleId) {
        return modules.findById(moduleId).orElseThrow(() ->
                new ResourceNotFoundException("Module not found: " + moduleId));
    }

    public void requireModuleInCourse(Long courseId, Long moduleId) {
        if (!modules.existsByIdAndCourse_Id(moduleId, courseId)) {
            throw new ResourceNotFoundException("Module not found in course: " + moduleId);
        }
    }
}
