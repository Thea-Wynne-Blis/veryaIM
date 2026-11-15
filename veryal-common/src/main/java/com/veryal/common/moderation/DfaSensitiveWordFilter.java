package com.veryal.common.moderation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DFA 敏感词过滤器。构建一次 Trie，之后 O(n) 扫描文本。
 */
public final class DfaSensitiveWordFilter {

    private static final char MASK = '*';

    private final Node root = new Node();

    public DfaSensitiveWordFilter(Collection<String> words) {
        if (words != null) {
            words.stream()
                    .filter(word -> word != null && !word.isBlank())
                    .map(word -> word.trim().toLowerCase(Locale.ROOT))
                    .forEach(this::add);
        }
    }

    public boolean contains(String text) {
        return firstEnd(text) >= 0;
    }

    public String replace(String text) {
        if (text == null || text.isEmpty() || root.children.isEmpty()) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(text);
        int i = 0;
        while (i < lower.length()) {
            Node node = root;
            int matchEnd = -1;
            for (int j = i; j < lower.length(); j++) {
                node = node.children.get(lower.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.end) {
                    matchEnd = j;
                }
            }
            if (matchEnd >= 0) {
                for (int k = i; k <= matchEnd; k++) {
                    result.setCharAt(k, MASK);
                }
                i = matchEnd + 1;
            } else {
                i++;
            }
        }
        return result.toString();
    }

    public List<String> findAll(String text) {
        List<String> found = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < lower.length()) {
            Node node = root;
            int matchEnd = -1;
            for (int j = i; j < lower.length(); j++) {
                node = node.children.get(lower.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.end) {
                    matchEnd = j;
                }
            }
            if (matchEnd >= 0) {
                found.add(text.substring(i, matchEnd + 1));
                i = matchEnd + 1;
            } else {
                i++;
            }
        }
        return found;
    }

    private int firstEnd(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            Node node = root;
            for (int j = i; j < lower.length(); j++) {
                node = node.children.get(lower.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.end) {
                    return j;
                }
            }
        }
        return -1;
    }

    private void add(String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.children.computeIfAbsent(word.charAt(i), ignored -> new Node());
        }
        node.end = true;
    }

    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private boolean end;
    }
}
