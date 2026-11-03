package edu.usc.splitsmart.data.contract;

/** Callable names and primary workstream owners for contract v1. */
public enum CallableName {
    COMPLETE_PROFILE("completeProfile", "A"),
    UPDATE_PROFILE_NAME("updateProfileName", "A"),
    CREATE_GROUP("createGroup", "A"),
    ADD_GROUP_MEMBER("addGroupMember", "A"),
    UPDATE_GROUP("updateGroup", "A"),
    DELETE_GROUP("deleteGroup", "A"),
    GET_GROUP_MEMBER_PROFILES("getGroupMemberProfiles", "A"),
    GET_GROUP_LEDGER("getGroupLedger", "C"),
    CREATE_EXPENSE("createExpense", "B"),
    UPDATE_EXPENSE("updateExpense", "B"),
    DELETE_EXPENSE("deleteExpense", "B"),
    CREATE_SETTLEMENT("createSettlement", "D"),
    UPDATE_SETTLEMENT("updateSettlement", "D"),
    DELETE_SETTLEMENT("deleteSettlement", "D");

    private final String wireName;
    private final String owner;

    CallableName(String wireName, String owner) {
        this.wireName = wireName;
        this.owner = owner;
    }

    public String wireName() { return wireName; }
    public String owner() { return owner; }
}
