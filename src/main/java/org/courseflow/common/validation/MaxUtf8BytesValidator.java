package org.courseflow.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {
    private int maximum;

    @Override
    public void initialize(MaxUtf8Bytes constraint) {
        maximum = constraint.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PasswordBytes.fits(value, maximum);
    }
}
