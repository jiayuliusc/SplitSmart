package edu.usc.splitsmart.data.firebase;

import java.time.Instant;
import java.util.*;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import edu.usc.splitsmart.domain.contract.ContractVersion;
import edu.usc.splitsmart.domain.model.*;

/** Explicit SDK/wire mappings; domain records are never passed to callable encoders. */
final class IdentityCodec {
    private IdentityCodec() { }
    static Map<?, ?> map(Object value) {
        if (!(value instanceof Map<?, ?> result)) throw new IllegalArgumentException("Expected object");
        return result;
    }
    static String string(Map<?, ?> data, String key) {
        if (!(data.get(key) instanceof String value)) throw new IllegalArgumentException("Invalid string");
        return value;
    }
    static String optional(Map<?, ?> data, String key) { return data.containsKey(key) ? string(data, key) : null; }
    static long integer(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) ||
            number.doubleValue() != number.longValue()) throw new IllegalArgumentException("Invalid integer");
        return number.longValue();
    }
    static long revision(Object value) {
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid revision");
        long result = Long.parseLong(text);
        if (result > ContractVersion.MAX_REVISION) throw new IllegalArgumentException("Invalid revision");
        return result;
    }
    static Instant time(Object value) {
        if (value instanceof Timestamp t) return Instant.ofEpochSecond(t.getSeconds(), t.getNanoseconds());
        Map<?, ?> data = map(value);
        long seconds = integer(data.get("seconds")); long nanos = integer(data.get("nanoseconds"));
        if (seconds < -62135596800L || seconds > 253402300799L || nanos < 0 || nanos >= 1_000_000_000 || nanos % 1000 != 0)
            throw new IllegalArgumentException("Invalid timestamp");
        return Instant.ofEpochSecond(seconds, nanos);
    }
    static void version(Map<?, ?> data) { if (integer(data.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unsupported version"); }
    static List<String> ids(Object value) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("Invalid members");
        List<String> result = new ArrayList<>();
        for (Object member : values) {
            if (!(member instanceof String id)) throw new IllegalArgumentException("Invalid member");
            result.add(id);
        }
        Collections.sort(result); return List.copyOf(result);
    }
    static User user(Object value) {
        Map<?, ?> data = map(value);
        return new User(string(data, "uid"), string(data, "name"), string(data, "email"), string(data, "uscId"),
            time(data.get("createdAt")), time(data.get("updatedAt")));
    }
    static User user(DocumentSnapshot document) {
        Map<String, Object> data = new HashMap<>(Objects.requireNonNull(document.getData())); version(data);
        data.put("uid", document.getId()); return user(data);
    }
    static Group group(Object value) {
        Map<?, ?> data = map(value);
        return new Group(string(data, "groupId"), string(data, "name"), optional(data, "description"), string(data, "ownerId"),
            ids(data.get("memberIds")), revision(data.get("revision")), GroupStatus.valueOf(string(data, "status")),
            time(data.get("createdAt")), time(data.get("updatedAt")));
    }
    static Group group(DocumentSnapshot document) {
        Map<String, Object> data = new HashMap<>(Objects.requireNonNull(document.getData())); version(data);
        data.put("groupId", document.getId()); data.put("revision", Long.toString(integer(data.get("revision")))); return group(data);
    }
    static List<MemberProfile> members(Object value) {
        Map<?, ?> data = map(value); version(data);
        if (!(data.get("members") instanceof List<?> values)) throw new IllegalArgumentException("Invalid members");
        List<MemberProfile> result = new ArrayList<>();
        for (Object member : values) {
            Map<?, ?> m = map(member);
            if (!m.keySet().equals(Set.of("uid", "name", "email"))) throw new IllegalArgumentException("Invalid member projection");
            result.add(new MemberProfile(string(m, "uid"), string(m, "name"), string(m, "email")));
        }
        result.sort(Comparator.comparing(MemberProfile::uid)); return List.copyOf(result);
    }
    static Map<String, Object> draft(GroupDraft draft) {
        Map<String, Object> value = new HashMap<>(); value.put("name", draft.name());
        if (draft.description() != null) value.put("description", draft.description()); return value;
    }
}
