package com.evehealthcare.diagnostics.dto;

/** result is one of PROCESSED, NO_CHANGE, IGNORED, DUPLICATE. */
public record WebhookResponse(String eventId, String result, String message) {
    public static WebhookResponse duplicate(String eventId, String originalOutcome) {
        return new WebhookResponse(eventId, "DUPLICATE",
                "Event already received (original outcome: " + originalOutcome + "); no changes made");
    }
}
