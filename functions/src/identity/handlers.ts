import {Auth} from "firebase-admin/auth";
import {Firestore, Timestamp, FieldValue} from "firebase-admin/firestore";
import {CallableName, GroupMutationResponse, ProfileMutationResponse, MembersResponse, GroupDeletion} from "../contracts/v1";
import {DomainError, requireThat} from "../infrastructure/errors";
import * as validate from "../infrastructure/validation";
import {mutate, Plan, replay} from "../infrastructure/receipts";
import {authorizedGroup, groupRecord, readyProfile, userRecord} from "../infrastructure/records";

export interface Actor {uid: string; email: string}
export const identityGroupCalls = ["completeProfile", "updateProfileName", "createGroup",
  "addGroupMember", "updateGroup", "deleteGroup", "getGroupMemberProfiles"] as const;
export type IdentityGroupCall = typeof identityGroupCalls[number];

/** SDK-neutral actor is constructed only by the callable authentication boundary. */
export async function handle(db: Firestore, auth: Auth, actor: Actor, operation: IdentityGroupCall,
  raw: unknown): Promise<unknown> {
  validate.id(actor.uid, "uid");
  const actorEmail = validate.email(actor.email);
  if (operation === "completeProfile") {
    const data = validate.envelope(raw, ["profile"]);
    const draft = validate.profile(data.profile);
    return mutate<ProfileMutationResponse>(db, actor.uid, operation, data, async tx => {
      const ref = db.doc(`users/${actor.uid}`);
      const claim = db.doc(`uscIdClaims/${draft.uscId}`);
      const existing = await tx.get(ref);
      const reserved = await tx.get(claim);
      if (existing.exists) {
        requireThat(existing.get("uscId") === draft.uscId && existing.get("email") === actorEmail,
          "ALREADY_EXISTS", "This account already has a completed profile.");
        return {response: {schemaVersion: 1, requestId: data.requestId as string,
          value: userRecord(actor.uid, existing.data()!)}, write: () => {}};
      }
      requireThat(!reserved.exists || reserved.get("uid") === actor.uid,
        "ALREADY_EXISTS", "This USC ID is already registered.", "uscId");
      const now = Timestamp.now();
      const stored = {schemaVersion: 1, ...draft, email: actorEmail, createdAt: now, updatedAt: now};
      return {response: {schemaVersion: 1, requestId: data.requestId as string, value: userRecord(actor.uid, stored)},
        write: transaction => {
          transaction.create(ref, stored);
          if (!reserved.exists) transaction.create(claim, {schemaVersion: 1, uid: actor.uid, createdAt: now});
        }};
    });
  }
  if (operation === "updateProfileName") {
    const data = validate.envelope(raw, ["name"]);
    const name = validate.string(data.name, "name").trim();
    return mutate<ProfileMutationResponse>(db, actor.uid, operation, data, async tx => {
      const current = await readyProfile(db, tx, actor.uid);
      const updatedAt = Timestamp.now();
      const stored = {...current.data()!, name, updatedAt};
      return {response: {schemaVersion: 1, requestId: data.requestId as string, value: userRecord(actor.uid, stored)},
        write: transaction => { transaction.update(current.ref, {name, updatedAt}); }};
    });
  }
  if (operation === "createGroup") {
    const data = validate.envelope(raw, ["draft"]);
    const draft = validate.groupDraft(data.draft);
    const ref = db.collection("groups").doc();
    return mutate(db, actor.uid, operation, data, async tx => {
      await readyProfile(db, tx, actor.uid);
      const now = Timestamp.now();
      const stored = {schemaVersion: 1, ...draft, ownerId: actor.uid, memberIds: [actor.uid],
        revision: 1, status: "ACTIVE", createdAt: now, updatedAt: now};
      return {response: {schemaVersion: 1, requestId: data.requestId, groupRevision: "1", value: groupRecord(ref.id, stored)},
        write: transaction => { transaction.create(ref, stored); }};
    });
  }
  if (operation === "getGroupMemberProfiles") {
    const data = validate.envelope(raw, ["groupId"], false);
    const groupId = validate.id(data.groupId);
    return db.runTransaction(async tx => {
      const group = await authorizedGroup(db, tx, actor.uid, groupId, false);
      const ids: string[] = [...group.get("memberIds")].sort();
      const members = await tx.getAll(...ids.map(uid => db.doc(`users/${uid}`)));
      requireThat(members.every(m => m.exists && m.get("schemaVersion") === 1), "INTERNAL", "A member profile is unavailable.");
      return {schemaVersion: 1, members: members.map(m => ({uid: m.id, name: m.get("name"), email: m.get("email")}))} satisfies MembersResponse;
    }, {maxAttempts: 5});
  }
  const fields = ["groupId", "expectedRevision", ...(operation === "addGroupMember" ? ["email"] :
    operation === "updateGroup" ? ["draft"] : [])];
  const data = validate.envelope(raw, fields);
  const groupId = validate.id(data.groupId);
  const expected = validate.revision(data.expectedRevision);
  const draft = operation === "updateGroup" ? validate.groupDraft(data.draft) : undefined;
  const memberEmail = operation === "addGroupMember" ? validate.email(data.email) : undefined;
  // External Auth I/O must not run inside the Firestore transaction/retry callback.
  let memberUid: string | undefined;
  if (memberEmail !== undefined) {
    const saved = await replay(db, actor.uid, operation, data);
    if (saved !== null) return saved.value;
    try {
      const user = await auth.getUserByEmail(memberEmail);
      if (!user.disabled) memberUid = user.uid;
    } catch (error) {
      if ((error as {code?: string}).code !== "auth/user-not-found") throw error;
    }
  }
  return mutate(db, actor.uid, operation as CallableName, data, async tx => {
    const group = await authorizedGroup(db, tx, actor.uid, groupId, true, expected);
    const now = Timestamp.now();
    const revision = expected + 1;
    if (operation === "deleteGroup") {
      const job = db.doc(`groupDeletionJobs/${groupId}`);
      const priorJob = await tx.get(job);
      requireThat(!priorJob.exists, "INTERNAL", "A deletion job already exists.");
      return {response: {schemaVersion: 1, requestId: data.requestId as string, groupRevision: String(revision) as `${bigint}`,
        value: {groupId, status: "DELETING"}} satisfies GroupMutationResponse<GroupDeletion>,
      write: transaction => {
        transaction.update(group.ref, {status: "DELETING", revision, updatedAt: now});
        transaction.create(job, {schemaVersion: 1, ownerId: actor.uid, status: "DELETING", createdAt: now, updatedAt: now});
      }} as Plan<unknown>;
    }
    let patch: FirebaseFirestore.UpdateData<FirebaseFirestore.DocumentData>;
    if (operation === "addGroupMember") {
      requireThat(memberUid !== undefined, "NOT_FOUND", "No completed account was found for that USC email.", "email");
      const member = await tx.get(db.doc(`users/${memberUid}`));
      requireThat(member.exists && member.get("schemaVersion") === 1 && member.get("email") === memberEmail,
        "NOT_FOUND", "No completed account was found for that USC email.", "email");
      requireThat(!group.get("memberIds").includes(memberUid), "ALREADY_EXISTS", "This user is already a member.", "email");
      patch = {memberIds: [...group.get("memberIds"), memberUid].sort(), revision, updatedAt: now};
    } else {
      requireThat(draft !== undefined, "INTERNAL", "Missing group draft.");
      patch = {name: draft.name, description: draft.description ?? FieldValue.delete(), revision, updatedAt: now};
    }
    const stored = {...group.data()!, ...patch};
    if (operation === "updateGroup" && draft?.description === undefined) delete stored.description;
    return {response: {schemaVersion: 1, requestId: data.requestId, groupRevision: String(revision), value: groupRecord(groupId, stored)},
      write: transaction => { transaction.update(group.ref, patch); }};
  });
}

export function actorFromAuth(auth: {uid: string; token: Record<string, unknown>} | undefined): Actor {
  requireThat(auth !== undefined && typeof auth.token.email === "string", "UNAUTHENTICATED", "Sign in to continue.");
  try { return {uid: validate.id(auth.uid, "uid"), email: validate.email(auth.token.email)}; }
  catch { throw new DomainError("PERMISSION_DENIED", "A USC email account is required."); }
}
