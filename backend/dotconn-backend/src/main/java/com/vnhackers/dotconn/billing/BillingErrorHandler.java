package com.vnhackers.dotconn.billing;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=BillingController.class)
public class BillingErrorHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String,String>> billing(ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode()).body(Map.of("detail", exception.getReason()==null ? "Cerere invalidă." : exception.getReason()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String,String>> validation() { return ResponseEntity.badRequest().body(Map.of("detail", "Planul sau checkout-ul trimis este invalid.")); }
}
