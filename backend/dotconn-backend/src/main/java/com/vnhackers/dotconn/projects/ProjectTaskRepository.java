// Repository pentru sarcinile workspace-ului, cu căutări pe proiect (pentru verificări de acces).
package com.vnhackers.dotconn.projects;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
  Page<ProjectTask> findByProjectId(Long projectId, Pageable pageable);

  Page<ProjectTask> findByProjectIdAndStatus(Long projectId, TaskStatus status, Pageable pageable);

  Optional<ProjectTask> findByIdAndProjectId(Long id, Long projectId);
}
