package com.example.auction.common.validation.annotation;

import com.example.auction.common.validation.validator.ThousandUnitValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ThousandUnitValidator.class)
public @interface ThousandUnit {
    String message() default "금액은 1000원 단위여야 합니다.";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
