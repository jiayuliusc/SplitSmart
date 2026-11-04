/** User/group and financial v1 callables and durable deletion worker. */
import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {getFirestore} from "firebase-admin/firestore";
import {onCall} from "firebase-functions/v2/https";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {actorFromAuth, handle, IdentityGroupCall} from "./identity/handlers";
import {handleExpenseCall, FinancialCall} from "./expenses/handlers";
import {httpsError} from "./infrastructure/errors";
import {finishDeletion} from "./groups/cleanup";

initializeApp();
const db = getFirestore();
const auth = getAuth();
function callable(operation: IdentityGroupCall) {
  return onCall({region: "us-central1", timeoutSeconds: 60, memory: "512MiB"}, async request => {
    try { return await handle(db, auth, actorFromAuth(request.auth), operation, request.data); }
    catch (error) { throw httpsError(error); }
  });
}

function expenseCallable(operation: FinancialCall) {
  return onCall({region: "us-central1", timeoutSeconds: 60, memory: "512MiB"}, async request => {
    try { return await handleExpenseCall(db, auth, actorFromAuth(request.auth), operation, request.data); }
    catch (error) { throw httpsError(error); }
  });
}

export const completeProfile = callable("completeProfile");
export const updateProfileName = callable("updateProfileName");
export const createGroup = callable("createGroup");
export const addGroupMember = callable("addGroupMember");
export const updateGroup = callable("updateGroup");
export const deleteGroup = callable("deleteGroup");
export const getGroupMemberProfiles = callable("getGroupMemberProfiles");

export const createExpense = expenseCallable("createExpense");
export const updateExpense = expenseCallable("updateExpense");
export const deleteExpense = expenseCallable("deleteExpense");
export const createSettlement = expenseCallable("createSettlement");
export const updateSettlement = expenseCallable("updateSettlement");
export const deleteSettlement = expenseCallable("deleteSettlement");
export const getGroupLedger = expenseCallable("getGroupLedger");

export const cleanupDeletedGroup = onDocumentCreated({document: "groupDeletionJobs/{groupId}",
  region: "us-central1", retry: true, timeoutSeconds: 540, memory: "512MiB"},
async event => { await finishDeletion(db, event.params.groupId); });
