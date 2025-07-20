package com.cobol.parser.processor;

import com.cobol.parser.model.CobolField;
import com.cobol.parser.model.ParseResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates the start position, end position, and byte length for every field in the AST.
 * This processor uses a non-recursive, stack-based traversal to prevent StackOverflowError
 * when handling complex nested REDEFINES clauses.
 */
public class PositionProcessor implements AstProcessor {

    private static class TraversalState {
        CobolField field;
        int childIndex = 0;
        int startPosition;

        TraversalState(CobolField field, int startPosition) {
            this.field = field;
            this.startPosition = startPosition;
        }
    }

    @Override
    public void process(ParseResult parseResult) {
        if (parseResult.getReferenceFields().isEmpty()) {
            return;
        }
        // Create a flat map for quick lookups, which is essential for resolving REDEFINES.
        Map<String, CobolField> fieldMap = new HashMap<>();
        for (CobolField rootField : parseResult.getReferenceFields()) {
            flattenFieldMap(rootField, fieldMap);
        }

        // Process positions for the entire raw structure.
        for (CobolField rootField : parseResult.getReferenceFields()) {
            calculateAllPositions(rootField, fieldMap);
        }
    }

    private void flattenFieldMap(CobolField field, Map<String, CobolField> map) {
        if (field == null) return;
        map.put(field.getName(), field);
        for (CobolField child : field.getChildren()) {
            flattenFieldMap(child, map);
        }
    }

    /**
     * Calculates positions for all fields using a non-recursive, iterative approach
     * to completely avoid StackOverflowErrors.
     */
    private void calculateAllPositions(CobolField root, Map<String, CobolField> fieldMap) {
        Stack<TraversalState> stack = new Stack<>();
        stack.push(new TraversalState(root, 1));

        while (!stack.isEmpty()) {
            TraversalState currentState = stack.peek();
            CobolField currentField = currentState.field;

            // Pre-order processing: Set start position when first visiting a node.
            if (currentState.childIndex == 0) {
                // If it's a redefines, get its start position from the target field.
                if (currentField.getRedefines() != null) {
                    CobolField target = fieldMap.get(currentField.getRedefines());
                    if (target != null && target.getStartPosition() > 0) {
                        currentField.setStartPosition(target.getStartPosition());
                    } else {
                        // Fallback if target hasn't been processed, although the flat map helps.
                        currentField.setStartPosition(currentState.startPosition);
                    }
                } else {
                    currentField.setStartPosition(currentState.startPosition);
                }
                analyzePictureAndSetType(currentField);
            }

            // Process children
            if (currentState.childIndex < currentField.getChildren().size()) {
                CobolField child = currentField.getChildren().get(currentState.childIndex);
                // The next child starts where the current one does, unless it's a REDEFINES.
                int childStartPosition = currentField.getStartPosition();
                if (currentState.childIndex > 0) {
                    CobolField previousSibling = currentField.getChildren().get(currentState.childIndex - 1);
                    if (previousSibling.getRedefines() == null) {
                        childStartPosition = previousSibling.getEndPosition() + 1;
                    } else {
                        // If previous was a redefines, this child starts where the previous one started.
                        childStartPosition = previousSibling.getStartPosition();
                    }
                }
                currentState.childIndex++;
                stack.push(new TraversalState(child, childStartPosition));
            } else {
                // Post-order processing: All children have been processed, so now we can calculate length.
                int fieldLength;
                if (!currentField.getChildren().isEmpty()) {
                    // Group length is from its start to the end of its last non-redefining child.
                    int maxEndPos = currentField.getStartPosition() -1;
                    for(CobolField child : currentField.getChildren()){
                        if(child.getRedefines() == null) {
                            maxEndPos = Math.max(maxEndPos, child.getEndPosition());
                        }
                    }
                    fieldLength = maxEndPos - currentField.getStartPosition() + 1;
                } else {
                    // Elementary field length is from its PIC clause.
                    fieldLength = countCharacterPositions(currentField.getPicture());
                }

                if (currentField.getOccursCount() > 0) {
                    fieldLength *= currentField.getOccursCount();
                }

                currentField.setLength(fieldLength);
                currentField.setEndPosition(currentField.getStartPosition() + fieldLength - 1);
                stack.pop();
            }
        }
    }

    // --- Helper Methods (Unchanged but included for completeness) ---

    private void analyzePictureAndSetType(CobolField field) {
        if (field.getPicture() == null) {
            field.setDataType("GROUP");
            return;
        }
        String pic = field.getPicture().toUpperCase();
        field.setSigned(pic.startsWith("S"));
        field.setDecimal(pic.contains("V"));

        if (field.isDecimal()) {
            String decimalPart = pic.substring(pic.indexOf('V') + 1);
            field.setDecimalPlaces(countCharacterPositions(decimalPart));
        }

        if (pic.contains("X") || pic.contains("A")) {
            field.setDataType("STRING");
        } else if (pic.contains("9")) {
            field.setDataType("NUMBER");
        }
    }

    private int countCharacterPositions(String pictureClause) {
        if (pictureClause == null || pictureClause.isEmpty()) {
            return 0;
        }

        int totalLength = 0;
        Pattern repetitionPattern = Pattern.compile("([X9A])\\((\\d+)\\)");
        Matcher matcher = repetitionPattern.matcher(pictureClause);

        StringBuffer tempClause = new StringBuffer();
        while (matcher.find()) {
            totalLength += Integer.parseInt(matcher.group(2));
            matcher.appendReplacement(tempClause, "");
        }
        matcher.appendTail(tempClause);

        String remainingPart = tempClause.toString();
        for (char c : remainingPart.toCharArray()) {
            if ("X9A".indexOf(c) != -1) {
                totalLength++;
            }
        }
        return totalLength;
    }
}
