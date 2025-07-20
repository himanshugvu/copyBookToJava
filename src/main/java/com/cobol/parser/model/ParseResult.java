package com.cobol.parser.model;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ParseResult {
    private String fileName;
    private int totalLength;
    private List<CobolField> referenceFields = new ArrayList<>();
    private List<RecordLayout> recordLayouts = new ArrayList<>();
}
