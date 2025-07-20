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
        int recordLength = extractRecordLength(lines);

        List<CobolToken> tokens = tokenizer.tokenize(lines);
        ParseResult result = astBuilder.build(tokens);
        result.setFileName(copybookPath.getFileName().toString());
        result.setTotalLength(recordLength);

        // --- Processing Pipeline ---
        // 1. Calculate initial positions for the raw AST
        positionProcessor.process(result);
        // 2. Identify copybook pattern and create distinct record layouts
        layoutProcessor.process(result);
        // 3. Expand OCCURS clauses within the generated layouts
        occursProcessor.process(result);

        return result;
    }

    private int extractRecordLength(List<String> lines) {
        Pattern pattern = Pattern.compile("\\*\\s*REC\\s+LEN\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 0; // If not found, it will be calculated from the base record later
    }
}
