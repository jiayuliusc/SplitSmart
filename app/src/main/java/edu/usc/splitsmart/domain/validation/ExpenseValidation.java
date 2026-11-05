package edu.usc.splitsmart.domain.validation;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
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
        Set<String> participants = new HashSet<>(value.participantIds());
        if (participants.size() != value.participantIds().size() || participants.contains("")) {
            return invalid("Each selected participant must appear exactly once.", "participantIds");
        }
        if (value.category() == null) {
            return invalid("Select a category.", "category");
        }
        if (value.note() != null && value.note().length() > 1000) {
            return invalid("Note must be at most 1000 characters.", "note");
        }
        return split(value, participants);
    }

    private static AppError split(ExpenseDraft value, Set<String> participants) {
        if (value.splitMethod() == SplitMethod.EQUAL) {
            return value.splitInputs().isEmpty() ? null :
                invalid("Equal splits cannot contain custom split values.", "splitInputs");
        }

        if (value.splitInputs().size() != participants.size()) {
            return invalid("Enter one split value for every selected participant.", "splitInputs");
        }

        Set<String> supplied = new HashSet<>();
        BigInteger exactTotal = BigInteger.ZERO;
        int percentageTotal = 0;
        for (SplitInput input : value.splitInputs()) {
            if (!participants.contains(input.participantId()) || !supplied.add(input.participantId())) {
                return invalid("Each selected participant must have exactly one split value.", "splitInputs");
            }
            if (value.splitMethod() == SplitMethod.EXACT) {
                if (input.amountCents() == null || input.basisPoints() != null || input.amountCents() < 0
                    || input.amountCents() > ContractVersion.MAX_ABSOLUTE_CENTS) {
                    return invalid("Enter a nonnegative exact amount for every participant.", "splitInputs");
                }
                exactTotal = exactTotal.add(BigInteger.valueOf(input.amountCents()));
            } else if (value.splitMethod() == SplitMethod.PERCENTAGE) {
                if (input.basisPoints() == null || input.amountCents() != null
                    || input.basisPoints() < 0 || input.basisPoints() > 10000) {
                    return invalid("Enter a percentage from 0 to 100 for every participant.", "splitInputs");
                }
                percentageTotal += input.basisPoints();
            }
        }

        if (!supplied.equals(participants)) {
            return invalid("Enter one split value for every selected participant.", "splitInputs");
        }
        if (value.splitMethod() == SplitMethod.EXACT
            && !exactTotal.equals(BigInteger.valueOf(value.amountCents()))) {
            return invalid("Exact split amounts must equal the expense total.", "splitInputs");
        }
        if (value.splitMethod() == SplitMethod.PERCENTAGE && percentageTotal != 10000) {
            return invalid("Percentages must total exactly 100%.", "splitInputs");
        }
        return null;
    }

    private static AppError invalid(String message, String field) {
        return new AppError(ErrorCode.VALIDATION, message, field, false);
    }
}
