package edu.usc.splitsmart.domain.validation;

import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

public final class ExpenseValidation {
    private ExpenseValidation() { }

    public static AppError title(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 100 ? null :
            invalid("Enter a title of 1–100 characters.", "title");
    }

    public static AppError amount(long amountCents) {
        return amountCents > 0 && amountCents <= ContractVersion.MAX_ABSOLUTE_CENTS ? null :
            invalid("Enter a positive total amount.", "amountCents");
    }

    public static AppError expense(ExpenseDraft value) {
        if (value == null) return invalid("Enter expense details.", "draft");
        AppError titleErr = title(value.title());
        if (titleErr != null) return titleErr;
        AppError amtErr = amount(value.amountCents());
        if (amtErr != null) return amtErr;
        if (value.payerId() == null || value.payerId().trim().isEmpty()) {
            return invalid("Select a payer.", "payerId");
        }
        if (value.participantIds() == null || value.participantIds().size() < 2 || !value.participantIds().contains(value.payerId())) {
            return invalid("Select at least 2 participants including the payer.", "participantIds");
        }
        if (value.note() != null && value.note().length() > 1000) {
            return invalid("Note must be at most 1000 characters.", "note");
        }
        return null;
    }

    private static AppError invalid(String message, String field) {
        return new AppError(ErrorCode.VALIDATION, message, field, false);
    }
}
