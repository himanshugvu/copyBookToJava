package com.cobol.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CobolField {
    private int level;
    private String name;
    private String picture;
    private int startPosition;
    private int endPosition;
    private int length;
    private String dataType;
    private String usage;
    private boolean signed;
    private boolean decimal;
    private int decimalPlaces;
    private int occursCount;
    private String redefines;
    private String value;
    private List<CobolField> children = new ArrayList<>();
    private List<ArrayElement> arrayElements = new ArrayList<>();
    private List<ConditionName> conditionNames = new ArrayList<>();

    public CobolField(int level, String name) {
        this.level = level;
        this.name = name;
    }

    public void addChild(CobolField child) {
        this.children.add(child);
    }

    public void addConditionName(ConditionName conditionName) {
        this.conditionNames.add(conditionName);
    }
}
