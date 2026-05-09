package com.smart.common.security.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sensitive word filter using DFA (Deterministic Finite Automaton) algorithm.
 * Provides efficient matching for large sets of sensitive words.
 *
 * 基于确定有限自动机（DFA）算法的敏感词过滤器。
 * 可高效匹配大规模敏感词集合，支持检测、查找和替换敏感词。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SENSITIVE_WORDS_CACHE = "sensitive:words:set";
    private static final String SENSITIVE_WORDS_DFA_CACHE = "sensitive:words:dfa";

    @Value("${smart.security.sensitive-word.enabled:true}")
    private boolean enabled;

    @Value("${smart.security.sensitive-word.cache-minutes:60}")
    private int cacheMinutes;

    // DFA root node
    private DfaNode root;

    public SensitiveWordFilter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        buildDfa();
    }

    /**
     * Build DFA from sensitive words.
     */
    @SuppressWarnings("unchecked")
    public void buildDfa() {
        root = new DfaNode();

        // Try to load from Redis cache
        Set<String> words = (Set<String>) redisTemplate.opsForValue().get(SENSITIVE_WORDS_DFA_CACHE);

        if (words == null || words.isEmpty()) {
            // Use default sensitive words (in production, load from database)
            words = getDefaultSensitiveWords();
        }

        for (String word : words) {
            if (word != null && !word.trim().isEmpty()) {
                addWord(word.trim().toLowerCase());
            }
        }

        log.info("Sensitive word DFA built with {} words", words.size());
    }

    /**
     * Add a word to DFA.
     */
    public void addWord(String word) {
        DfaNode current = root;
        for (char c : word.toCharArray()) {
            DfaNode child = current.getChild(c);
            if (child == null) {
                child = new DfaNode();
                current.putChild(c, child);
            }
            current = child;
        }
        current.setEnd(true);
    }

    /**
     * Remove a word from DFA.
     */
    public void removeWord(String word) {
        // DFA removal is complex; simpler to rebuild
        buildDfa();
    }

    /**
     * Check if text contains sensitive words.
     */
    public boolean containsSensitive(String text) {
        if (!enabled || text == null || text.isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase();
        for (int i = 0; i < lowerText.length(); i++) {
            DfaNode current = root;
            int matchLength = 0;

            for (int j = i; j < lowerText.length(); j++) {
                char c = lowerText.charAt(j);
                current = current.getChild(c);

                if (current == null) {
                    break;
                }

                if (current.isEnd()) {
                    matchLength = j - i + 1;
                }
            }

            if (matchLength > 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find all sensitive words in text.
     */
    public List<SensitiveWordResult> findSensitiveWords(String text) {
        List<SensitiveWordResult> results = new ArrayList<>();

        if (!enabled || text == null || text.isEmpty()) {
            return results;
        }

        String lowerText = text.toLowerCase();
        for (int i = 0; i < lowerText.length(); i++) {
            DfaNode current = root;
            int matchLength = 0;
            int startIndex = i;

            for (int j = i; j < lowerText.length(); j++) {
                char c = lowerText.charAt(j);
                current = current.getChild(c);

                if (current == null) {
                    break;
                }

                if (current.isEnd()) {
                    matchLength = j - i + 1;
                }
            }

            if (matchLength > 0) {
                String matchedWord = lowerText.substring(startIndex, startIndex + matchLength);
                results.add(new SensitiveWordResult(matchedWord, startIndex, startIndex + matchLength - 1));
                i = startIndex + matchLength - 1; // Skip matched word
            }
        }

        return results;
    }

    /**
     * Replace sensitive words with mask.
     */
    public String replaceSensitive(String text, char maskChar) {
        if (!enabled || text == null || text.isEmpty()) {
            return text;
        }

        String lowerText = text.toLowerCase();
        StringBuilder result = new StringBuilder(text);
        Set<Integer> replacePositions = new HashSet<>();

        for (int i = 0; i < lowerText.length(); i++) {
            DfaNode current = root;
            int matchLength = 0;
            int matchStart = -1;

            for (int j = i; j < lowerText.length(); j++) {
                char c = lowerText.charAt(j);
                current = current.getChild(c);

                if (current == null) {
                    break;
                }

                if (current.isEnd()) {
                    matchLength = j - i + 1;
                    matchStart = i;
                }
            }

            if (matchLength > 0) {
                for (int k = matchStart; k < matchStart + matchLength; k++) {
                    replacePositions.add(k);
                }
                i = matchStart + matchLength - 1;
            }
        }

        // Replace from end to start to maintain indices
        List<Integer> sortedPositions = replacePositions.stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        for (int pos : sortedPositions) {
            result.setCharAt(pos, maskChar);
        }

        return result.toString();
    }

    /**
     * Replace sensitive words with specified mask.
     */
    public String replaceSensitive(String text, String mask) {
        if (!enabled || text == null || text.isEmpty()) {
            return text;
        }

        List<SensitiveWordResult> results = findSensitiveWords(text);
        if (results.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text);
        int offset = 0;

        for (SensitiveWordResult sw : results) {
            int start = sw.getStartIndex() + offset;
            int end = sw.getEndIndex() + offset;
            int length = end - start + 1;

            result.replace(start, end + 1, mask.repeat(length));
            offset += mask.length() - length;
        }

        return result.toString();
    }

    /**
     * Get default sensitive words (for demo).
     */
    private Set<String> getDefaultSensitiveWords() {
        return Set.of(
                "admin", "test", "fuck", "shit", "damn",
                "暴力", "赌博", "毒品", "诈骗", "恐怖"
        );
    }

    /**
     * Reload sensitive words from database.
     */
    public void reloadFromDatabase(Set<String> words) {
        root = new DfaNode();
        for (String word : words) {
            addWord(word.trim().toLowerCase());
        }
        // Cache in Redis
        redisTemplate.opsForValue().set(SENSITIVE_WORDS_DFA_CACHE, words, cacheMinutes, TimeUnit.MINUTES);
        log.info("Sensitive words reloaded: {} words", words.size());
    }

    /**
     * DFA node.
     */
    @Data
    private static class DfaNode {
        private Map<Character, DfaNode> children = new HashMap<>();
        private boolean end = false;

        public DfaNode getChild(char c) {
            return children.get(c);
        }

        public void putChild(char c, DfaNode node) {
            children.put(c, node);
        }
    }

    /**
     * Sensitive word result.
     */
    @Data
    @AllArgsConstructor
    public static class SensitiveWordResult {
        private String word;
        private int startIndex;
        private int endIndex;
    }
}