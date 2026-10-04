package com.vnhackers.dotconn.analysis;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
@RestControllerAdvice(assignableTypes=ProjectAnalysisController.class)
public class AnalysisErrorHandler {
    public record ErrorResponse(String message) {}
    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ErrorResponse> analysis(AnalysisException e) {return ResponseEntity.status(e.status()).body(new ErrorResponse(e.getMessage()));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {return ResponseEntity.badRequest().body(new ErrorResponse("Descrierea trebuie să aibă între 20 și 5000 de caractere."));}
}
