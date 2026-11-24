package org.courseflow.identity.api;

import java.time.Instant;
import java.util.Set;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public identity operations for other modules. Persistence remains inside identity.
 * Entity references intentionally participate in the caller's ORM transaction. */
@Service
public class UserDirectory {
    private final UserRepository users;

    public UserDirectory(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return users.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public User requireStudentReference(Long studentId) {
        return users.findById(studentId).orElseThrow(() ->
                new ResourceNotFoundException("Student not found: " + studentId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public User lockTeacherReference(Long teacherId) {
        return users.findByIdForUpdate(teacherId).orElseThrow(() ->
                new ResourceNotFoundException("Teacher not found: " + teacherId));
    }

    @Transactional(readOnly = true)
    public boolean emailInUse(String normalizedEmail) {
        return users.existsByEmail(normalizedEmail);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean systemAdminExists() { return users.existsBySystemAdminTrue(); }

    @Transactional(propagation = Propagation.MANDATORY)
    public User createUser(String displayName, String normalizedEmail, Set<UserRole> roles, Instant now) {
        return users.saveAndFlush(new User(displayName, normalizedEmail, roles, now));
    }

    /** Called only by bootstrap under the database-wide initialization lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User createSystemAdmin(String normalizedEmail, Instant now) {
        User admin = new User("System Administrator", normalizedEmail, Set.of(UserRole.ADMIN), now);
        admin.markAsSystemAdmin();
        return users.saveAndFlush(admin);
    }
}
