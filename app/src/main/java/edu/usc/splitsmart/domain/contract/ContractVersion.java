package edu.usc.splitsmart.domain.contract;

/** Shared v1 representation bounds, not calculation implementations. */
public final class ContractVersion {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_ABSOLUTE_CENTS = 9_007_199_254_740_991L;
    public static final long MAX_REVISION = 9_007_199_254_740_991L;
    public static final int TOTAL_BASIS_POINTS = 10_000;
    public static final String CURRENCY = "USD";

    private ContractVersion() {}
}
