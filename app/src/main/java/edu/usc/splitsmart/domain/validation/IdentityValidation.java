package edu.usc.splitsmart.domain.validation;

import java.util.Locale;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;

/** Feature validation; backend checks independently. Never includes entered private values in errors. */
public final class IdentityValidation {
    private IdentityValidation() { }
    public static String normalizeEmail(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public static AppError email(String input) {
        String email = normalizeEmail(input);
        return email.length() <= 254 && email.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@usc\\.edu")
            && !email.startsWith(".") && !email.contains("..") && !email.contains(".@") ? null :
            invalid("Use an email address with the exact usc.edu domain.", "email");
    }
    public static AppError name(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 100 ? null :
            invalid("Enter a name of 1–100 characters.", "name");
    }
    public static AppError profile(ProfileDraft value) {
        if (value == null) return invalid("Complete your profile.", "profile");
        AppError name = name(value.name());
        return name != null ? name : value.uscId().matches("[0-9]{10}") ? null :
            invalid("USC ID must contain exactly 10 digits.", "uscId");
    }
    public static AppError password(String value) {
        return value != null && value.length() >= 6 && value.length() <= 4096 ? null :
            invalid("Use a password with at least 6 characters (maximum 4096).", "password");
    }
    public static AppError group(GroupDraft value) {
        if (value == null) return invalid("Enter group details.", "draft");
        AppError name = name(value.name());
        return name != null ? name : value.description() == null || value.description().length() <= 1000 ? null :
            invalid("Description must be at most 1000 characters.", "description");
    }
    public static AppError invalid(String message, String field) { return new AppError(ErrorCode.VALIDATION, message, field, false); }
}
