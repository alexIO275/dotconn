// Teste de integrare pentru chat: REST, izolarea conversațiilor și livrarea prin WebSocket.
package com.vnhackers.dotconn.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vnhackers.dotconn.user.UserRepository;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "GROQ_API_KEY=")
@ActiveProfiles("test")
class ChatIntegrationTests {

  @Autowired WebApplicationContext context;
  @Autowired UserRepository users;
  @LocalServerPort int port;
  private MockMvc mvc;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private record Account(String token, Long userId) {}

  private Account signup() throws Exception {
    String email = "chat-" + UUID.randomUUID() + "@example.com";
    String body = mapper.writeValueAsString(Map.of("email", email, "password", "Test-password-123"));
    var result =
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String token = mapper.readTree(result.getResponse().getContentAsString()).path("token").asString();
    return new Account(token, users.findByEmail(email).orElseThrow().getId());
  }

  private void send(Account from, Account to, String content) throws Exception {
    mvc.perform(
            post("/api/chats/" + to.userId() + "/messages")
                .header("Authorization", "Bearer " + from.token())
                .contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("content", content))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.senderId", is(from.userId().intValue())))
        .andExpect(jsonPath("$.recipientId", is(to.userId().intValue())));
  }

  @Test
  void conversationFlowWithUnreadCountsAndReadReceipts() throws Exception {
    Account ana = signup();
    Account bob = signup();
    send(ana, bob, "Salut, cauți un frontend dev?");
    send(bob, ana, "Da! Hai să vorbim.");
    send(ana, bob, "Perfect.");

    mvc.perform(
            get("/api/chats/" + ana.userId() + "/messages")
                .header("Authorization", "Bearer " + bob.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].content", is("Perfect.")));

    mvc.perform(get("/api/chats").header("Authorization", "Bearer " + bob.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].partnerId", is(ana.userId().intValue())))
        .andExpect(jsonPath("$[0].lastMessage.content", is("Perfect.")))
        .andExpect(jsonPath("$[0].unreadCount", is(2)));

    mvc.perform(
            post("/api/chats/" + ana.userId() + "/read")
                .header("Authorization", "Bearer " + bob.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated", is(2)));

    mvc.perform(get("/api/chats").header("Authorization", "Bearer " + bob.token()))
        .andExpect(jsonPath("$[0].unreadCount", is(0)));
  }

  @Test
  void thirdUserCannotSeeOthersConversation() throws Exception {
    Account ana = signup();
    Account bob = signup();
    Account eve = signup();
    send(ana, bob, "Mesaj privat");

    mvc.perform(
            get("/api/chats/" + bob.userId() + "/messages")
                .header("Authorization", "Bearer " + eve.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
    mvc.perform(get("/api/chats").header("Authorization", "Bearer " + eve.token()))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void rejectsInvalidMessages() throws Exception {
    Account ana = signup();
    mvc.perform(
            post("/api/chats/" + ana.userId() + "/messages")
                .header("Authorization", "Bearer " + ana.token())
                .contentType("application/json")
                .content("{\"content\":\"eu cu mine\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/chats/999999999/messages")
                .header("Authorization", "Bearer " + ana.token())
                .contentType("application/json")
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/chats/999999999/messages")
                .header("Authorization", "Bearer " + ana.token())
                .contentType("application/json")
                .content("{\"content\":\"   \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/chats")).andExpect(status().isUnauthorized());
  }

  private StompSession connect(String token) throws Exception {
    var client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(new JacksonJsonMessageConverter());
    var connectHeaders = new StompHeaders();
    if (token != null) connectHeaders.add("Authorization", "Bearer " + token);
    var handshake = new WebSocketHttpHeaders();
    handshake.setOrigin("http://localhost:5173");
    return client
        .connectAsync(
            "ws://localhost:" + port + "/ws", handshake, connectHeaders, new StompSessionHandlerAdapter() {})
        .get(5, TimeUnit.SECONDS);
  }

  private BlockingQueue<Map<?, ?>> subscribe(StompSession session, String destination)
      throws InterruptedException {
    BlockingQueue<Map<?, ?>> queue = new LinkedBlockingQueue<>();
    session.subscribe(
        destination,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return Map.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            queue.add((Map<?, ?>) payload);
          }
        });
    // Lasă broker-ul să înregistreze abonarea înainte de a trimite.
    Thread.sleep(300);
    return queue;
  }

  @Test
  void deliversMessagesInRealTimeOnlyToParticipants() throws Exception {
    Account ana = signup();
    Account bob = signup();
    Account eve = signup();
    StompSession bobSession = connect(bob.token());
    StompSession eveSession = connect(eve.token());
    var bobInbox = subscribe(bobSession, "/user/queue/messages");
    var eveInbox = subscribe(eveSession, "/user/queue/messages");

    // Trimis prin REST, livrat prin WebSocket.
    send(ana, bob, "Prin REST");
    Map<?, ?> received = bobInbox.poll(5, TimeUnit.SECONDS);
    assertThat(received).isNotNull();
    assertThat(received.get("content")).isEqualTo("Prin REST");
    assertThat(((Number) received.get("senderId")).longValue()).isEqualTo(ana.userId());

    // Trimis direct prin STOMP; expeditorul e luat din token, nu din payload.
    StompSession anaSession = connect(ana.token());
    var anaInbox = subscribe(anaSession, "/user/queue/messages");
    anaSession.send("/app/chat.send", Map.of("recipientId", bob.userId(), "content", "Prin WebSocket"));
    Map<?, ?> viaSocket = bobInbox.poll(5, TimeUnit.SECONDS);
    assertThat(viaSocket).isNotNull();
    assertThat(viaSocket.get("content")).isEqualTo("Prin WebSocket");
    assertThat(((Number) viaSocket.get("senderId")).longValue()).isEqualTo(ana.userId());
    assertThat(anaInbox.poll(5, TimeUnit.SECONDS)).isNotNull();

    assertThat(eveInbox.poll(500, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  void socketErrorsGoOnlyToSender() throws Exception {
    Account ana = signup();
    StompSession anaSession = connect(ana.token());
    var errors = subscribe(anaSession, "/user/queue/errors");
    anaSession.send("/app/chat.send", Map.of("recipientId", ana.userId(), "content", "eu"));
    Map<?, ?> error = errors.poll(5, TimeUnit.SECONDS);
    assertThat(error).isNotNull();
    assertThat(error.get("message")).isEqualTo("Nu îți poți trimite mesaje singur.");
  }

  @Test
  void rejectsSocketWithoutValidToken() {
    assertThatThrownBy(() -> connect(null)).isInstanceOf(java.util.concurrent.ExecutionException.class);
    assertThatThrownBy(() -> connect("not-a-jwt"))
        .isInstanceOf(java.util.concurrent.ExecutionException.class);
  }

  @Test
  void rejectsSubscribingOutsideOwnQueues() throws Exception {
    Account eve = signup();
    StompSession session = connect(eve.token());
    session.subscribe("/queue/messages-user123", new StompSessionHandlerAdapter() {});
    assertThat(waitForDisconnect(session)).isTrue();
    assertThatThrownBy(() -> session.send("/app/chat.send", Map.of())).isInstanceOf(Exception.class);
  }

  private static boolean waitForDisconnect(StompSession session) throws InterruptedException {
    for (int i = 0; i < 50 && session.isConnected(); i++) Thread.sleep(100);
    return !session.isConnected();
  }
}
