package com.telco.platform.events.testsupport;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

/**
 * Shared Avro schema/Java-payload contract checker (feature 14.5 phase 6, ADR-019).
 *
 * <p>Every per-service {@code *EventSchemaCompatTest} / {@code *EventContractTest} that existed
 * before this class only ever compared Avro field <em>names</em> against a Java payload (via
 * {@code Set<String>} reflection over record components, or over a captured runtime {@code Map} for
 * the two services that build outbox payloads inline as {@code Map.of(...)}). That let real drift
 * (usage-service's timestamp type mismatches, billing-service's nullability question) go undetected,
 * because two schemas with identically-named but differently-typed fields would still pass. This
 * class extends the check to types and nullability, once, reusably, on every producing service's test
 * classpath (it is packaged as this module's test-jar; see {@code pom.xml}).
 *
 * <p>It is deliberately built against the real, canonical {@link Schema} embedded in the
 * Avro-generated {@code SpecificRecord} classes in {@code platform-event-contracts} (obtained via
 * {@link #canonicalSchema(String)}), not a hand-copied local {@code .avsc} snapshot under a service's
 * own {@code src/test/resources} -- comparing against a local copy only proves the Java class matches
 * the copy, not the actual governed contract (see the 14.5 tracking doc, phase 6, for the reasoning).
 *
 * <h2>Two entry points</h2>
 * <ul>
 *   <li>{@link #assertRecordMatchesSchema(Schema, Class)} -- for services that publish a typed Java
 *       {@code record} as the outbox payload (the majority). Reflects over {@link RecordComponent}
 *       declared types. Recurses into nested Avro records (e.g. {@code order.created.v1}'s
 *       {@code items} array of {@code OrderItemPayload}) when the Java field is itself a record or a
 *       {@code List} of records.</li>
 *   <li>{@link #assertPayloadMatchesSchema(Schema, Map)} -- for services that build the outbox payload
 *       inline as {@code Map.of(...)} (ticket-service, notification-service). Checks the actual
 *       captured runtime value's type and observed nullness, since there is no static Java type to
 *       reflect on.</li>
 * </ul>
 *
 * <h2>Nullability policy</h2>
 * <ul>
 *   <li>Avro schema field is non-nullable, but the Java side can be null (a boxed wrapper type,
 *       {@code Optional<T>}, or -- for the runtime/map entry point -- an actually-observed
 *       {@code null} value): <b>FAIL</b>. This is a real bug: the wire contract promises the field is
 *       always present, but the producer can omit it.</li>
 *   <li>Avro schema field is nullable ({@code ["null", X]}), but the Java side can never be null (a
 *       Java primitive): <b>WARN</b> only (printed, does not fail the build). This is slack the
 *       schema retains that the current producer does not need -- looser than strictly necessary, but
 *       safe.</li>
 *   <li>Anything else (in particular: a plain reference type such as {@code String} or
 *       {@code BigDecimal}, which the Java type system cannot statically prove is non-null either way)
 *       is undecidable from reflection alone and is not asserted in either direction.</li>
 * </ul>
 */
public final class AvroContractAssertions {

    private static final Set<Class<?>> BOXED_WRAPPERS = Set.of(Long.class, Integer.class, Double.class,
            Float.class, Boolean.class, Short.class, Byte.class, Character.class);

    private AvroContractAssertions() {
    }

    // ---------------------------------------------------------------------
    // Schema loading
    // ---------------------------------------------------------------------

