// Repository pentru proiectele unui utilizator, cu căutare paginată pe owner.
package com.vnhackers.dotconn.projects;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

  Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
}
