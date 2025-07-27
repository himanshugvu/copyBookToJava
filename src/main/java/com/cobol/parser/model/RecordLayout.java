package com.cobol.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RecordLayout {
    private final String name;
    private int startPosition;
    private int endPosition;
    private int length;
    private Map<String, String> identificationCriteria = new HashMap<>();
    private String description;
    private List<CobolField> fields = new ArrayList<>();
}
