package com.example.auction.admin.calendar.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum CalendarEventTag {
    OPERATION("운영"),
    INCIDENT("장애"),
    SETTLEMENT("정산"),
    MAINTENANCE("점검"),
    ETC("기타");

    private final String label;

    CalendarEventTag(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static CalendarEventTag from(String value) {
        if (value == null || value.isBlank()) return ETC;
        return Arrays.stream(values())
                .filter(event -> event.label.equals(value) || event.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(ETC);
    }
}
