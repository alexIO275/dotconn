package com.vnhackers.dotconn.analysis;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import jakarta.validation.Validation;
import org.springframework.http.HttpStatus;
class GroqAnalysisClientTests {
    private HttpServer server;
    private String reply;
    private int status=200;
    private String submitted;
    private GroqAnalysisClient client;
    private final JsonMapper mapper=JsonMapper.builder().build();
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            submitted=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            byte[] bytes=reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        client=new GroqAnalysisClient("test-key","test-model",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),HttpClient.newHttpClient());
    }
    @AfterEach void cleanup(){server.stop(0);}
    void answer(String content,String finish){reply=mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",finish,"message",Map.of("content",content)))));}
    @Test void parsesStructuredResultAndKeepsInputInUserMessage(){
        answer("{\"summary\":\"Ai nevoie de backend.\",\"roles\":[\"backend\"],\"tasks\":[\"API\"],\"existingStack\":[\"React\"],\"missingInformation\":[\"Buget?\"]}","stop");
        var result=client.analyze("Am frontend React; caut backend.");
        assertEquals(List.of("backend"),result.roles());
        var request=mapper.readTree(submitted);
        assertTrue(request.path("response_format").path("json_schema").path("strict").asBoolean());
        assertEquals("user",request.path("messages").path(1).path("role").asString());
    }
    @Test void missingKeyDoesNotCallProvider(){
        var unconfigured=new GroqAnalysisClient("","test-model",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),HttpClient.newHttpClient());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,assertThrows(AnalysisException.class,()->unconfigured.analyze("test")).status());
        assertNull(submitted);
    }
    @Test void rejectsTruncatedOutput(){answer("{}","length");assertEquals(HttpStatus.BAD_GATEWAY,assertThrows(AnalysisException.class,()->client.analyze("test")).status());}
    @Test void mapsRateLimitWithoutLeakingProviderBody(){status=429;reply="private upstream details";var e=assertThrows(AnalysisException.class,()->client.analyze("test"));assertEquals(HttpStatus.TOO_MANY_REQUESTS,e.status());assertFalse(e.getMessage().contains("private"));}
    @Test void validatesRolesAndRequiredFields(){
        answer("{\"summary\":\"Test\",\"roles\":[\"invented-role\"],\"tasks\":[],\"existingStack\":[],\"missingInformation\":[]}","stop");
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var service=new ProjectAnalysisService(client,factory.getValidator());
            assertEquals(HttpStatus.BAD_GATEWAY,assertThrows(AnalysisException.class,()->service.analyze("test")).status());
            assertFalse(factory.getValidator().validate(new ProjectAnalysisRequest(" ")).isEmpty());
        }
    }
}
