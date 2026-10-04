package com.vnhackers.dotconn.analysis;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
@Service
public class ProjectAnalysisService {
    private final GroqAnalysisClient client;
    private final Validator validator;
    public ProjectAnalysisService(GroqAnalysisClient client,Validator validator) {this.client=client;this.validator=validator;}
    public ProjectAnalysisResponse analyze(String description) {
        var result=client.analyze(description.trim());
        if(!validator.validate(result).isEmpty()) throw new AnalysisException(HttpStatus.BAD_GATEWAY,"Serviciul a returnat o analiză invalidă.");
        return result;
    }
}
