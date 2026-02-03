package com.example.auction.common.validation.validator;

import com.example.auction.common.validation.annotation.ThousandUnit;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ThousandUnitValidator implements ConstraintValidator<ThousandUnit, Long> {

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        if (value == null) return true; // null 체크는 @NotNull이 담당
        return value >= 1000 && value % 1000 == 0;
    }
}
