package com.vnhackers.dotconn.chat;

import com.vnhackers.dotconn.projects.*;
import com.vnhackers.dotconn.user.UserRepository;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import java.util.LinkedHashSet;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectChatService {
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ProjectChatService.class);
  private final ProjectAccessService access;
  private final ProjectChatRepository messages;
  private final UserRepository users;
  private final DeveloperProfileRepository profiles;
  private final InvitationRepository invitations;
  private final SimpMessagingTemplate messaging;
  public ProjectChatService(ProjectAccessService access, ProjectChatRepository messages, UserRepository users,
      DeveloperProfileRepository profiles, InvitationRepository invitations, SimpMessagingTemplate messaging) {
    this.access=access; this.messages=messages; this.users=users; this.profiles=profiles; this.invitations=invitations; this.messaging=messaging;
  }
  @Transactional(readOnly=true)
  public Page<ProjectChatDto> history(Long projectId,Long userId,Pageable pageable) {
    access.requireMember(projectId,userId);
    return messages.findByProjectId(projectId,PageRequest.of(pageable.getPageNumber(),Math.min(100,pageable.getPageSize()),Sort.by(Sort.Direction.DESC,"id"))).map(this::dto);
  }
  @Transactional
  public ProjectChatDto send(Long projectId,Long userId,String rawContent) {
    var project=access.requireMember(projectId,userId);
    String content=rawContent==null?"":rawContent.strip();
    if(content.isEmpty()||content.length()>2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Scrie un mesaj de 1–2000 de caractere.");
    var sender=users.findById(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sesiune invalidă."));
    var message=messages.save(new ProjectChatMessage(project,sender,content));
    var dto=dto(message);
    var recipients=new LinkedHashSet<Long>(); recipients.add(project.getOwner().getId());
    invitations.findByProjectIdAndStatus(projectId,InvitationStatus.ACCEPTED).forEach(inv->recipients.add(inv.getInvitee().getId()));
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { recipients.forEach(id->{ try { messaging.convertAndSendToUser(id.toString(),"/queue/messages",dto); } catch (RuntimeException e) { log.warn("Project chat message {} saved but realtime delivery failed for recipient {}", dto.id(), id); } }); }
    });
    return dto;
  }
  private ProjectChatDto dto(ProjectChatMessage message) {
    Long senderId=message.getSender().getId();
    String name=profiles.findById(senderId).map(p->p.getDisplayName()).filter(n->n!=null&&!n.isBlank()).orElse("Membru #"+senderId);
    return new ProjectChatDto(message.getId(),message.getProject().getId(),senderId,name,message.getContent(),message.getCreatedAt());
  }
}
