
package org.courseflow.auth.repository;

import org.courseflow.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUser_Id(Long userId);

    Optional<UserAccount> findByUser_Email(String email);
}
