package com.cobol.parser;

import com.cobol.parser.model.ParseResult;
import com.cobol.parser.processor.LayoutProcessor;
import com.cobol.parser.processor.OccursProcessor;
import com.cobol.parser.processor.PositionProcessor;
import com.cobol.parser.token.CobolToken;
import com.cobol.parser.token.Tokenizer;
import com.cobol.parser.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopybookParserFacade {

    private final Tokenizer tokenizer;
    private final AstBuilder astBuilder;
    private final PositionProcessor positionProcessor;
    private final LayoutProcessor layoutProcessor;
    private final OccursProcessor occursProcessor;

    public CopybookParserFacade() {
        this.tokenizer = new Tokenizer();
        this.astBuilder = new AstBuilder();
        this.positionProcessor = new PositionProcessor();
        this.layoutProcessor = new LayoutProcessor();
        this.occursProcessor = new OccursProcessor();
    }

    public ParseResult parse(Path copybookPath) throws IOException {
        List<String> lines = FileUtils.readLines(copybookPath);
        List<CobolToken> tokens = tokenizer.tokenize(lines);

        int recordLength = extractRecordLength(lines, tokens);

        ParseResult result = astBuilder.build(tokens);
        result.setFileName(copybookPath.getFileName().toString());
        result.setTotalLength(recordLength);

        // --- Processing Pipeline ---
        positionProcessor.process(result);
        layoutProcessor.process(result);
        occursProcessor.process(result);

        return result;
    }

    private int extractRecordLength(List<String> lines, List<CobolToken> tokens) {
        Pattern commentPattern = Pattern.compile("^\\*.*(?:REC\\s+LEN|RECORD\\s+SIZE)\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            Matcher matcher = commentPattern.matcher(line.trim());
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }

        for (CobolToken token : tokens) {
            if (token.getLevel() == 1 && token.getRedefines() == null && token.getPicture() != null) {
                Pattern picPattern = Pattern.compile("[Xx]\\((\\d+)\\)");
                Matcher matcher = picPattern.matcher(token.getPicture());
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }

        return 0; // Let the processor calculate it if not found
    }
}
