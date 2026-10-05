package com.vnhackers.dotconn.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vnhackers.dotconn.chat.ChatMessageRepository;
import com.vnhackers.dotconn.chat.ProjectChatRepository;
import com.vnhackers.dotconn.developers.DeveloperProfile;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.developers.DeveloperRole;
import com.vnhackers.dotconn.projects.InvitationRepository;
import com.vnhackers.dotconn.projects.InvitationStatus;
import com.vnhackers.dotconn.projects.ProjectRepository;
import com.vnhackers.dotconn.projects.ProjectTaskRepository;
import com.vnhackers.dotconn.projects.TaskStatus;
import com.vnhackers.dotconn.user.UserRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "app.demo.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:demo-seeder-test;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles({"local", "test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class DemoDataSeederTests {
  @Autowired DemoDataSeeder seeder;
  @Autowired DemoSeedRunRepository seedRuns;
  @Autowired UserRepository users;
  @Autowired DeveloperProfileRepository profiles;
  @Autowired ProjectRepository projects;
  @Autowired InvitationRepository invitations;
  @Autowired ProjectTaskRepository tasks;
  @Autowired ChatMessageRepository messages;
  @Autowired ProjectChatRepository projectMessages;
  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void restartingDoesNotDuplicateRecordsOrResetUserEdits() {
    long[] before = counts();
    DeveloperProfile ana = profiles.findAll().stream()
        .filter(profile -> profile.getUser().getEmail().equals("ana.frontend" + DemoDataSeeder.DOMAIN))
        .findFirst().orElseThrow();
    ana.setDisplayName("Ana — nume editat în demo");
    profiles.saveAndFlush(ana);

    seeder.run();
    seeder.run();

    assertEquals(java.util.Arrays.toString(before), java.util.Arrays.toString(counts()));
    assertEquals("Ana — nume editat în demo", profiles.findById(ana.getId()).orElseThrow().getDisplayName());
    assertTrue(seedRuns.existsById(DemoDataSeeder.VERSION));
    assertTrue(passwordEncoder.matches(DemoDataSeeder.PASSWORD,
        users.findByEmail("client" + DemoDataSeeder.DOMAIN).orElseThrow().getPasswordHash()));
  }

  @Test
  void demoContainsAllRolesAndTheEssentialWorkflowStates() {
    Set<DeveloperRole> roles = profiles.findAll().stream().map(DeveloperProfile::getRole)
        .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    assertEquals(EnumSet.allOf(DeveloperRole.class), roles);
    assertEquals(12, users.count());
    assertEquals(4, projects.count());
    assertEquals(Set.of(InvitationStatus.PENDING, InvitationStatus.ACCEPTED, InvitationStatus.DECLINED),
        invitations.findAll().stream().map(invitation -> invitation.getStatus()).collect(Collectors.toSet()));
    assertEquals(Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE),
        tasks.findAll().stream().map(task -> task.getStatus()).collect(Collectors.toSet()));
    assertEquals(6, messages.count());
    assertEquals(3, projectMessages.count());
    assertTrue(tasks.findAll().stream().anyMatch(task -> task.getAssignee() == null));
  }

  private long[] counts() {
    return new long[] {users.count(), profiles.count(), projects.count(), invitations.count(), tasks.count(), messages.count(), projectMessages.count()};
  }
}
