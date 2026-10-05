package com.vnhackers.dotconn.billing;

import com.vnhackers.dotconn.analysis.AnalysisException;
import org.springframework.http.HttpStatus;

public class AnalysisLimitException extends AnalysisException {
  public AnalysisLimitException(int limit) {
    super(HttpStatus.TOO_MANY_REQUESTS, "Ai folosit cele " + limit + " analize AI ale lunii. Alege un abonament sau așteaptă resetarea lunară UTC.");
  }
}
