package com.cobol.parser.processor;

import com.cobol.parser.model.CobolField;
import com.cobol.parser.model.ParseResult;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PositionProcessor implements AstProcessor {

    private static class PositionTracker {
        int currentPosition = 1;
        Map<String, Integer> fieldStartPositions = new HashMap<>();

        void advance(int length) { currentPosition += length; }
        void set(int position) { currentPosition = position; }
        void savePosition(String fieldName) { fieldStartPositions.put(fieldName, currentPosition); }
        void restoreForRedefines(String targetField) {
            set(fieldStartPositions.getOrDefault(targetField, 1));
        }
    }

    @Override
    public void process(ParseResult parseResult) {
        PositionTracker tracker = new PositionTracker();
        for (CobolField field : parseResult.getReferenceFields()) {
            calculatePositions(field, tracker);
        }
    }

    private void calculatePositions(CobolField field, PositionTracker tracker) {
        if (field.getRedefines() != null) {
            tracker.restoreForRedefines(field.getRedefines());
        }

        field.setStartPosition(tracker.currentPosition);
        tracker.savePosition(field.getName());

        analyzePictureAndSetType(field);

        int fieldLength;
        if (!field.getChildren().isEmpty()) {
            PositionTracker childTracker = new PositionTracker();
            childTracker.set(tracker.currentPosition);
            for (CobolField child : field.getChildren()) {
                calculatePositions(child, childTracker);
            }
            fieldLength = childTracker.currentPosition - tracker.currentPosition;
        } else {
            fieldLength = countCharacterPositions(field.getPicture());
        }

        if (field.getOccursCount() > 0) {
            fieldLength *= field.getOccursCount();
        }

        field.setLength(fieldLength);
        field.setEndPosition(field.getStartPosition() + fieldLength - 1);

        if (field.getRedefines() == null) {
            tracker.advance(fieldLength);
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
        if (pictureClause == null || pictureClause.isEmpty()) {
            return 0;
        }

        int totalLength = 0;
        Pattern repetitionPattern = Pattern.compile("[X9A]\\((\\d+)\\)");
        Matcher matcher = repetitionPattern.matcher(pictureClause);

        StringBuffer tempClause = new StringBuffer();
        while (matcher.find()) {
            totalLength += Integer.parseInt(matcher.group(1));
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
