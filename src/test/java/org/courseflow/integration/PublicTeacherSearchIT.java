package org.courseflow.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.Filter;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.integration.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

class PublicTeacherSearchIT extends AbstractIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter security;
    @Autowired private UserRepository users;

    @Test
    @Transactional
    void publicDirectorySearchesOnlyActiveTeacherNamesWithStablePagination() throws Exception {
        String prefix = "Dir" + UUID.randomUUID().toString().replace("-", "");
        User first = add(prefix + " A", Set.of(UserRole.TEACHER, UserRole.STUDENT));
        User second = add(prefix + " A", Set.of(UserRole.TEACHER));
        add(prefix + " B", Set.of(UserRole.TEACHER));
        add(prefix + " 100%", Set.of(UserRole.TEACHER));
        add(prefix + " 100X", Set.of(UserRole.TEACHER));
        add(prefix + " _literal", Set.of(UserRole.TEACHER));
        add(prefix + " Xliteral", Set.of(UserRole.TEACHER));
        User inactive = add(prefix + " Inactive", Set.of(UserRole.TEACHER));
        inactive.deactivate();
        users.saveAndFlush(inactive);
        User deleted = add(prefix + " Deleted", Set.of(UserRole.TEACHER));
        deleted.deactivate();
        deleted.markDeleted(Instant.now());
        users.saveAndFlush(deleted);
        add(prefix + " Student", Set.of(UserRole.STUDENT));
        users.saveAndFlush(new User("Unrelated", prefix + "@example.invalid", Set.of(UserRole.TEACHER), Instant.now()));
        var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        mvc.perform(get("/api/teachers").param("q", "  " + prefix.toUpperCase(java.util.Locale.ROOT) + "  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.content.length()").value(7))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].roles").doesNotExist());
        mvc.perform(get("/api/teachers").param("q", prefix + " A").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first.getId()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/api/teachers").param("q", prefix + " A").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.last").value(true));
        for (String literal : new String[]{"100%", "_literal"}) {
            mvc.perform(get("/api/teachers").param("q", prefix + " " + literal))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc.perform(get("/api/teachers").param("q", prefix + " absent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/teachers").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/teachers").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/teachers").param("q", "a".repeat(121))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/teachers/{id}", first.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/teachers/{id}", inactive.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/info")).andExpect(status().isUnauthorized());
    }

    private User add(String name, Set<UserRole> roles) {
        return users.saveAndFlush(new User(name, UUID.randomUUID() + "@example.invalid", roles, Instant.now()));
    }
}
