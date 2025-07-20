package com.cobol.parser.processor;

import com.cobol.parser.model.CobolField;
import com.cobol.parser.model.ParseResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PositionProcessor implements AstProcessor {

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

        Map<String, CobolField> fieldMap = new HashMap<>();
        for (CobolField rootField : parseResult.getReferenceFields()) {
            flattenFieldMap(rootField, fieldMap);
        }

        for (CobolField rootField : parseResult.getReferenceFields()) {
            calculateMainLayoutPositions(rootField, new PositionTracker());
        }

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

    private void calculateMainLayoutPositions(CobolField field, PositionTracker tracker) {
        if (field.getRedefines() != null) {
            return;
        }

        field.setStartPosition(tracker.currentPosition);
        analyzePictureAndSetType(field);

        if (!field.getChildren().isEmpty()) {
            for (CobolField child : field.getChildren()) {
                calculateMainLayoutPositions(child, tracker);
            }
        } else {
            tracker.advance(countCharacterPositions(field.getPicture()));
        }

        int fieldLength = tracker.currentPosition - field.getStartPosition();
        if (field.getOccursCount() > 0) {
            tracker.advance(fieldLength * (field.getOccursCount() - 1));
            fieldLength *= field.getOccursCount();
        }

        field.setLength(fieldLength);
        field.setEndPosition(field.getStartPosition() + field.getLength() - 1);
    }

    private void calculateRedefinesPositions(CobolField field, Map<String, CobolField> fieldMap) {
        if (field.getRedefines() != null) {
            CobolField targetField = fieldMap.get(field.getRedefines());
            if (targetField != null) {
                field.setStartPosition(targetField.getStartPosition());

                PositionTracker redefinesTracker = new PositionTracker();
                redefinesTracker.set(field.getStartPosition());

                for (CobolField child : field.getChildren()) {
                    calculateMainLayoutPositions(child, redefinesTracker);
                }

                field.setLength(redefinesTracker.currentPosition - field.getStartPosition());
                field.setEndPosition(field.getStartPosition() + field.getLength() - 1);
            }
        }

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
