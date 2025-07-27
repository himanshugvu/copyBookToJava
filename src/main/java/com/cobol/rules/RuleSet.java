package com.cobol.rules;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
@Data
public class RuleSet {
    @JsonProperty("rules")
    private List<Rule> rules;
}