    /**
     * Loads the {@link Schema} embedded in an Avro-generated {@code SpecificRecord} class (its static
     * {@code getClassSchema()} method) by fully qualified class name. This is the canonical, governed
     * contract -- not a local test-resources copy.
     */
    public static Schema canonicalSchema(String generatedClassName) {
        try {
            Class<?> generated = Class.forName(generatedClassName);
            Method getClassSchema = generated.getMethod("getClassSchema");
            return (Schema) getClassSchema.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Canonical Avro class not found/usable on the test "
                    + "classpath: " + generatedClassName + ". Confirm platform-event-contracts is a "
                    + "test (or compile) dependency of this module.", e);
        }
    }

    // ---------------------------------------------------------------------
    // Entry point 1: typed Java record reflection
    // ---------------------------------------------------------------------

    /**
     * Asserts that {@code javaRecordClass}'s declared components match {@code schema} field-for-field
     * (name, type, and decidable nullability). Throws {@link AssertionError} listing every violation
     * found (not just the first) on failure.
     */
    public static void assertRecordMatchesSchema(Schema schema, Class<?> javaRecordClass) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        checkRecordAgainstSchema(schema, javaRecordClass, failures, warnings, javaRecordClass.getSimpleName());
        report(schema, javaRecordClass.getName(), failures, warnings);
    }

    private static void checkRecordAgainstSchema(Schema schema, Class<?> javaRecordClass,
            List<String> failures, List<String> warnings, String path) {
        if (!javaRecordClass.isRecord()) {
            failures.add(path + ": " + javaRecordClass.getName() + " must be a Java record");
            return;
        }

        Map<String, RecordComponent> javaComponents = new HashMap<>();
        for (RecordComponent rc : javaRecordClass.getRecordComponents()) {
            javaComponents.put(rc.getName(), rc);
        }

        Set<String> avroFieldNames = new LinkedHashSet<>();
        for (Schema.Field f : schema.getFields()) {
            avroFieldNames.add(f.name());
        }

        for (String name : avroFieldNames) {
            if (!javaComponents.containsKey(name)) {
                failures.add(path + ": Avro field '" + name + "' is missing from Java record "
                        + javaRecordClass.getName() + " (a removed/renamed field is a "
                        + "backward-incompatible change: add a new event version)");
            }
        }
        for (String name : javaComponents.keySet()) {
            if (!avroFieldNames.contains(name)) {
                failures.add(path + ": Java record " + javaRecordClass.getName() + " declares field '"
                        + name + "' that is absent from the frozen Avro contract (register it in the "
                        + ".avsc, or remove it from the record)");
            }
        }

        for (Schema.Field avroField : schema.getFields()) {
            RecordComponent rc = javaComponents.get(avroField.name());
            if (rc == null) {
                continue; // already reported as a missing field above
            }
            checkDeclaredField(avroField, rc.getType(), rc.getGenericType(), failures, warnings,
                    path + "." + avroField.name());
        }
    }

    private static void checkDeclaredField(Schema.Field avroField, Class<?> javaType, Type genericType,
            List<String> failures, List<String> warnings, String path) {
        NullableResolution resolution = resolveNullable(avroField.schema(), failures, path);

        boolean javaIsOptional = javaType == Optional.class;
        Class<?> effectiveJavaType = javaType;
        Type effectiveGenericType = genericType;
        if (javaIsOptional && genericType instanceof ParameterizedType pt
                && pt.getActualTypeArguments()[0] instanceof Class<?> c) {
            effectiveJavaType = c;
            effectiveGenericType = c;
        }

        Nullability javaNullability = classifyNullability(javaType, javaIsOptional);
        if (!resolution.nullable() && javaNullability == Nullability.CAN_BE_NULL) {
            failures.add(path + ": Avro schema requires this field to always be present "
                    + "(non-nullable), but the Java type " + describeJavaType(javaType, genericType)
                    + " signals it may be null -- tighten the Java type, or make the schema field "
                    + "nullable if null genuinely is possible");
        } else if (resolution.nullable() && javaNullability == Nullability.NEVER_NULL) {
            warnings.add(path + ": Avro schema allows null (nullable union) but the Java type "
                    + describeJavaType(javaType, genericType) + " can never be null -- looser than "
                    + "strictly necessary, but safe");
        }

        checkType(resolution.effective(), effectiveJavaType, effectiveGenericType, failures, warnings, path);
    }

    // ---------------------------------------------------------------------
    // Entry point 2: runtime-captured Map payload (Map.of(...) producers)
    // ---------------------------------------------------------------------

    /**
     * Asserts that a captured runtime outbox payload (as produced by services that build
     * {@code Map.of(...)} directly, e.g. ticket-service, notification-service) matches {@code schema}
     * field-for-field (name, runtime value type, and observed nullness). Throws {@link AssertionError}
     * listing every violation found on failure.
     */
    public static void assertPayloadMatchesSchema(Schema schema, Map<String, Object> payload) {
        List<String> failures = new ArrayList<>();
        checkMapAgainstSchema(schema, payload, failures, "payload");
        report(schema, "<captured runtime payload>", failures, List.of());
    }

    private static void checkMapAgainstSchema(Schema schema, Map<String, Object> payload,
            List<String> failures, String path) {
        Set<String> avroFieldNames = new LinkedHashSet<>();
        for (Schema.Field f : schema.getFields()) {
            avroFieldNames.add(f.name());
        }

        for (String name : avroFieldNames) {
            if (!payload.containsKey(name)) {
                failures.add(path + ": Avro field '" + name + "' is missing from the captured runtime "
                        + "payload (a removed/renamed field is a backward-incompatible change: add a "
                        + "new event version)");
            }
        }
        for (String name : payload.keySet()) {
            if (!avroFieldNames.contains(name)) {
                failures.add(path + ": captured runtime payload has key '" + name + "' that is absent "
                        + "from the frozen Avro contract (register it in the .avsc, or remove it from "
                        + "the handler)");
            }
        }

        for (Schema.Field avroField : schema.getFields()) {
            if (!payload.containsKey(avroField.name())) {
                continue; // already reported above
            }
            checkRuntimeField(avroField, payload.get(avroField.name()), failures,
                    path + "." + avroField.name());
        }
    }

    private static void checkRuntimeField(Schema.Field avroField, Object value, List<String> failures,
            String path) {
        NullableResolution resolution = resolveNullable(avroField.schema(), failures, path);

        if (value == null) {
            if (!resolution.nullable()) {
                failures.add(path + ": Avro schema requires this field to always be present "
                        + "(non-nullable), but the captured runtime payload has a null value here -- "
                        + "a real bug, not just a looser-than-necessary gap");
            }
            return;
        }

        checkRuntimeType(resolution.effective(), value, failures, path);
    }

    // ---------------------------------------------------------------------
    // Shared: nullable-union resolution
    // ---------------------------------------------------------------------

    private record NullableResolution(boolean nullable, Schema effective) {
    }

    private static NullableResolution resolveNullable(Schema fieldSchema, List<String> failures, String path) {
        if (fieldSchema.getType() != Schema.Type.UNION) {
            return new NullableResolution(false, fieldSchema);
        }
        List<Schema> branches = fieldSchema.getTypes();
        List<Schema> nonNull = branches.stream().filter(s -> s.getType() != Schema.Type.NULL).toList();
        boolean hasNull = branches.stream().anyMatch(s -> s.getType() == Schema.Type.NULL);
        if (nonNull.size() != 1) {
            failures.add(path + ": unsupported multi-branch union " + fieldSchema + "; this checker "
                    + "only understands the [\"null\", X] nullable-field pattern used across this "
                    + "codebase's schemas");
            return new NullableResolution(hasNull, nonNull.isEmpty() ? fieldSchema : nonNull.get(0));
        }
        return new NullableResolution(hasNull, nonNull.get(0));
    }

    // ---------------------------------------------------------------------
    // Nullability classification (declared-type / reflection side only)
    // ---------------------------------------------------------------------

    private enum Nullability { NEVER_NULL, CAN_BE_NULL, UNKNOWN }

    private static Nullability classifyNullability(Class<?> javaType, boolean isOptional) {
        if (isOptional) {
            return Nullability.CAN_BE_NULL;
        }
        if (javaType.isPrimitive()) {
            return Nullability.NEVER_NULL;
        }
        if (BOXED_WRAPPERS.contains(javaType)) {
            return Nullability.CAN_BE_NULL;
        }
        return Nullability.UNKNOWN;
    }

    // ---------------------------------------------------------------------
    // Type checking: declared (reflection) side
    // ---------------------------------------------------------------------

    private static void checkType(Schema effective, Class<?> javaType, Type genericType,
            List<String> failures, List<String> warnings, String path) {
        switch (effective.getType()) {
            case STRING -> requireDeclared(path, effective, javaType, failures, String.class);
            case BOOLEAN -> requireDeclared(path, effective, javaType, failures, boolean.class, Boolean.class);
            case INT -> requireDeclared(path, effective, javaType, failures, int.class, Integer.class);
            case LONG -> {
                if (isTimestampMillis(effective)) {
                    requireDeclared(path, effective, javaType, failures, long.class, Long.class, Instant.class);
                } else {
                    requireDeclared(path, effective, javaType, failures, long.class, Long.class);
                }
            }
            case FLOAT -> requireDeclared(path, effective, javaType, failures, float.class, Float.class);
            case DOUBLE -> requireDeclared(path, effective, javaType, failures, double.class, Double.class);
            case BYTES, FIXED -> {
                if (isDecimal(effective)) {
                    requireDeclared(path, effective, javaType, failures, BigDecimal.class);
                } else {
                    requireDeclared(path, effective, javaType, failures, byte[].class, ByteBuffer.class);
                }
            }
            case ENUM -> requireDeclared(path, effective, javaType, failures, String.class, Enum.class);
            case ARRAY -> {
                if (!Collection.class.isAssignableFrom(javaType)) {
                    failures.add(path + ": Avro array field must map to a Java Collection/List type, "
                            + "found " + javaType.getName());
                    break;
                }
                Schema elementSchema = effective.getElementType();
                Class<?> elementClass = genericElementType(genericType);
                if (elementSchema.getType() == Schema.Type.RECORD) {
                    if (elementClass != null && elementClass.isRecord()) {
                        checkRecordAgainstSchema(elementSchema, elementClass, failures, warnings, path + "[]");
                    }
                    // else: element type erased at runtime (raw List) -- best-effort only, documented
                    // limitation; nothing further can be checked without an actual instance.
                }
            }
            case RECORD -> {
                if (javaType.isRecord()) {
                    checkRecordAgainstSchema(effective, javaType, failures, warnings, path);
                } else {
                    failures.add(path + ": Avro record field must map to a Java record type, found "
                            + javaType.getName());
                }
            }
            case MAP -> requireDeclared(path, effective, javaType, failures, Map.class);
            case NULL -> {
                // A bare top-level "null" schema is nonsensical for a real field; nothing to check.
            }
            default -> failures.add(path + ": unsupported Avro type " + effective.getType()
                    + " in this checker");
        }
    }

    private static void requireDeclared(String path, Schema effective, Class<?> javaType,
            List<String> failures, Class<?>... acceptable) {
        for (Class<?> c : acceptable) {
            if (isCompatible(c, javaType)) {
                return;
            }
        }
        failures.add(path + ": Avro type " + describeAvroType(effective) + " expects Java type "
                + describeExpected(acceptable) + ", but found " + javaType.getName());
    }

    private static boolean isCompatible(Class<?> acceptable, Class<?> javaType) {
        if (acceptable.equals(javaType)) {
            return true;
        }
        if (!acceptable.isPrimitive() && !javaType.isPrimitive()) {
            return acceptable.isAssignableFrom(javaType);
        }
        return false;
    }

    private static Class<?> genericElementType(Type genericType) {
        if (genericType instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1
                && pt.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Type checking: runtime-value (captured Map payload) side
    // ---------------------------------------------------------------------

    private static void checkRuntimeType(Schema effective, Object value, List<String> failures, String path) {
        Class<?> javaType = value.getClass();
        switch (effective.getType()) {
            case STRING -> requireRuntime(path, effective, javaType, failures, String.class, CharSequence.class);
            case BOOLEAN -> requireRuntime(path, effective, javaType, failures, Boolean.class);
            case INT -> requireRuntime(path, effective, javaType, failures, Integer.class);
            case LONG -> {
                if (isTimestampMillis(effective)) {
                    requireRuntime(path, effective, javaType, failures, Long.class, Instant.class);
                } else {
                    requireRuntime(path, effective, javaType, failures, Long.class);
                }
            }
            case FLOAT -> requireRuntime(path, effective, javaType, failures, Float.class);
            case DOUBLE -> requireRuntime(path, effective, javaType, failures, Double.class);
            case BYTES, FIXED -> {
                if (isDecimal(effective)) {
                    requireRuntime(path, effective, javaType, failures, BigDecimal.class);
                } else {
                    requireRuntime(path, effective, javaType, failures, byte[].class);
                }
            }
            case ENUM -> requireRuntime(path, effective, javaType, failures, String.class, Enum.class);
            case ARRAY -> {
                if (!(value instanceof Collection<?> coll)) {
                    failures.add(path + ": Avro array field must map to a runtime Collection, found "
                            + javaType.getName());
                    break;
                }
                Schema elementSchema = effective.getElementType();
                if (elementSchema.getType() == Schema.Type.RECORD) {
                    int i = 0;
                    for (Object element : coll) {
                        if (element instanceof Map<?, ?> m) {
                            checkMapAgainstSchema(elementSchema, castToStringObjectMap(m), failures,
                                    path + "[" + i + "]");
                        }
                        i++;
                    }
                }
            }
            case RECORD -> {
                if (value instanceof Map<?, ?> m) {
                    checkMapAgainstSchema(effective, castToStringObjectMap(m), failures, path);
                } else {
                    failures.add(path + ": Avro record field must map to a Map<String,Object> in a "
                            + "captured runtime payload, found " + javaType.getName());
                }
            }
            case MAP -> requireRuntime(path, effective, javaType, failures, Map.class);
            case NULL -> {
                // handled by the caller's null check before reaching here
            }
            default -> failures.add(path + ": unsupported Avro type " + effective.getType()
                    + " in this checker");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToStringObjectMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static void requireRuntime(String path, Schema effective, Class<?> javaType,
            List<String> failures, Class<?>... acceptable) {
        for (Class<?> c : acceptable) {
            if (c.isAssignableFrom(javaType)) {
                return;
            }
        }
        failures.add(path + ": Avro type " + describeAvroType(effective) + " expects a runtime value "
                + "assignable to " + describeExpected(acceptable) + ", but found " + javaType.getName());
    }

    // ---------------------------------------------------------------------
    // Logical-type helpers
    // ---------------------------------------------------------------------

    private static boolean isTimestampMillis(Schema schema) {
        LogicalType lt = schema.getLogicalType();
        return lt != null && "timestamp-millis".equals(lt.getName());
    }

    private static boolean isDecimal(Schema schema) {
        return schema.getLogicalType() instanceof LogicalTypes.Decimal;
    }

    // ---------------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------------

    private static void report(Schema schema, String subjectDescription, List<String> failures,
            List<String> warnings) {
        for (String w : warnings) {
            System.out.println("[avro-contract WARN] " + schema.getFullName() + " vs " + subjectDescription
                    + ": " + w);
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Avro contract violation(s) for schema " + schema.getFullName()
                    + " vs " + subjectDescription + ":\n  - " + String.join("\n  - ", failures));
        }
    }

    private static String describeAvroType(Schema effective) {
        LogicalType lt = effective.getLogicalType();
        return lt != null ? effective.getType() + "/" + lt.getName() : effective.getType().toString();
    }

    private static String describeExpected(Class<?>... classes) {
        return Arrays.stream(classes).map(Class::getSimpleName).collect(Collectors.joining(" or "));
    }

    private static String describeJavaType(Class<?> javaType, Type genericType) {
        return genericType != null ? genericType.getTypeName() : javaType.getName();
    }
}
