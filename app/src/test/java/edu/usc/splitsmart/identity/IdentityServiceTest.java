package edu.usc.splitsmart.identity;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import edu.usc.splitsmart.data.repository.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.domain.validation.IdentityValidation;
import edu.usc.splitsmart.service.identity.DefaultAuthService;
import edu.usc.splitsmart.util.PendingOperation;

public final class IdentityServiceTest {
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private final Executor executor = queue::add;
    private final FakeAuth auth = new FakeAuth();
    private final FakeUsers users = new FakeUsers();
    private final DefaultAuthService service = new DefaultAuthService(auth, users, executor);
    private void drain() { while (!queue.isEmpty()) queue.remove().run(); }
    private static final class Result<T> implements Callback<T> {
        T value; AppError error; int calls;
        @Override public void onSuccess(T value) { this.value = value; calls++; }
        @Override public void onError(AppError error) { this.error = error; calls++; }
    }
    private <T> AsyncOperation success(T value, Callback<T> callback) {
        PendingOperation<T> op = new PendingOperation<>(executor, callback); op.onSuccess(value); return op;
    }
    private final class FakeAuth implements AuthRepository {
        Session session = new Session(null, null); int registrations; int resets; String resetEmail; AppError resetError;
        @Override public AsyncOperation getSession(Callback<Session> callback) { return success(session, callback); }
        @Override public AsyncOperation observeSession(SessionObserver observer) { executor.execute(() -> observer.onSessionChanged(session)); return () -> {}; }
        @Override public AsyncOperation signUp(String email, String password, Callback<Session> callback) {
            registrations++; session = new Session("u", email); return success(session, callback);
        }
        @Override public AsyncOperation signIn(String email, String password, Callback<Session> callback) { session = new Session("u", email); return success(session, callback); }
        @Override public AsyncOperation signOut(Callback<Void> callback) { session = new Session(null, null); return success(null, callback); }
        @Override public AsyncOperation sendPasswordResetEmail(String email, Callback<Void> callback) {
            resets++; resetEmail = email; PendingOperation<Void> op = new PendingOperation<>(executor, callback);
            if (resetError == null) op.onSuccess(null); else op.onError(resetError); return op;
        }
    }
    private final class FakeUsers implements UserRepository {
        boolean fail = true; int completions; String key;
        @Override public AsyncOperation getCurrentProfile(ReadPolicy policy, Callback<ReadResult<User>> callback) {
            PendingOperation<ReadResult<User>> op = new PendingOperation<>(executor, callback);
            op.onError(new AppError(ErrorCode.NOT_FOUND, "Missing", null, false)); return op;
        }
        @Override public AsyncOperation completeProfile(ProfileDraft profile, String requestId, Callback<MutationResult<User>> callback) {
            completions++; key = requestId;
            PendingOperation<MutationResult<User>> op = new PendingOperation<>(executor, callback);
            if (fail) op.onError(new AppError(ErrorCode.UNAVAILABLE, "Offline", null, true));
            else op.onSuccess(new MutationResult<>(requestId, null, new User("u", profile.name(), auth.session.email(), profile.uscId(), Instant.EPOCH, Instant.EPOCH)));
            return op;
        }
        @Override public AsyncOperation updateName(String name, String requestId, Callback<MutationResult<User>> callback) { return completeProfile(new ProfileDraft(name, "0012345678"), requestId, callback); }
    }
    @Test public void exactDomainAsciiIdAndPasswordValidation() {
        assertNull(IdentityValidation.email("  STUDENT@USC.EDU  "));
        for (String email : List.of("student@sub.usc.edu", "student@usc.edu.attacker.com", "a@@usc.edu", "@usc.edu", "a b@usc.edu", ".a@usc.edu")) assertNotNull(email, IdentityValidation.email(email));
        assertNull(IdentityValidation.profile(new ProfileDraft("Student", "0012345678")));
        assertNotNull(IdentityValidation.profile(new ProfileDraft("Student", "１２３４５６７８９０")));
        assertNotNull(IdentityValidation.profile(new ProfileDraft(" ", "0012345678")));
        assertNotNull(IdentityValidation.password("12345"));
        assertNotNull(IdentityValidation.group(new GroupDraft("Group", "x".repeat(1001))));
    }
    @Test public void validationIsQueuedAndDoesNotCreateAnAuthAccount() {
        Result<MutationResult<User>> result = new Result<>();
        service.register("a@example.com", "password", new ProfileDraft("A", "0012345678"), "key", result);
        assertEquals(0, result.calls); drain(); assertEquals(ErrorCode.VALIDATION, result.error.code()); assertEquals(0, auth.registrations);
    }
    @Test public void partialRegistrationRetainsAuthAndRetriesOnlyPrivateProfileWithSameKey() {
        Result<MutationResult<User>> first = new Result<>();
        ProfileDraft profile = new ProfileDraft(" Student ", "0012345678");
        service.register("STUDENT@usc.edu", "password", profile, "stable-key", first); drain();
        assertEquals(1, auth.registrations); assertEquals("u", auth.session.uid()); assertEquals(ErrorCode.UNAVAILABLE, first.error.code()); assertNull(first.value);
        users.fail = false; Result<MutationResult<User>> recovered = new Result<>();
        service.completeProfile(profile, "stable-key", recovered); drain();
        assertEquals(1, auth.registrations); assertEquals(2, users.completions); assertEquals("stable-key", users.key);
        assertEquals("Student", recovered.value.value().name()); assertEquals("0012345678", recovered.value.value().uscId());
    }
    @Test public void cancellationBetweenAuthAndProfileSuppressesProfileWorkAndCallbacks() {
        Result<MutationResult<User>> result = new Result<>();
        AsyncOperation op = service.register("a@usc.edu", "password", new ProfileDraft("A", "0012345678"), "key", result);
        op.cancel(); drain(); assertEquals(0, users.completions); assertEquals(0, result.calls);
    }
    @Test public void resetNormalizesEmailAndPropagatesQueuedConfirmationAndTransportFailure() {
        Result<Void> result = new Result<>(); service.sendPasswordResetEmail(" A@USC.EDU ", result);
        assertEquals(0, result.calls); drain(); assertEquals("a@usc.edu", auth.resetEmail); assertEquals(1, result.calls); assertNull(result.error);
        auth.resetError = new AppError(ErrorCode.UNAVAILABLE, "Offline", null, true);
        Result<Void> failed = new Result<>(); service.sendPasswordResetEmail("a@usc.edu", failed); drain(); assertTrue(failed.error.retryable());
        int count = auth.resets; Result<Void> invalid = new Result<>(); service.sendPasswordResetEmail("a@example.com", invalid); drain(); assertEquals(count, auth.resets); assertEquals(ErrorCode.VALIDATION, invalid.error.code());
    }
    @Test public void loginAndLogoutHaveExplicitSessionResults() {
        Result<Session> login = new Result<>(); service.signIn(" A@USC.EDU ", "password", login); drain(); assertEquals("a@usc.edu", login.value.email());
        Result<Void> logout = new Result<>(); service.signOut(logout); drain(); assertNull(auth.session.uid()); assertEquals(1, logout.calls);
    }
    @Test public void cancelledOrOldSessionDeliveryNeverReachesCallerAndTerminalIsOnce() {
        Result<String> result = new Result<>(); PendingOperation<String> op = new PendingOperation<>(executor, result);
        op.onSuccess("saved"); op.onError(new AppError(ErrorCode.INTERNAL, "late", null, false)); op.cancel(); drain(); assertEquals(0, result.calls);
        boolean[] current = {true}; PendingOperation<String> old = new PendingOperation<>(executor, result, () -> current[0]); old.onSuccess("private"); current[0] = false; drain(); assertEquals(0, result.calls);
        PendingOperation<String> once = new PendingOperation<>(executor, result); once.onSuccess("first"); once.onSuccess("second"); drain(); assertEquals(1, result.calls); assertEquals("first", result.value);
    }
}
