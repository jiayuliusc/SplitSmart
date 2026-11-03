package edu.usc.splitsmart.domain.contract;

/** Stable v1 codes. Retry policy and Firebase error mapping are in docs/CONTRACTS.md. */
public enum ErrorCode {
    VALIDATION, UNAUTHENTICATED, PERMISSION_DENIED, NOT_FOUND, CACHE_MISS,
    ALREADY_EXISTS, CONFLICT, IDEMPOTENCY_MISMATCH, GROUP_DELETING,
    OUT_OF_RANGE, RESOURCE_EXHAUSTED, UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL
}
