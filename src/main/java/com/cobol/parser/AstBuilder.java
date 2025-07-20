package com.cobol.parser;
import com.cobol.parser.model.CobolField;
import com.cobol.parser.model.ConditionName;
import com.cobol.parser.model.ParseResult;
import com.cobol.parser.token.CobolToken;
import java.util.List;
import java.util.Stack;
public class AstBuilder {
    public ParseResult build(List<CobolToken> tokens) {
        ParseResult result = new ParseResult();
        Stack<CobolField> fieldStack = new Stack<>();
        for (CobolToken token : tokens) {
            if (token.isConditionName()) {
                if (!fieldStack.isEmpty()) fieldStack.peek().addConditionName(new ConditionName(token.getName(), token.getValue()));
                continue;
            }
            CobolField field = createFieldFromToken(token);
            while (!fieldStack.isEmpty() && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField parent = fieldStack.pop();
                if (fieldStack.isEmpty()) result.getReferenceFields().add(parent);
                else fieldStack.peek().addChild(parent);
            }
            fieldStack.push(field);
        }
        while (!fieldStack.isEmpty()) {
            CobolField parent = fieldStack.pop();
            if (fieldStack.isEmpty()) result.getReferenceFields().add(parent);
            else fieldStack.peek().addChild(parent);
        }
        return result;
    }
    private CobolField createFieldFromToken(CobolToken token) {
        CobolField field = new CobolField(token.getLevel(), token.getName());
        field.setPicture(token.getPicture());
        field.setOccursCount(token.getOccurs());
        field.setRedefines(token.getRedefines());
        field.setUsage(getMeaningfulUsage(token.getUsage()));
        field.setValue(token.getValue());
        return field;
    }
    private String getMeaningfulUsage(String usage) {
        if (usage == null) return "Text/ASCII format (1 byte per character)";
        return switch (usage.toUpperCase()) {
            case "COMP", "BINARY", "COMP-4", "COMP-5" -> "Binary format (2, 4, or 8 bytes)";
            case "COMP-1" -> "Single precision floating point (4 bytes)";
            case "COMP-2" -> "Double precision floating point (8 bytes)";
            case "COMP-3", "PACKED-DECIMAL" -> "Packed decimal format (space efficient)";
            case "INDEX" -> "Index data item (4 bytes)";
            case "POINTER" -> "Pointer data item (4 bytes)";
            default -> "Text/ASCII format (1 byte per character)";
        };
    }
}
