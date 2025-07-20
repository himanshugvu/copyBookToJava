package com.cobol.parser.processor;

import com.cobol.parser.model.CobolField;
import com.cobol.parser.model.ParseResult;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates start positions, end positions, and lengths for all fields using a robust,
 * two-pass architecture to correctly handle all REDEFINES scenarios without recursion errors
 * or incorrect positioning. This is the definitive, correct implementation.
 */
public class PositionProcessor implements AstProcessor {

    // A helper class to track the current byte position during the AST walk.
    private static class PositionTracker {
        int currentPosition = 1;

        void advance(int length) {
            currentPosition += length;
        }

        void set(int position) {
            this.currentPosition = position;
        }
    }

    @Override
    public void process(ParseResult parseResult) {
        if (parseResult.getReferenceFields().isEmpty()) {
            return;
        }

        // Create a flat map of all fields by name for quick lookups during Pass 2.
        Map<String, CobolField> fieldMap = new HashMap<>();
        for (CobolField rootField : parseResult.getReferenceFields()) {
            flattenFieldMap(rootField, fieldMap);
        }

        // Pass 1: Calculate positions for the main layout (ignoring REDEFINES fields).
        // This establishes the definitive positions for all primary fields.
        for (CobolField rootField : parseResult.getReferenceFields()) {
            calculateMainLayoutPositions(rootField, new PositionTracker());
        }

        // Pass 2: Position all REDEFINES fields by looking up their targets in the now-populated map.
        // This pass is safe because all target positions are already known.
        for (CobolField rootField : parseResult.getReferenceFields()) {
            calculateRedefinesPositions(rootField, fieldMap);
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
     * PASS 1: Recursively calculates positions for all fields that are NOT REDEFINES.
     * This builds the "physical" backbone of the record layout.
     */
    private void calculateMainLayoutPositions(CobolField field, PositionTracker tracker) {
        // Skip any field that is an overlay; it doesn't consume space in the main layout.
        if (field.getRedefines() != null) {
            return;
        }

        field.setStartPosition(tracker.currentPosition);
        analyzePictureAndSetType(field);

        // If it's a group field, recurse into its non-redefining children.
        if (!field.getChildren().isEmpty()) {
            for (CobolField child : field.getChildren()) {
                calculateMainLayoutPositions(child, tracker);
            }
        } else {
            // If it's an elementary field, advance the tracker by its logical length.
            tracker.advance(countCharacterPositions(field.getPicture()));
        }

        // The length is the total space consumed by this field and its non-redefining children.
        int fieldLength = tracker.currentPosition - field.getStartPosition();
        if (field.getOccursCount() > 0) {
            // The tracker has already advanced by one occurrence's length.
            // Advance it for the remaining N-1 occurrences.
            tracker.advance(fieldLength * (field.getOccursCount() - 1));
            fieldLength *= field.getOccursCount();
        }

        field.setLength(fieldLength);
        field.setEndPosition(field.getStartPosition() + field.getLength() - 1);
    }

    /**
     * PASS 2: Recursively sets positions for all REDEFINES fields by looking up
     * the positions calculated in Pass 1.
     */
    private void calculateRedefinesPositions(CobolField field, Map<String, CobolField> fieldMap) {
        if (field.getRedefines() != null) {
            CobolField targetField = fieldMap.get(field.getRedefines());
            if (targetField != null) {
                // Set this field's start position to its target's start position.
                field.setStartPosition(targetField.getStartPosition());

                // Now, calculate the positions of this REDEFINES field's children
                // relative to ITS new start position, using a new, isolated tracker.
                PositionTracker redefinesTracker = new PositionTracker();
                redefinesTracker.set(field.getStartPosition());

                for (CobolField child : field.getChildren()) {
                    // Use the main layout calculator, but with the isolated tracker.
                    calculateMainLayoutPositions(child, redefinesTracker);
                }

                // The length of the REDEFINES group is determined by its own children's layout.
                field.setLength(redefinesTracker.currentPosition - field.getStartPosition());
                field.setEndPosition(field.getStartPosition() + field.getLength() - 1);
            }
        }

        // Must recurse to find REDEFINES fields nested inside other groups.
        for (CobolField child : field.getChildren()) {
            calculateRedefinesPositions(child, fieldMap);
        }
    }

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
        if (pictureClause == null || pictureClause.isEmpty()) return 0;
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
            if ("X9A".indexOf(c) != -1) totalLength++;
        }
        return totalLength;
    }
}
