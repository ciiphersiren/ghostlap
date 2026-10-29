package com.ghostlap.race.validation;

import com.ghostlap.race.lap.RejectReason;

/**
 * Outcome of LapValidator.
 *
 * @param reason     null if the lap was accepted
 * @param detail     what exactly was wrong (for logs and the database), null if accepted
 * @param ghostBytes the decoded ghost file, only set for accepted laps
 */
public record ValidationResult(RejectReason reason, String detail, byte[] ghostBytes) {

    public static ValidationResult accepted(byte[] ghostBytes) {
        return new ValidationResult(null, null, ghostBytes);
    }

    public static ValidationResult rejected(RejectReason reason, String detail) {
        return new ValidationResult(reason, detail, null);
    }

    public boolean isAccepted() {
        return reason == null;
    }
}
