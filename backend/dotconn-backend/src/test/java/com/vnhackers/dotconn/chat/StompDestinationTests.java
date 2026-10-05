package com.vnhackers.dotconn.chat;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
class StompDestinationTests {
  private final StompAuthInterceptor interceptor=new StompAuthInterceptor(token->{throw new UnsupportedOperationException();});
  private void check(StompCommand command,String destination){
    var accessor=StompHeaderAccessor.create(command);accessor.setUser(()->"123");accessor.setDestination(destination);accessor.setLeaveMutable(true);
    interceptor.preSend(MessageBuilder.createMessage(new byte[0],accessor.getMessageHeaders()),null);
  }
  @Test void cannotPublishDirectlyIntoAnotherUsersQueue(){
    for(String path:java.util.List.of("/user/456/queue/messages","/queue/messages","/app/unknown"))assertThatThrownBy(()->check(StompCommand.SEND,path)).isInstanceOf(MessageDeliveryException.class);
    assertThatCode(()->check(StompCommand.SEND,"/app/chat.send")).doesNotThrowAnyException();
  }
  @Test void onlyKnownPrivateSubscriptionsAreAccepted(){
    assertThatThrownBy(()->check(StompCommand.SUBSCRIBE,"/user/queue/other")).isInstanceOf(MessageDeliveryException.class);
    assertThatCode(()->check(StompCommand.SUBSCRIBE,"/user/queue/messages")).doesNotThrowAnyException();
  }
}
