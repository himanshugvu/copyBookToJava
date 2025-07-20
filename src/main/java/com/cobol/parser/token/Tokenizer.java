package com.cobol.parser.token;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokenizer {

    private static final Pattern LEVEL_NAME_PATTERN = Pattern.compile("^\\s*(\\d{2})\\s+([A-Za-z0-9-]+).*$");
    private static final Pattern PIC_PATTERN = Pattern.compile("PIC\\s+([^\\s.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE_PATTERN = Pattern.compile("VALUE\\s+['\"]?([^'\"\\s.]+)['\"]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern USAGE_PATTERN = Pattern.compile("(COMP(?:-[1-5X])?|BINARY|PACKED-DECIMAL|DISPLAY)(?!-)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OCCURS_PATTERN = Pattern.compile("OCCURS\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REDEFINES_PATTERN = Pattern.compile("REDEFINES\\s+([A-Za-z0-9-]+)", Pattern.CASE_INSENSITIVE);

    public List<CobolToken> tokenize(List<String> lines) {
        List<CobolToken> tokens = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("*")) {
                continue;
            }

            Matcher levelNameMatcher = LEVEL_NAME_PATTERN.matcher(trimmed);
            if (levelNameMatcher.matches()) {
                CobolToken token = new CobolToken();
                token.setLevel(Integer.parseInt(levelNameMatcher.group(1)));
                token.setName(levelNameMatcher.group(2));
                token.setConditionName(token.getLevel() == 88);

                if (!token.isConditionName()) {
                    extract(PIC_PATTERN, trimmed, token::setPicture);
                    extract(USAGE_PATTERN, trimmed, token::setUsage);
                    extract(REDEFINES_PATTERN, trimmed, token::setRedefines);
                    extract(OCCURS_PATTERN, trimmed, (s) -> token.setOccurs(Integer.parseInt(s)));
                }
                extract(VALUE_PATTERN, trimmed, token::setValue);

                tokens.add(token);
            }
        }
        return tokens;
    }

    private void extract(Pattern pattern, String line, Consumer<String> setter) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            setter.accept(matcher.group(1));
        }
    }
}
