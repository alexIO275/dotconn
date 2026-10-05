package com.vnhackers.dotconn.chat;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/projects/{projectId}/chat/messages")
public class ProjectChatController {
  private final ProjectChatService chat;
  public ProjectChatController(ProjectChatService chat) { this.chat=chat; }
  @GetMapping public Page<ProjectChatDto> history(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,@PageableDefault(size=50) Pageable pageable) { return chat.history(projectId,Long.valueOf(jwt.getSubject()),pageable); }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public ProjectChatDto send(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,@Valid @RequestBody SendMessageRequest request) { return chat.send(projectId,Long.valueOf(jwt.getSubject()),request.content()); }
}
