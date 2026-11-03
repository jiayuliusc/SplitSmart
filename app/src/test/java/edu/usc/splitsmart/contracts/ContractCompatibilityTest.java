package edu.usc.splitsmart.contracts;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import edu.usc.splitsmart.data.contract.CallableName;
import edu.usc.splitsmart.data.contract.CallableRequests;
import edu.usc.splitsmart.data.contract.WireModels;
import edu.usc.splitsmart.domain.contract.ContractVersion;
import edu.usc.splitsmart.domain.contract.ErrorCode;
import edu.usc.splitsmart.domain.model.*;

/** Compatibility checks against common JSON, not tests of unimplemented feature algorithms. */
public final class ContractCompatibilityTest {
    private static final Gson JSON = new Gson();
    private static final JsonObject FIXTURES = loadFixtures();

    private static JsonObject loadFixtures() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(
                ContractCompatibilityTest.class.getResourceAsStream("/fixtures.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Type typeFor(String name) throws ClassNotFoundException {
        if (name.endsWith(".request")) {
            String op = name.substring(0, name.indexOf('.'));
            return Class.forName(CallableRequests.class.getName() + "$"
                    + Character.toUpperCase(op.charAt(0)) + op.substring(1) + "Request");
        }
        if (name.endsWith(".response")) {
            String op = name.substring(0, name.indexOf('.'));
            Class<?> value = switch (op) {
                case "completeProfile", "updateProfileName" -> WireModels.User.class;
                case "createGroup", "addGroupMember", "updateGroup" -> WireModels.Group.class;
                case "deleteGroup" -> WireModels.GroupDeletion.class;
                case "createExpense", "updateExpense" -> WireModels.Expense.class;
                case "createSettlement", "updateSettlement" -> WireModels.Settlement.class;
                case "deleteExpense", "deleteSettlement" -> WireModels.DeletedRecord.class;
                case "getGroupMemberProfiles" -> WireModels.MembersResponse.class;
                case "getGroupLedger" -> WireModels.LedgerResponse.class;
                default -> throw new AssertionError("Unmapped callable: " + op);
            };
            if (op.startsWith("get")) return value;
            if (value.equals(WireModels.User.class)) return WireModels.ProfileMutationResponse.class;
            return TypeToken.getParameterized(WireModels.GroupMutationResponse.class, value).getType();
        }
        return Class.forName(WireModels.class.getName() + "$" + name);
    }

    private static void roundTrip(JsonObject example) throws ClassNotFoundException {
        JsonElement value = example.get("value");
        Type type = typeFor(example.get("type").getAsString());
        Object decoded = JSON.fromJson(value, type);
        assertEquals(example.get("id").getAsString(), value, JSON.toJsonTree(decoded, type));
        checkEncoding(value);
    }

    // Test-only contract assertions. Product SDK adapters still need their own runtime decoder.
    private static void checkEncoding(JsonElement value) {
        assertFalse("Null must be omitted, not serialized", value.isJsonNull());
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(ContractCompatibilityTest::checkEncoding);
            return;
        }
        if (!value.isJsonObject()) return;
        JsonObject obj = value.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement child = entry.getValue();
            assertFalse(key, child.isJsonNull());
            if (key.endsWith("Cents") || List.of("revision", "expectedRevision", "groupRevision").contains(key)) {
                assertTrue(key, child.getAsJsonPrimitive().isString());
                String number = child.getAsString();
                assertTrue(key, number.matches("0|[1-9][0-9]*"));
                assertTrue(key, new BigInteger(number).compareTo(
                        BigInteger.valueOf(ContractVersion.MAX_ABSOLUTE_CENTS)) <= 0);
            }
            if (key.equals("schemaVersion")) assertEquals(1, child.getAsInt());
            if (key.equals("currency")) assertEquals("USD", child.getAsString());
            if (key.equals("localDate")) {
                assertTrue(child.getAsString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
                assertTrue(LocalDate.parse(child.getAsString()).getYear() >= 1);
                assertEquals(child.getAsString(), LocalDate.parse(child.getAsString()).toString());
            }
            if (key.equals("requestId")) assertTrue(child.getAsString().matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
            if (key.equals("basisPoints")) {
                int number = child.getAsBigDecimal().intValueExact();
                assertTrue(number >= 0 && number <= 10000);
            }
            checkEncoding(child);
        }
        if (obj.has("seconds")) {
            long seconds = obj.get("seconds").getAsBigDecimal().longValueExact();
            int nanos = obj.get("nanoseconds").getAsBigDecimal().intValueExact();
            assertTrue(seconds >= -62135596800L && seconds <= 253402300799L);
            assertTrue(nanos >= 0 && nanos < 1000000000);
            assertEquals(0, nanos % 1000);
            Instant time = Instant.ofEpochSecond(seconds, nanos);
            assertEquals(seconds, time.getEpochSecond());
            assertEquals(nanos, time.getNano());
        }
    }

    @Test
    public void wireRecordsRoundTripAllCommonExamplesWithoutDroppingFields() throws Exception {
        for (JsonElement entry : FIXTURES.getAsJsonArray("cases")) roundTrip(entry.getAsJsonObject());
    }

    @Test
    public void invalidCommonRepresentationsAreRejected() throws Exception {
        for (JsonElement entry : FIXTURES.getAsJsonArray("invalidCases")) {
            JsonObject example = entry.getAsJsonObject();
            // Resolve first so a misspelled test registry entry cannot count as a rejection.
            typeFor(example.get("type").getAsString());
            boolean rejected = false;
            try {
                roundTrip(example);
            } catch (AssertionError | RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Accepted invalid fixture: " + example.get("id"), rejected);
        }
    }

    @Test
    public void namesEnumsOwnershipAndNumericLimitsAgreeWithTypeScriptFixtures() {
        Map<String, String> owners = new LinkedHashMap<>();
        for (CallableName callable : CallableName.values()) owners.put(callable.wireName(), callable.owner());
        assertEquals(FIXTURES.get("owners"), JSON.toJsonTree(owners));
        for (Class<? extends Enum<?>> enumClass : List.of(Category.class, SplitMethod.class,
                TransactionType.class, GroupStatus.class, HistorySort.class, ErrorCode.class)) {
            List<String> names = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).collect(Collectors.toList());
            assertEquals(FIXTURES.getAsJsonObject("enums").get(enumClass.getSimpleName()), JSON.toJsonTree(names));
        }
        JsonObject constants = FIXTURES.getAsJsonObject("constants");
        assertEquals(constants.get("maxAbsoluteCents").getAsString(), Long.toString(ContractVersion.MAX_ABSOLUTE_CENTS));
        assertEquals(constants.get("maxRevision").getAsString(), Long.toString(ContractVersion.MAX_REVISION));
        assertEquals(constants.get("totalBasisPoints").getAsInt(), ContractVersion.TOTAL_BASIS_POINTS);
        assertEquals(constants.get("currency").getAsString(), ContractVersion.CURRENCY);
        JsonArray cases = FIXTURES.getAsJsonArray("cases");
        List<String> types = new ArrayList<>();
        cases.forEach(c -> types.add(c.getAsJsonObject().get("type").getAsString()));
        for (CallableName callable : CallableName.values()) {
            assertTrue(types.contains(callable.wireName() + ".request"));
            assertTrue(types.contains(callable.wireName() + ".response"));
        }
        for (Class<?> shape : WireModels.class.getDeclaredClasses()) {
            if (!shape.equals(WireModels.GroupMutationResponse.class)) assertTrue(shape.getName(), types.contains(shape.getSimpleName()));
        }
    }

    @Test
    public void domainCollectionsCannotBeChangedThroughAliasedInputOrOutput() {
        List<String> participants = new ArrayList<>(List.of("A", "B"));
        List<SplitInput> inputs = new ArrayList<>();
        ExpenseDraft draft = new ExpenseDraft("Example", 1000, "A", participants,
                SplitMethod.EQUAL, Category.OTHER, null, inputs);
        participants.clear();
        inputs.add(new SplitInput("C", null, null));
        assertEquals(List.of("A", "B"), draft.participantIds());
        assertTrue(draft.splitInputs().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> draft.participantIds().clear());
        Map<Category, Long> totals = new LinkedHashMap<>();
        for (Category category : Category.values()) totals.put(category, 0L);
        totals.put(Category.FOOD, 1000L);
        Map<Category, Long> expectedTotals = new LinkedHashMap<>(totals);
        MonthlySummary summary = new MonthlySummary("group-1", java.time.YearMonth.of(2026, 11), 1, 1, 1000, totals);
        totals.clear();
        assertEquals(expectedTotals, summary.categoryTotalsCents());
        assertThrows(UnsupportedOperationException.class, () -> summary.categoryTotalsCents().clear());
        assertThrows(NullPointerException.class, () -> new LedgerSnapshot("g", 1, List.of("A"), null, List.of()));
    }

    @Test
    public void signedCentsFixturesFitTheSameExactRange() {
        for (JsonElement example : FIXTURES.getAsJsonArray("signedCentsExamples")) {
            checkSigned(example.getAsString());
        }
        for (JsonElement example : FIXTURES.getAsJsonArray("invalidSignedCentsExamples")) {
            assertThrows(AssertionError.class, () -> checkSigned(example.getAsString()));
        }
    }

    private static void checkSigned(String value) {
        assertTrue(value.matches("0|-?[1-9][0-9]*"));
        BigInteger amount = new BigInteger(value);
        assertTrue(amount.abs().compareTo(BigInteger.valueOf(ContractVersion.MAX_ABSOLUTE_CENTS)) <= 0);
        assertEquals(value, Long.toString(amount.longValueExact()));
    }
}
