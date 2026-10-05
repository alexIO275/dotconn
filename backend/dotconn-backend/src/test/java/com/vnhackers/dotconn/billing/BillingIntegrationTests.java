package com.vnhackers.dotconn.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stripe.Stripe;
import com.vnhackers.dotconn.analysis.AnalysisException;
import com.vnhackers.dotconn.analysis.GroqAnalysisClient;
import com.vnhackers.dotconn.analysis.ProjectAnalysisResponse;
import com.vnhackers.dotconn.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties={"GROQ_API_KEY=", "app.billing.secret-key=sk_test_unit_only",
    "app.billing.webhook-secret=whsec_unit_only", "app.billing.frontend-url=http://127.0.0.1:5173",
    "app.billing.price-bronze=price_bronze", "app.billing.price-silver=price_silver", "app.billing.price-gold=price_gold"})
@ActiveProfiles("test")
class BillingIntegrationTests {
  @Autowired WebApplicationContext context;
  @Autowired BillingConfiguration configuration;
  @Autowired BillingAccountRepository accounts;
  @Autowired BillingEventRepository events;
  @Autowired UserRepository users;
  @MockitoBean StripeSdkGateway stripe;
  @MockitoBean GroqAnalysisClient groq;
  @MockitoBean BillingClock clock;
  private MockMvc mvc;
  private final JsonMapper mapper=JsonMapper.builder().build();
  private final Map<String,StripeGateway.Checkout> checkouts=new ConcurrentHashMap<>();
  private final Map<String,StripeGateway.Subscription> subscriptions=new ConcurrentHashMap<>();
  private static final Instant NOW=Instant.parse("2026-10-05T10:00:00Z");
  private static final String DESCRIPTION="Vreau o platformă software cu frontend și backend pentru rezervări.";
  private record Account(String token,Long id) {}

