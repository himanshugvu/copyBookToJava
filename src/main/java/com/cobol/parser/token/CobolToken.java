package com.cobol.parser.token;
import lombok.Data;
@Data
public class CobolToken {
    private int level;
    private String name;
    private String picture;
    private String usage;
    private int occurs;
    private String redefines;
    private String value;
    private boolean isConditionName;
}
