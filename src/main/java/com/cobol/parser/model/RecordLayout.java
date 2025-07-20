package com.cobol.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RecordLayout {
    private final String name;
    private String redefines;
    private int startPosition;
    private int endPosition;
    private int length;
    private List<String> recordTypeValues = new ArrayList<>();
    private String description;
    private List<CobolField> fields = new ArrayList<>();
}