  @BeforeEach void setup() {
    mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    checkouts.clear(); subscriptions.clear();
    when(clock.now()).thenReturn(NOW);
    when(groq.analyze(anyString())).thenReturn(new ProjectAnalysisResponse("Platformă rezervări",
        List.of("frontend","backend"), List.of("API rezervări"), List.of(), List.of("Ce buget ai?")));
    when(stripe.price(anyString())).thenAnswer(call -> price(BillingPlan.values()[
        switch ((String)call.getArgument(0)) { case "price_bronze" -> 1; case "price_silver" -> 2; case "price_gold" -> 3; default -> throw new IllegalArgumentException(); }]));
    when(stripe.createCustomer(anyLong(), anyString(), anyString())).thenAnswer(call ->
        new StripeGateway.Customer("cus_u"+call.getArgument(0),false,call.getArgument(0).toString()));
    when(stripe.subscriptions(anyString())).thenReturn(List.of());
    when(stripe.createCheckout(anyLong(),anyString(),any(),anyString(),anyString())).thenAnswer(call -> {
      Long userId=call.getArgument(0); BillingPlan plan=call.getArgument(2);
      String id="cs_test_u"+userId+plan.id();
      var checkout=new StripeGateway.Checkout(id,false,call.getArgument(1),userId.toString(),userId.toString(),plan.id(),
          "subscription","open","unpaid",null,"https://checkout.stripe.com/c/pay/"+id,NOW.plusSeconds(86400));
      checkouts.put(id,checkout); return checkout;
    });
    when(stripe.checkout(anyString())).thenAnswer(call -> {
      var checkout=checkouts.get(call.getArgument(0));
      if(checkout==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Checkout missing");
      return checkout;
    });
    when(stripe.expireCheckout(anyString(),anyString())).thenAnswer(call -> {
      var previous=checkouts.get(call.getArgument(0));
      var expired=new StripeGateway.Checkout(previous.id(),previous.liveMode(),previous.customerId(),previous.clientReferenceId(),
          previous.userId(),previous.plan(),previous.mode(),"expired",previous.paymentStatus(),null,null,previous.expiresAt());
      checkouts.put(expired.id(),expired);
      return expired;
    });
    when(stripe.subscription(anyString())).thenAnswer(call -> {
      var subscription=subscriptions.get(call.getArgument(0));
      if(subscription==null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Subscription missing");
      return subscription;
    });
    when(stripe.portal(anyString())).thenReturn("https://billing.stripe.com/p/session/unit");
    var verifier=new StripeSdkGateway(configuration);
    when(stripe.verifyWebhook(anyString(),anyString())).thenAnswer(call -> verifier.verifyWebhook(call.getArgument(0),call.getArgument(1)));
  }

  private StripeGateway.Price price(BillingPlan plan) {
    return new StripeGateway.Price(configuration.priceId(plan),false,true,"usd",(long)plan.price(),"month",1L);
  }
  private Account signup() throws Exception {
    String email="billing-"+UUID.randomUUID()+"@example.com";
    var response=mvc.perform(post("/api/auth/signup").contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("email",email,"password","Test-password-123"))))
        .andExpect(status().isCreated()).andReturn();
    return new Account(mapper.readTree(response.getResponse().getContentAsString()).path("token").asString(),users.findByEmail(email).orElseThrow().getId());
  }
  private void checkout(Account account, BillingPlan plan) throws Exception {
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+account.token())
        .contentType("application/json").content("{\"plan\":\""+plan.id()+"\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/cs_test_u"+account.id()+plan.id()));
  }
  private String complete(Account account, BillingPlan plan, String status, boolean cancelAtEnd) {
    String sessionId="cs_test_u"+account.id()+plan.id(), subId="sub_u"+account.id();
    subscriptions.put(subId,new StripeGateway.Subscription(subId,false,"cus_u"+account.id(),account.id().toString(),plan.id(),
        status,cancelAtEnd,NOW.plusSeconds(30*86400L),List.of(new StripeGateway.Item(price(plan),1L))));
    checkouts.put(sessionId,new StripeGateway.Checkout(sessionId,false,"cus_u"+account.id(),account.id().toString(),account.id().toString(),plan.id(),
        "subscription","complete","paid",subId,null,NOW.plusSeconds(86400)));
    return sessionId;
  }
  private void sync(Account account, String id, String expectedPlan) throws Exception {
    mvc.perform(post("/api/billing/sync").header("Authorization","Bearer "+account.token())
        .contentType("application/json").content(mapper.writeValueAsString(Map.of("sessionId",id))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value(expectedPlan));
  }
  private int analyze(Account account) throws Exception {
    return mvc.perform(post("/api/project-analysis").header("Authorization","Bearer "+account.token())
        .contentType("application/json").content(mapper.writeValueAsString(Map.of("description",DESCRIPTION))))
        .andReturn().getResponse().getStatus();
  }
  private String event(Account account, String type, boolean live) {
    Map<String,Object> object=type.startsWith("checkout.") ? Map.of("id","cs_test_u"+account.id()+"silver","object","checkout.session","customer","cus_u"+account.id())
        : Map.of("id",type.startsWith("invoice.") ? "in_unit" : "sub_u"+account.id(),"object",type.startsWith("invoice.") ? "invoice" : "subscription",
            "customer","cus_u"+account.id(),"subscription","sub_u"+account.id(),"status","canceled");
    return mapper.writeValueAsString(Map.of("id","evt_"+UUID.randomUUID().toString().replace("-",""),"object","event",
        "type",type,"livemode",live,"api_version",Stripe.API_VERSION,"created",Instant.now().getEpochSecond(),"data",Map.of("object",object)));
  }
  private String signature(String body) throws Exception {
    long timestamp=Instant.now().getEpochSecond();
    Mac mac=Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("whsec_unit_only".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
    return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
  }

  @Test void publicPlansExactUsdAndAuthenticatedSubscription() throws Exception {
    mvc.perform(get("/api/billing/plans")).andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("stripe-test")).andExpect(jsonPath("$.plans.length()").value(4))
        .andExpect(jsonPath("$.plans[0].id").value("free")).andExpect(jsonPath("$.plans[0].monthlyAnalysisLimit").value(3))
        .andExpect(jsonPath("$.plans[1].monthlyPriceCents").value(999)).andExpect(jsonPath("$.plans[2].monthlyPriceCents").value(1999))
        .andExpect(jsonPath("$.plans[3].monthlyPriceCents").value(3499)).andExpect(jsonPath("$.plans[3].currency").value("usd"));
    mvc.perform(get("/api/billing/subscription")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/billing/checkout").contentType("application/json").content("{\"plan\":\"silver\"}")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/billing/sync").contentType("application/json").content("{\"sessionId\":\"cs_test_fake\"}")).andExpect(status().isUnauthorized());
    Account account=signup();
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("free")).andExpect(jsonPath("$.status").value("free"))
        .andExpect(jsonPath("$.remainingAnalyses").value(3)).andExpect(jsonPath("$.canManage").value(false));
  }

  @Test void checkoutIgnoresBrowserAmountsAndDoesNotActivateFromPostedPlan() throws Exception {
    Account account=signup();
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+account.token()).contentType("application/json")
        .content("{\"plan\":\"bronze\",\"amount\":1,\"currency\":\"eur\",\"userId\":999999}"))
        .andExpect(status().isOk());
    verify(stripe).createCheckout(eq(account.id()),eq("cus_u"+account.id()),eq(BillingPlan.BRONZE),eq("price_bronze"),anyString());
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("free"));
    mvc.perform(post("/api/billing/sync").header("Authorization","Bearer "+account.token()).contentType("application/json")
        .content("{\"sessionId\":\"cs_test_u"+account.id()+"bronze\"}"))
        .andExpect(status().isConflict());
  }

  @Test void unknownFreeAndTamperedStripePricesCannotCheckout() throws Exception {
    Account account=signup();
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+account.token()).contentType("application/json").content("{\"plan\":\"free\"}"))
        .andExpect(status().isBadRequest());
    when(stripe.price("price_silver")).thenReturn(new StripeGateway.Price("price_silver",false,true,"usd",1L,"month",1L));
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+account.token()).contentType("application/json").content("{\"plan\":\"silver\"}"))
        .andExpect(status().isConflict());
    verify(stripe,never()).createCheckout(anyLong(),anyString(),any(),anyString(),anyString());
  }

  @Test void choosingAnotherPlanExpiresOwnedCheckoutBeforeCreatingNewOne() throws Exception {
    Account account=signup();
    checkout(account,BillingPlan.BRONZE);
    String bronzeSession="cs_test_u"+account.id()+"bronze";
    checkout(account,BillingPlan.SILVER);
    assertThat(checkouts.get(bronzeSession).status()).isEqualTo("expired");
    var order=inOrder(stripe);
    order.verify(stripe).createCheckout(eq(account.id()),anyString(),eq(BillingPlan.BRONZE),eq("price_bronze"),anyString());
    order.verify(stripe).expireCheckout(eq(bronzeSession),eq("microcrew-expire-"+bronzeSession));
    order.verify(stripe).createCheckout(eq(account.id()),anyString(),eq(BillingPlan.SILVER),eq("price_silver"),anyString());
    var saved=accounts.findById(account.id()).orElseThrow();
    assertThat(saved.getCheckoutPlan()).isEqualTo("silver");
    assertThat(saved.getCheckoutSequence()).isEqualTo(2);
    assertThat(saved.getPlan()).isEqualTo("free");
  }

  @Test void samePlanReusesCheckoutWithoutExpiringOrCreatingAnother() throws Exception {
    Account account=signup();
    checkout(account,BillingPlan.BRONZE);
    checkout(account,BillingPlan.BRONZE);
    verify(stripe,never()).expireCheckout(anyString(),anyString());
    verify(stripe,times(1)).createCheckout(anyLong(),anyString(),any(),anyString(),anyString());
  }

  @Test void failedExpirationLeavesExistingCheckoutAndNeverCreatesNewOne() throws Exception {
    Account account=signup();
    checkout(account,BillingPlan.BRONZE);
    doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Stripe TEST indisponibil."))
        .when(stripe).expireCheckout(anyString(),anyString());
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+account.token())
        .contentType("application/json").content("{\"plan\":\"silver\"}"))
        .andExpect(status().isBadGateway());
    verify(stripe,times(1)).createCheckout(anyLong(),anyString(),any(),anyString(),anyString());
    var saved=accounts.findById(account.id()).orElseThrow();
    assertThat(saved.getCheckoutPlan()).isEqualTo("bronze");
    assertThat(saved.getCheckoutSequence()).isEqualTo(1);
  }

  @Test void paymentWinningExpirationRaceRefreshesSubscriptionAndBlocksSecondCheckout() throws Exception {
    Account account=signup();
    checkout(account,BillingPlan.BRONZE);
    doAnswer(call -> {
      complete(account,BillingPlan.BRONZE,"active",false);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Session is no longer open.");
    }).when(stripe).expireCheckout(anyString(),anyString());
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+account.token())
        .contentType("application/json").content("{\"plan\":\"silver\"}"))
        .andExpect(status().isConflict());
    verify(stripe,times(1)).createCheckout(anyLong(),anyString(),any(),anyString(),anyString());
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("bronze"))
        .andExpect(jsonPath("$.status").value("active")).andExpect(jsonPath("$.monthlyAnalysisLimit").value(25));
  }

  @Test void foreignSessionAndWrongCustomerOrPriceCannotGrantPlan() throws Exception {
    Account owner=signup(), stranger=signup();
    checkout(owner,BillingPlan.SILVER);
    String session=complete(owner,BillingPlan.SILVER,"active",false);
    mvc.perform(post("/api/billing/sync").header("Authorization","Bearer "+stranger.token()).contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("sessionId",session)))).andExpect(status().isNotFound());
    var valid=subscriptions.get("sub_u"+owner.id());
    subscriptions.put(valid.id(),new StripeGateway.Subscription(valid.id(),false,"cus_foreign",valid.userId(),valid.plan(),valid.status(),false,valid.periodEnd(),valid.items()));
    mvc.perform(post("/api/billing/sync").header("Authorization","Bearer "+owner.token()).contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("sessionId",session)))).andExpect(status().isConflict());
    subscriptions.put(valid.id(),new StripeGateway.Subscription(valid.id(),false,valid.customerId(),valid.userId(),valid.plan(),valid.status(),false,valid.periodEnd(),
        List.of(new StripeGateway.Item(new StripeGateway.Price("price_foreign",false,true,"usd",1999L,"month",1L),1L))));
    mvc.perform(post("/api/billing/sync").header("Authorization","Bearer "+owner.token()).contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("sessionId",session)))).andExpect(status().isConflict());
    assertThat(accounts.findById(owner.id()).orElseThrow().getPlan()).isEqualTo("free");
  }

  @Test void authoritativePaidStatusCancelAndFailureUpdateEntitlement() throws Exception {
    Account account=signup(); checkout(account,BillingPlan.SILVER);
    sync(account,complete(account,BillingPlan.SILVER,"active",true),"silver");
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyAnalysisLimit").value(50))
        .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true)).andExpect(jsonPath("$.canManage").value(true));
    complete(account,BillingPlan.SILVER,"past_due",false);
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("free")).andExpect(jsonPath("$.status").value("past_due"));
    complete(account,BillingPlan.SILVER,"canceled",false);
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("free")).andExpect(jsonPath("$.status").value("canceled"));
  }

  @Test void activeSubscriptionCannotCreateAnotherCheckoutAndPortalUsesStoredCustomer() throws Exception {
    Account account=signup(); checkout(account,BillingPlan.BRONZE);
    sync(account,complete(account,BillingPlan.BRONZE,"active",false),"bronze");
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+account.token()).contentType("application/json").content("{\"plan\":\"gold\"}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/billing/portal").header("Authorization","Bearer "+account.token())).andExpect(status().isOk());
    verify(stripe).portal("cus_u"+account.id());
    verify(stripe,times(1)).createCheckout(anyLong(),anyString(),any(),anyString(),anyString());
  }

  @Test void signatureLiveEventAndReplayAreCheckedAndEventPayloadCannotOverrideCurrentStatus() throws Exception {
    Account account=signup(); checkout(account,BillingPlan.SILVER);
    complete(account,BillingPlan.SILVER,"active",false);
    String raw=event(account,"customer.subscription.deleted",false);
    mvc.perform(post("/api/billing/webhook").contentType("application/json").content(raw)).andExpect(status().isBadRequest());
    mvc.perform(post("/api/billing/webhook").header("Stripe-Signature","t=0,v1=fake").contentType("application/json").content(raw)).andExpect(status().isBadRequest());
    String live=event(account,"customer.subscription.updated",true);
    mvc.perform(post("/api/billing/webhook").header("Stripe-Signature",signature(live)).contentType("application/json").content(live)).andExpect(status().isBadRequest());
    String signed=signature(raw);
    mvc.perform(post("/api/billing/webhook").header("Stripe-Signature",signed).contentType("application/json").content(raw))
        .andExpect(status().isOk());
    mvc.perform(post("/api/billing/webhook").header("Stripe-Signature",signed).contentType("application/json").content(raw))
        .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
    assertThat(accounts.findById(account.id()).orElseThrow().getStatus()).isEqualTo("active");
    verify(stripe,times(1)).subscription("sub_u"+account.id());
  }

  @Test void lateEventsFromOldSubscriptionCannotReplaceCurrentOne() throws Exception {
    Account account=signup(); checkout(account,BillingPlan.SILVER);
    sync(account,complete(account,BillingPlan.SILVER,"active",false),"silver");
    var valid=subscriptions.get("sub_u"+account.id());
    subscriptions.put("sub_old",new StripeGateway.Subscription("sub_old",false,valid.customerId(),valid.userId(),valid.plan(),"canceled",false,valid.periodEnd(),valid.items()));
    String raw=event(account,"customer.subscription.deleted",false).replace("sub_u"+account.id(),"sub_old");
    mvc.perform(post("/api/billing/webhook").header("Stripe-Signature",signature(raw)).contentType("application/json").content(raw)).andExpect(status().isOk());
    assertThat(accounts.findById(account.id()).orElseThrow().getStripeSubscriptionId()).isEqualTo(valid.id());
    assertThat(accounts.findById(account.id()).orElseThrow().getStatus()).isEqualTo("active");
  }

  @Test void actualPriceCanUpgradeTierWithoutChangingInitialPlanMetadata() throws Exception {
    Account account=signup(); checkout(account,BillingPlan.BRONZE);
    sync(account,complete(account,BillingPlan.BRONZE,"active",false),"bronze");
    var valid=subscriptions.get("sub_u"+account.id());
    subscriptions.put(valid.id(),new StripeGateway.Subscription(valid.id(),false,valid.customerId(),valid.userId(),"bronze","active",false,valid.periodEnd(),
        List.of(new StripeGateway.Item(price(BillingPlan.GOLD),1L))));
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("gold")).andExpect(jsonPath("$.monthlyAnalysisLimit").value(200));
  }

  @Test void successfulAnalysesOnlyAndMonthlyReset() throws Exception {
    Account account=signup();
    when(groq.analyze(anyString())).thenThrow(new AnalysisException(HttpStatus.BAD_GATEWAY,"Provider failed"));
    assertThat(analyze(account)).isEqualTo(502);
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.analysesUsed").value(0));
    doReturn(new ProjectAnalysisResponse("Valid",List.of("backend"),List.of("API"),List.of(),List.of())).when(groq).analyze(anyString());
    assertThat(analyze(account)).isEqualTo(200); assertThat(analyze(account)).isEqualTo(200); assertThat(analyze(account)).isEqualTo(200);
    mvc.perform(post("/api/project-analysis").header("Authorization","Bearer "+account.token()).contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("description",DESCRIPTION)))).andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("analysis_quota_exceeded"));
    when(clock.now()).thenReturn(Instant.parse("2026-11-01T00:00:00Z"));
    assertThat(analyze(account)).isEqualTo(200);
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.analysesUsed").value(1))
        .andExpect(jsonPath("$.usageResetAt").value("2026-12-01T00:00:00Z"));
  }

  @Test void quotaConcurrencyCannotExceedRemainingCapacityAndUpgradePreservesUsage() throws Exception {
    Account account=signup();
    assertThat(analyze(account)).isEqualTo(200); assertThat(analyze(account)).isEqualTo(200);
    try(var executor=Executors.newFixedThreadPool(2)) {
      var first=executor.submit(() -> analyze(account)); var second=executor.submit(() -> analyze(account));
      assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,429);
    }
    assertThat(accounts.findById(account.id()).orElseThrow().getAnalysesUsed()).isEqualTo(3);
    checkout(account,BillingPlan.BRONZE); sync(account,complete(account,BillingPlan.BRONZE,"active",false),"bronze");
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+account.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyAnalysisLimit").value(25))
        .andExpect(jsonPath("$.analysesUsed").value(3)).andExpect(jsonPath("$.remainingAnalyses").value(22));
  }

  @Test void concurrentCheckoutDoubleClickReusesOneSession() throws Exception {
    Account account=signup();
    try(var executor=Executors.newFixedThreadPool(2)) {
      var first=executor.submit(() -> { checkout(account,BillingPlan.SILVER); return true; });
      var second=executor.submit(() -> { checkout(account,BillingPlan.SILVER); return true; });
      assertThat(first.get()).isTrue(); assertThat(second.get()).isTrue();
    }
    verify(stripe,times(1)).createCustomer(anyLong(),anyString(),anyString());
    verify(stripe,times(1)).createCheckout(anyLong(),anyString(),any(),anyString(),anyString());
  }
}
