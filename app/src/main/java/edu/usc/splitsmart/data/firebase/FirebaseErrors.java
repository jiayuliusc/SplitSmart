package edu.usc.splitsmart.data.firebase;

import java.util.Map;
import java.util.Set;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.functions.FirebaseFunctionsException;
import edu.usc.splitsmart.domain.contract.*;

final class FirebaseErrors {
    private FirebaseErrors() { }
    static AppError error(ErrorCode code, String message) {
        return new AppError(code, message, null, code == ErrorCode.UNAVAILABLE || code == ErrorCode.DEADLINE_EXCEEDED);
    }
    static AppError map(Exception exception) {
        if (exception instanceof FirebaseFunctionsException f) {
            if (f.getDetails() instanceof Map<?, ?> details && details.get("schemaVersion") instanceof Number version && version.doubleValue() == 1) {
                try {
                    ErrorCode code = ErrorCode.valueOf(String.valueOf(details.get("code")));
                    String field = details.get("field") instanceof String value &&
                        Set.of("name", "email", "uscId", "description", "groupId", "expectedRevision", "requestId").contains(value) ? value : null;
                    String text = message(code);
                    if (code == ErrorCode.ALREADY_EXISTS && "uscId".equals(field)) text = "This USC ID is already registered. Check your ID or sign in to your existing account.";
                    if (code == ErrorCode.ALREADY_EXISTS && "email".equals(field)) text = "This user is already a member of the group.";
                    if (code == ErrorCode.NOT_FOUND && "email".equals(field)) text = "No completed SplitSmart account was found for this USC email.";
                    return new AppError(code, text, field, code == ErrorCode.UNAVAILABLE || code == ErrorCode.DEADLINE_EXCEEDED);
                } catch (IllegalArgumentException ignored) { return error(ErrorCode.INTERNAL, message(ErrorCode.INTERNAL)); }
            }
            return transport(f.getCode().name());
        }
        if (exception instanceof FirebaseFirestoreException f) return transport(f.getCode().name());
        if (exception instanceof FirebaseNetworkException) return transport("UNAVAILABLE");
        if (exception instanceof FirebaseTooManyRequestsException) return transport("RESOURCE_EXHAUSTED");
        if (exception instanceof FirebaseAuthException auth) {
            return switch (auth.getErrorCode()) {
                case "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> error(ErrorCode.ALREADY_EXISTS, "This email is already registered. Sign in to continue or reset your password.");
                case "ERROR_WEAK_PASSWORD" -> error(ErrorCode.VALIDATION, "Choose a stronger password that meets the account policy.");
                case "ERROR_INVALID_EMAIL" -> error(ErrorCode.VALIDATION, "Check your USC email address.");
                case "ERROR_TOO_MANY_REQUESTS" -> transport("RESOURCE_EXHAUSTED");
                default -> error(ErrorCode.UNAUTHENTICATED, "Could not sign in. Check your email and password, or reset your password.");
            };
        }
        return error(ErrorCode.INTERNAL, message(ErrorCode.INTERNAL));
    }
    private static AppError transport(String name) {
        ErrorCode code;
        try { code = ErrorCode.valueOf(name); }
        catch (IllegalArgumentException ignored) { code = name.equals("ABORTED") ? ErrorCode.UNAVAILABLE : ErrorCode.INTERNAL; }
        return error(code, message(code));
    }
    static String message(ErrorCode code) {
        return switch (code) {
            case VALIDATION -> "Check the entered details and try again.";
            case UNAUTHENTICATED -> "Please sign in again.";
            case PERMISSION_DENIED -> "You no longer have permission for this action.";
            case NOT_FOUND -> "The requested profile or group was not found.";
            case ALREADY_EXISTS -> "This USC ID or group membership already exists.";
            case CONFLICT -> "The group changed. Refresh its details before trying again.";
            case IDEMPOTENCY_MISMATCH -> "This request changed during retry. Refresh before starting a new action.";
            case GROUP_DELETING -> "This group is being deleted.";
            case CACHE_MISS -> "No saved copy is available. Refresh while online.";
            case UNAVAILABLE, DEADLINE_EXCEEDED -> "Connection interrupted. Retry to recover the result of this request.";
            case RESOURCE_EXHAUSTED -> "Too many requests or capacity reached. Try again later.";
            case OUT_OF_RANGE -> "This request exceeds the supported range.";
            default -> "The operation could not be completed. Please try again later.";
        };
    }
}
