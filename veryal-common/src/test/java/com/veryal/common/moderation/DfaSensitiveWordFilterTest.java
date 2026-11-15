package com.veryal.common.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DfaSensitiveWordFilterTest {

    @Test
    void detectsAndMasksSensitiveWords() {
        DfaSensitiveWordFilter filter = new DfaSensitiveWordFilter(List.of("badword", "敏感词"));

        assertThat(filter.contains("this is badword")).isTrue();
        assertThat(filter.contains("这里包含敏感词")).isTrue();
        assertThat(filter.contains("clean text")).isFalse();
        assertThat(filter.replace("badword and 敏感词")).isEqualTo("******* and ***");
        assertThat(filter.findAll("badword 敏感词")).containsExactly("badword", "敏感词");
    }

    @Test
    void handlesCaseInsensitiveLatinWords() {
        DfaSensitiveWordFilter filter = new DfaSensitiveWordFilter(List.of("Spam"));
        assertThat(filter.contains("SPAM")).isTrue();
        assertThat(filter.replace("SpAm")).isEqualTo("****");
    }

    @Test
    void ignoresBlankWords() {
        DfaSensitiveWordFilter filter = new DfaSensitiveWordFilter(List.of("", "  ", "ok"));
        assertThat(filter.contains("ok")).isTrue();
        assertThat(filter.contains("fine")).isFalse();
    }
}
