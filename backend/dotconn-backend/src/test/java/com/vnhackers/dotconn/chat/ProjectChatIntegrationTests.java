package com.vnhackers.dotconn.chat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import com.vnhackers.dotconn.user.*;
import com.vnhackers.dotconn.projects.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties="GROQ_API_KEY=") @ActiveProfiles("test")
class ProjectChatIntegrationTests {
  @Autowired WebApplicationContext context;
  @Autowired UserRepository users;
  @Autowired ProjectRepository projects;
  @Autowired InvitationRepository invitations;
  private MockMvc mvc;
  @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
  private User user(){return users.save(new User("project-chat-"+UUID.randomUUID()+"@example.com","not-used"));}
  private Project project(User owner){var p=new Project(owner);p.setTitle("Chat test");p.setDescription("Proiect de test pentru accesul la conversația echipei.");return projects.save(p);}
  @Test void onlyOwnerAndAcceptedMembersCanReadAndSend() throws Exception {
    var owner=user();var member=user();var pending=user();var outsider=user();var p=project(owner);
    var accepted=new Invitation(p,owner,member);accepted.setStatus(InvitationStatus.ACCEPTED);invitations.save(accepted);
    invitations.save(new Invitation(p,owner,pending));
    String url="/api/projects/"+p.getId()+"/chat/messages";
    mvc.perform(post(url).with(jwt().jwt(j->j.subject(owner.getId().toString()))).contentType("application/json").content("{\"content\":\"Salut echipă!\"}"))
      .andExpect(status().isCreated()).andExpect(jsonPath("$.projectId",is(p.getId().intValue())));
    mvc.perform(get(url).with(jwt().jwt(j->j.subject(member.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.content",hasSize(1)));
    for(var denied:java.util.List.of(pending,outsider)){
      mvc.perform(get(url).with(jwt().jwt(j->j.subject(denied.getId().toString())))).andExpect(status().isNotFound());
      mvc.perform(post(url).with(jwt().jwt(j->j.subject(denied.getId().toString()))).contentType("application/json").content("{\"content\":\"secret\"}")).andExpect(status().isNotFound());
    }
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
  }
  @Test void validatesMessagesAndPaginatesOnlyCurrentProject() throws Exception {
    var owner=user();var p=project(owner);var other=project(owner);String url="/api/projects/"+p.getId()+"/chat/messages";
    mvc.perform(post(url).with(jwt().jwt(j->j.subject(owner.getId().toString()))).contentType("application/json").content("{\"content\":\"   \"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/projects/"+other.getId()+"/chat/messages").with(jwt().jwt(j->j.subject(owner.getId().toString()))).contentType("application/json").content("{\"content\":\"Other project\"}")).andExpect(status().isCreated());
    mvc.perform(get(url).with(jwt().jwt(j->j.subject(owner.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.content",hasSize(0)));
  }
}
