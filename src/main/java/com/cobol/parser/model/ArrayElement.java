package com.cobol.parser.model;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ArrayElement {
    private int index;
    private int startPosition;
    private int endPosition;
    private int length;
    private List<FieldPosition> fields = new ArrayList<>();
    public ArrayElement(int index, int startPosition, int length) {
        this.index = index;
        this.startPosition = startPosition;
        this.length = length;
        this.endPosition = startPosition + length - 1;
    }
}
