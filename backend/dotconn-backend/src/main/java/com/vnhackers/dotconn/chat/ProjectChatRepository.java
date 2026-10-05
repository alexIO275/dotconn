package com.vnhackers.dotconn.chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectChatRepository extends JpaRepository<ProjectChatMessage,Long> {
  Page<ProjectChatMessage> findByProjectId(Long projectId, Pageable pageable);
}
