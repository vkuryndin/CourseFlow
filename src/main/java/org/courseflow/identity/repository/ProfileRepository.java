package org.courseflow.identity.repository;

import java.util.Optional;
import org.courseflow.identity.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

  Optional<Profile> findByUser_Id(Long userId);
}
