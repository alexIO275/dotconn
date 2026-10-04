package com.vnhackers.dotconn.analysis;
import org.springframework.http.HttpStatus;
public class AnalysisException extends RuntimeException {
    private final HttpStatus status;
    public AnalysisException(HttpStatus status, String message) { super(message); this.status=status; }
    public HttpStatus status() { return status; }
}
