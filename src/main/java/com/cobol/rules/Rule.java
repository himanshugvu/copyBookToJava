package com.cobol.rules;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
@Data
public class Rule {
    @JsonProperty("id")
    private String id;
    @JsonProperty("description")
    private String description;
    @JsonProperty("identifier")
    private Identifier identifier;
    @JsonProperty("layouts")
    private List<LayoutMapping> layouts;
}
