package com.vnhackers.dotconn.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vnhackers.dotconn.chat.ChatMessageRepository;
import com.vnhackers.dotconn.chat.ProjectChatRepository;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.projects.InvitationRepository;
import com.vnhackers.dotconn.projects.ProjectRepository;
import com.vnhackers.dotconn.projects.ProjectTaskRepository;
import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo-seeder-safety-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class DemoDataSeederSafetyTests {
  @Autowired ApplicationContext context;
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
  void demoIsDisabledWithoutExplicitOptInAndLocalProfile() {
    assertFalse(context.containsBean("demoDataSeeder"));
    assertFalse(context.containsBean("demoDataController"));
    assertEquals(0, users.count());
    assertEquals(0, projects.count());
  }

  @Test
  void emailCollisionStopsBeforeCreatingOrChangingAnyRecords() {
    User existing = users.saveAndFlush(new User("client" + DemoDataSeeder.DOMAIN, "existing-hash"));
    DemoDataSeeder seeder = new DemoDataSeeder(users, profiles, projects, invitations, tasks,
        messages, projectMessages, seedRuns, passwordEncoder);

    assertThrows(IllegalStateException.class, () -> seeder.run());

    assertEquals(1, users.count());
    assertEquals("existing-hash", users.findById(existing.getId()).orElseThrow().getPasswordHash());
    assertEquals(0, profiles.count());
    assertEquals(0, projects.count());
    assertEquals(0, seedRuns.count());
  }
}
