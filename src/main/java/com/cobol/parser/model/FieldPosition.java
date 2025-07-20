package com.cobol.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FieldPosition {
    private String name;
    private int startPosition;
    private int endPosition;
    private int length;
    private String picture;
    private String dataType;
    private String usage;
}
