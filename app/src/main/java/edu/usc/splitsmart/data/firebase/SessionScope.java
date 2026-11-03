package edu.usc.splitsmart.data.firebase;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.google.firebase.auth.FirebaseAuth;

/** Caches are memory-only, account-scoped, and dropped synchronously on observed session changes. */
final class SessionScope {
    private final FirebaseAuth auth;
    private String uid;
    private long generation;
    final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
    SessionScope(FirebaseAuth auth) {
        this.auth = auth;
        this.uid = auth.getUid();
        auth.addAuthStateListener(ignored -> sync());
    }
    private synchronized void sync() {
        String next = auth.getUid();
        if (!Objects.equals(uid, next)) { generation++; uid = next; cache.clear(); }
    }
    synchronized String uid() { sync(); return uid; }
    synchronized long generation() { sync(); return generation; }
    synchronized boolean current(String expected, long epoch) { sync(); return Objects.equals(uid, expected) && generation == epoch; }
}
