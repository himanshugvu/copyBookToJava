package com.cobol.rules;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
@Data
public class LayoutMapping {
    @JsonProperty("value")
    private String value;
    @JsonProperty("condition")
    private String condition;
    @JsonProperty("copybookRecordName")
    private String copybookRecordName;
    @JsonProperty("layoutName")
    private String layoutName;
}
