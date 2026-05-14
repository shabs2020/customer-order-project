package com.customer.order.service.database.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderState {
    DRAFT("draft"),
    PREVIEW("preview"),
    SUBMITTED("submitted"),
    CONFIRMED("confirmed");

    @JsonValue
    private final String value;

    // Optional: Helper to find Enum by string value
    public static OrderState fromValue(String text) {
        for (OrderState b : OrderState.values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }

    public boolean checkTransition(OrderState nextState) {
        return switch (this) {
            case DRAFT -> nextState == PREVIEW;
            case PREVIEW -> nextState == DRAFT || nextState == SUBMITTED;
            case SUBMITTED -> nextState == CONFIRMED;
            case CONFIRMED -> false; // Terminal state
        };
    }
}
