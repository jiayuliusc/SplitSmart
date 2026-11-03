package edu.usc.splitsmart.identity;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import edu.usc.splitsmart.config.*;
import edu.usc.splitsmart.domain.contract.*;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.service.*;

/** Real Android adapters -> Firebase emulators, including private-profile recovery and Auth reset API. */
@RunWith(AndroidJUnit4.class)
public final class IdentityGroupSdkTest {
    private static final class Result<T> implements Callback<T> {
        final CountDownLatch completed = new CountDownLatch(1); T value; AppError error; boolean main;
        @Override public void onSuccess(T value) { this.value = value; main = android.os.Looper.myLooper() == android.os.Looper.getMainLooper(); completed.countDown(); }
        @Override public void onError(AppError error) { this.error = error; main = android.os.Looper.myLooper() == android.os.Looper.getMainLooper(); completed.countDown(); }
    }
    private <T> Result<T> invoke(Function<Callback<T>, AsyncOperation> call) throws Exception {
        Result<T> result = new Result<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> call.apply(result));
        assertTrue("callback deadline", result.completed.await(30, TimeUnit.SECONDS)); assertTrue("main-thread callback", result.main); return result;
    }
    private <T> T ok(Function<Callback<T>, AsyncOperation> call) throws Exception {
        Result<T> result = invoke(call); assertNull(result.error == null ? "" : result.error.toString(), result.error); return result.value;
    }
    @Test public void androidAccountGroupAndResetFlow() throws Exception {
        SplitSmartApplication app = (SplitSmartApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        AuthService auth = app.getContainer().auth(); GroupService groups = app.getContainer().groups();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "android-" + suffix + "@usc.edu";
        String otherEmail = "android-member-" + suffix + "@usc.edu";
        String uscId = String.format(Locale.ROOT, "%010d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000_000_000L));
        String otherId = String.format(Locale.ROOT, "%010d", (Long.parseLong(uscId) + 1) % 10_000_000_000L);
        try {
            ok(auth::signOut);
            MutationResult<User> registered = ok(cb -> auth.register(email, "android-password", new ProfileDraft("Android owner", uscId), UUID.randomUUID().toString(), cb));
            assertEquals(email, registered.value().email());
            ReadResult<User> profile = ok(cb -> auth.getCurrentProfile(ReadPolicy.FRESH, cb)); assertEquals(ReadSource.SERVER, profile.source());
            assertEquals(uscId, profile.value().uscId());
            MutationResult<User> renamed = ok(cb -> auth.updateName("Renamed owner", UUID.randomUUID().toString(), cb)); assertEquals("Renamed owner", renamed.value().name());
            String createKey = UUID.randomUUID().toString();
            MutationResult<Group> created = ok(cb -> groups.createGroup(new GroupDraft("SDK group", "Private test"), createKey, cb));
            MutationResult<Group> repeated = ok(cb -> groups.createGroup(new GroupDraft("SDK group", "Private test"), createKey, cb));
            assertEquals(created.value().groupId(), repeated.value().groupId());
            String groupId = created.value().groupId();
            ok(auth::signOut);
            this.<MutationResult<User>>ok(cb -> auth.register(otherEmail, "android-password", new ProfileDraft("Android member", otherId), UUID.randomUUID().toString(), cb));
            Result<ReadResult<Group>> outsider = invoke(cb -> groups.getGroup(groupId, ReadPolicy.FRESH, cb)); assertEquals(ErrorCode.PERMISSION_DENIED, outsider.error.code());
            ok(auth::signOut); this.<Session>ok(cb -> auth.signIn(email, "android-password", cb));
            MutationResult<Group> added = ok(cb -> groups.addMember(groupId, 1, otherEmail, UUID.randomUUID().toString(), cb)); assertEquals(2, added.value().memberIds().size());
            ReadResult<List<MemberProfile>> members = ok(cb -> groups.getMemberProfiles(groupId, ReadPolicy.FRESH, cb)); assertEquals(2, members.value().size());
            Result<MutationResult<Group>> stale = invoke(cb -> groups.updateGroup(groupId, 1, new GroupDraft("Stale", null), UUID.randomUUID().toString(), cb)); assertEquals(ErrorCode.CONFLICT, stale.error.code());
            ReadResult<List<Group>> list = ok(cb -> groups.getGroupsForCurrentUser(ReadPolicy.FRESH, cb)); assertEquals(1, list.value().size());
            this.<Void>ok(cb -> auth.sendPasswordResetEmail(email, cb));
            this.<Void>ok(cb -> auth.sendPasswordResetEmail("unregistered-" + suffix + "@usc.edu", cb));
            MutationResult<GroupDeletion> deleted = ok(cb -> groups.deleteGroup(groupId, 2, UUID.randomUUID().toString(), cb)); assertEquals(GroupStatus.DELETING, deleted.value().status());
            ok(auth::signOut);
            Result<ReadResult<User>> signedOut = invoke(cb -> auth.getCurrentProfile(ReadPolicy.CACHE_ONLY, cb)); assertEquals(ErrorCode.UNAUTHENTICATED, signedOut.error.code());
        } finally { ok(auth::signOut); }
    }
}
