package edu.usc.splitsmart.data.firebase;

import java.util.Map;
import com.google.firebase.firestore.Source;
import edu.usc.splitsmart.data.repository.UserRepository;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.util.PendingOperation;

public final class FirebaseUserRepository implements UserRepository {
    private final FirebaseRepositoryContext context;
    public FirebaseUserRepository(FirebaseRepositoryContext context) { this.context = context; }
    @Override public AsyncOperation getCurrentProfile(ReadPolicy policy, Callback<ReadResult<User>> callback) {
        PendingOperation<ReadResult<User>> op = context.operation(callback);
        String uid = context.scope.uid();
        if (uid == null || context.cached("profile", policy, op)) return op;
        Callback<ReadResult<User>> result = context.cache("profile", op);
        context.db.collection("users").document(uid).get(Source.SERVER).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) result.onError(FirebaseErrors.map(task.getException()));
            else if (!task.getResult().exists()) result.onError(FirebaseErrors.error(ErrorCode.NOT_FOUND, "Complete your profile to continue."));
            else try { result.onSuccess(new ReadResult<>(IdentityCodec.user(task.getResult()), ReadSource.SERVER)); }
            catch (RuntimeException invalid) { result.onError(FirebaseErrors.error(ErrorCode.INTERNAL, "Unsupported profile data.")); }
        });
        return op;
    }
    @Override public AsyncOperation completeProfile(ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback) {
        return context.call("completeProfile", Map.of("schemaVersion", 1, "requestId", requestId,
            "profile", Map.of("name", profile.name(), "uscId", profile.uscId())),
            value -> context.mutation(value, requestId, false, IdentityCodec::user), callback);
    }
    @Override public AsyncOperation updateName(String name, String requestId, Callback<MutationResult<User>> callback) {
        return context.call("updateProfileName", Map.of("schemaVersion", 1, "requestId", requestId, "name", name),
            value -> context.mutation(value, requestId, false, IdentityCodec::user), callback);
    }
}
