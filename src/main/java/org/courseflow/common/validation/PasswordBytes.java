package org.courseflow.common.validation;

import java.nio.charset.StandardCharsets;
import org.courseflow.common.exception.BadRequestException;

/** BCrypt accepts no more than 72 UTF-8 bytes, not 72 Unicode characters. */
public final class PasswordBytes {
    private static final int MAX_BYTES = 72;
    private PasswordBytes() {}

    public static boolean fits(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    public static void requireFits(String password, String label) {
        if (!fits(password)) {
            throw new BadRequestException(label + " must not exceed 72 UTF-8 bytes");
        }
    }
}
