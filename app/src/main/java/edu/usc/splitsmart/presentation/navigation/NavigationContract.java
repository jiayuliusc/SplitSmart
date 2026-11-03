package edu.usc.splitsmart.presentation.navigation;

/** Intent/result keys only. Activities and ViewModel factories are implemented by features. */
public final class NavigationContract {
    public static final String GROUP_ID = "edu.usc.splitsmart.groupId";
    public static final String EXPENSE_ID = "edu.usc.splitsmart.expenseId";
    public static final String SETTLEMENT_ID = "edu.usc.splitsmart.settlementId";
    public static final String RESULT_REQUEST_ID = "edu.usc.splitsmart.result.requestId";
    public static final String RESULT_GROUP_ID = "edu.usc.splitsmart.result.groupId";
    public static final String RESULT_GROUP_REVISION = "edu.usc.splitsmart.result.groupRevision";

    private NavigationContract() {}
}
