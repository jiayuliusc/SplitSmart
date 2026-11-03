import {DocumentSnapshot, Timestamp, Transaction, Firestore} from "firebase-admin/firestore";
import {User, Group, MAX_REVISION} from "../contracts/v1";
import {requireThat} from "./errors";

export function time(value: Timestamp): {seconds: number; nanoseconds: number} {
  return {seconds: value.seconds, nanoseconds: value.nanoseconds};
}

export function userRecord(uid: string, data: FirebaseFirestore.DocumentData): User {
  requireThat(data.schemaVersion === 1, "INTERNAL", "Unsupported profile version.");
  return {uid, name: data.name, email: data.email, uscId: data.uscId,
    createdAt: time(data.createdAt), updatedAt: time(data.updatedAt)};
}

export function groupRecord(groupId: string, data: FirebaseFirestore.DocumentData): Group {
  requireThat(data.schemaVersion === 1, "INTERNAL", "Unsupported group version.");
  return {groupId, name: data.name, ...(data.description === undefined ? {} : {description: data.description}),
    ownerId: data.ownerId, memberIds: [...data.memberIds].sort(), revision: String(data.revision) as `${bigint}`,
    status: data.status, createdAt: time(data.createdAt), updatedAt: time(data.updatedAt)};
}

export async function readyProfile(db: Firestore, tx: Transaction, uid: string): Promise<DocumentSnapshot> {
  const profile = await tx.get(db.doc(`users/${uid}`));
  requireThat(profile.exists, "NOT_FOUND", "Complete your profile before continuing.");
  requireThat(profile.get("schemaVersion") === 1, "INTERNAL", "Unsupported profile version.");
  return profile;
}

export async function authorizedGroup(db: Firestore, tx: Transaction, uid: string, groupId: string,
  owner: boolean, expected?: number): Promise<DocumentSnapshot> {
  const profile = await tx.get(db.doc(`users/${uid}`));
  const group = await tx.get(db.doc(`groups/${groupId}`));
  requireThat(profile.exists, "NOT_FOUND", "Complete your profile before continuing.");
  requireThat(group.exists, "NOT_FOUND", "This group no longer exists.");
  requireThat(group.get("schemaVersion") === 1 && profile.get("schemaVersion") === 1, "INTERNAL", "Unsupported record version.");
  requireThat(group.get("memberIds").includes(uid), "PERMISSION_DENIED", "You do not have access to this group.");
  requireThat(!owner || group.get("ownerId") === uid, "PERMISSION_DENIED", "Only the group owner can make this change.");
  requireThat(group.get("status") === "ACTIVE", "GROUP_DELETING", "This group is being deleted.");
  if (expected !== undefined) {
    requireThat(group.get("revision") === expected, "CONFLICT", "The group changed. Refresh before trying again.");
    requireThat(BigInt(expected) < MAX_REVISION, "OUT_OF_RANGE", "Group revision is exhausted.");
  }
  return group;
}
