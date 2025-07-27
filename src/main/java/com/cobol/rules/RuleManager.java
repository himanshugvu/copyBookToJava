package com.cobol.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RuleManager {

    private static final String RULES_FILE = "/rules.yml";
    private static final RuleManager INSTANCE = new RuleManager();
    private final Map<String, Rule> ruleMap;

    private RuleManager() {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            InputStream inputStream = RuleManager.class.getResourceAsStream(RULES_FILE);
            if (inputStream == null) throw new IllegalStateException("Cannot find rules file: " + RULES_FILE);
            RuleSet ruleSet = mapper.readValue(inputStream, RuleSet.class);
            this.ruleMap = ruleSet.getRules().stream().collect(Collectors.toMap(Rule::getId, Function.identity()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load or parse rules configuration.", e);
        }
    }

    public static RuleManager getInstance() {
        return INSTANCE;
    }

    public Optional<Rule> getRule(String ruleId) {
        return Optional.ofNullable(ruleMap.get(ruleId));
    }
}
