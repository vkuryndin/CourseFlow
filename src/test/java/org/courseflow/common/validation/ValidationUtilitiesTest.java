package org.courseflow.common.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class ValidationUtilitiesTest {

    @Test
    void emailNormalization() {
        assertEquals("alice@example.test", EmailAddresses.normalize("  ALICE@EXAMPLE.TEST "));
    }

    @Test
    void optionalUrlAcceptsOnlyAbsoluteHttpAndHttps() {
        assertNull(HttpUrls.optional(null, "url"));
        assertNull(HttpUrls.optional("  ", "url"));
        assertEquals("https://example.test/a", HttpUrls.optional(" https://example.test/a ", "url"));
        assertEquals("http://localhost:8080/a", HttpUrls.optional("http://localhost:8080/a", "url"));
    }

    @Test
    void invalidUrlsRejected() {
        for (String s : List.of("javascript:alert(1)", "file:///etc/passwd", "//example.test/a", "https:/path",
                "https://user@example.test/a", "https://exa mple.test")) {
            assertThrows(BadRequestException.class, () -> HttpUrls.optional(s, "url"), s);
        }
    }

    @Test
    void byteLimitValidatorSupportsNullAndMultibyte() {
        MaxUtf8Bytes config = mock(MaxUtf8Bytes.class);
        when(config.value()).thenReturn(4);
        MaxUtf8BytesValidator validator = new MaxUtf8BytesValidator();
        validator.initialize(config);

        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("\u044f\u044f", null));
        assertFalse(validator.isValid("\u044f\u044fa", null));
        assertTrue(PasswordBytes.fits("\ud83d\ude00", 4));
        assertFalse(PasswordBytes.fits("\ud83d\ude00", 3));
    }

    @Test
    void requireFitsRejectsLongPassword() {
        assertDoesNotThrow(() -> PasswordBytes.requireFits("a".repeat(72), "Password"));
        assertThrows(BadRequestException.class, () -> PasswordBytes.requireFits("a".repeat(73), "Password"));
    }
}
