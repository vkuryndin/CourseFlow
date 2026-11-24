package org.courseflow.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.courseflow.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class PasswordBytesTest {

    @Test
    void acceptsExactly72AsciiBytes() {
        assertThat(PasswordBytes.fits("a".repeat(72))).isTrue();
    }

    @Test
    void rejects73AsciiBytes() {
        assertThat(PasswordBytes.fits("a".repeat(73))).isFalse();
    }

    @Test
    void countsUtf8BytesInsteadOfCharacters() {
        assertThat(PasswordBytes.fits("\u044f".repeat(36))).isTrue();
        assertThat(PasswordBytes.fits("\u044f".repeat(37))).isFalse();
    }

    @Test
    void acceptsPasswordWithinLimit() {
        assertThatCode(() -> PasswordBytes.requireFits("a".repeat(72), "Password")).doesNotThrowAnyException();
    }

    @Test
    void rejectsOversizedPassword() {
        assertThatThrownBy(() -> PasswordBytes.requireFits("a".repeat(73),
                "Password")).isInstanceOf(BadRequestException.class)
                .hasMessage("Password must not exceed 72 UTF-8 bytes");
    }
}
