package com.cobol.rules;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
@Data
public class Identifier {
    @JsonProperty("type")
    private String type;
    @JsonProperty("position")
    private Integer position;
    @JsonProperty("length")
    private Integer length;
}
