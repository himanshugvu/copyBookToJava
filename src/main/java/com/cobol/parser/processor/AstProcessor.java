package com.cobol.parser.processor;

import com.cobol.parser.model.ParseResult;

public interface AstProcessor {
    void process(ParseResult parseResult);
}
