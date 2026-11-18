package edu.usc.splitsmart.registration;

import static org.junit.Assert.*;
import edu.usc.splitsmart.domain.contract.ErrorCode;
import edu.usc.splitsmart.domain.model.GroupDraft;
import edu.usc.splitsmart.domain.model.ProfileDraft;
import edu.usc.splitsmart.domain.validation.IdentityValidation;
import org.junit.Test;

/** Branch-focused tests for the Feature 1 input-validation paths. */
public final class IdentityFeatureWhiteBoxTest {
    @Test public void emailNormalizationAndExactDomain() {
        assertNull(IdentityValidation.email("  Student.Name@USC.EDU  "));
        assertEquals(ErrorCode.VALIDATION, IdentityValidation.email("student@sub.usc.edu").code());
        assertEquals(ErrorCode.VALIDATION, IdentityValidation.email("student@usc.edu.attacker.org").code());
    }

    @Test public void uscIdRequiresTenAsciiDigitsAndPreservesLeadingZeroCase() {
        assertNull(IdentityValidation.profile(new ProfileDraft("Student", "0012345678")));
        assertEquals(ErrorCode.VALIDATION, IdentityValidation.profile(new ProfileDraft("Student", "123456789" )).code());
        assertEquals(ErrorCode.VALIDATION, IdentityValidation.profile(new ProfileDraft("Student", "１２３４５６７８９０")).code());
    }

    @Test public void requiredNamesRejectBlankAndOverLimitValues() {
        assertNotNull(IdentityValidation.name(" \t "));
        assertNotNull(IdentityValidation.name("x".repeat(101)));
        assertNull(IdentityValidation.name("x".repeat(100)));
    }

    @Test public void passwordBoundsAreInclusiveAtSixAnd4096Characters() {
        assertNotNull(IdentityValidation.password("12345"));
        assertNull(IdentityValidation.password("123456"));
        assertNull(IdentityValidation.password("x".repeat(4096)));
        assertNotNull(IdentityValidation.password("x".repeat(4097)));
    }

    @Test public void groupDraftAllowsOmittedDescriptionButBoundsProvidedDescription() {
        assertNull(IdentityValidation.group(new GroupDraft("Roommates", null)));
        assertNull(IdentityValidation.group(new GroupDraft("Roommates", "x".repeat(1000))));
        assertEquals(ErrorCode.VALIDATION, IdentityValidation.group(new GroupDraft("Roommates", "x".repeat(1001))).code());
    }
}
