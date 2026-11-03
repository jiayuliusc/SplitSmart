import {Firestore, FieldPath, Timestamp} from "firebase-admin/firestore";
import {MAX_REVISION} from "../contracts/v1";
import {requireThat} from "../infrastructure/errors";

/** One restart-safe unit of deletion; tests and the retry-enabled event worker use this same code. */
export async function cleanupBatch(db: Firestore, groupId: string): Promise<boolean> {
  return db.runTransaction(async tx => {
    const ref = db.doc(`groups/${groupId}`);
    const jobRef = db.doc(`groupDeletionJobs/${groupId}`);
    const job = await tx.get(jobRef);
    const group = await tx.get(ref);
    if (!job.exists || job.get("status") === "DELETED") return true;
    requireThat(job.get("status") === "DELETING" && job.get("schemaVersion") === 1,
      "INTERNAL", "Invalid deletion job.");
    requireThat(group.exists && group.get("status") === "DELETING" && group.get("ownerId") === job.get("ownerId"),
      "INTERNAL", "Deletion marker and group do not match.");
    const expenses = await tx.get(ref.collection("expenses").orderBy(FieldPath.documentId()).limit(200));
    const settlements = await tx.get(ref.collection("settlements").orderBy(FieldPath.documentId()).limit(200));
    const docs = [...expenses.docs, ...settlements.docs].slice(0, 200);
    const now = Timestamp.now();
    if (docs.length === 0) {
      tx.delete(ref);
      tx.update(jobRef, {status: "DELETED", updatedAt: now});
      return true;
    }
    const revision = group.get("revision") as number;
    requireThat(BigInt(revision) < MAX_REVISION, "OUT_OF_RANGE", "Group revision is exhausted.");
    for (const child of docs) tx.delete(child.ref);
    tx.update(ref, {revision: revision + 1, updatedAt: now});
    tx.update(jobRef, {updatedAt: now});
    return false;
  }, {maxAttempts: 5});
}

export async function finishDeletion(db: Firestore, groupId: string): Promise<void> {
  // Bound a single delivery. A failure retries the durable creation event, resuming remaining children.
  for (let batch = 0; batch < 100; batch++) if (await cleanupBatch(db, groupId)) return;
  throw new Error("Deletion will resume in the next event delivery.");
}
