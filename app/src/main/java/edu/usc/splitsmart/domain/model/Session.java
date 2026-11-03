package edu.usc.splitsmart.domain.model;

/** Contract v1. Signed out is (null, null); a signed-in session has both UID and email. */
public record Session(
        String uid,
        String email) {
}
