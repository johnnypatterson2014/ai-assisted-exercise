package exercise;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidates demographic records from several source systems into one record per patient.
 * <p>
 * Identity: two records are the same patient when their first name, last name and date of birth are equal after
 * trimming, collapsing whitespace, folding case, stripping diacritics and normalising apostrophes. No fuzzy
 * matching.
 * <p>
 * Precedence: name and date of birth come from the most trusted source ({@link SourceSystem} order, ties by
 * newest); phone and email each come from the newest record that has a valid value (ties by trust). A record
 * whose {@code updatedAt} is missing or unparseable ranks last.
 * <p>
 * Records that cannot be matched or traced (blank name, bad date of birth, unknown source, blank MRN) are
 * rejected with a reason and never abort the batch. An invalid phone or email drops that field only.
 */
public final class PatientConsolidation {

    private static final Logger LOG = LoggerFactory.getLogger(PatientConsolidation.class);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOT_DIGIT = Pattern.compile("\\D");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    /** Longest piece of raw input text echoed in a reject reason, so garbage cannot bloat the result. */
    private static final int MAX_ECHO = 40;

    /** Newest first; a record without a usable updatedAt ranks last. */
    private static final Comparator<Normalised> NEWEST_FIRST =
            Comparator.comparing(Normalised::updatedAt, Comparator.nullsLast(Comparator.<Instant>reverseOrder()));

    /** Trust order from {@link SourceSystem}: EHR before LAB before CRM. */
    private static final Comparator<Normalised> MOST_TRUSTED_FIRST = Comparator.comparing(Normalised::system);

    /** For demographics: trust, then recency, then input position so the order is total. */
    private static final Comparator<Normalised> FOR_IDENTITY =
            MOST_TRUSTED_FIRST.thenComparing(NEWEST_FIRST).thenComparing(Normalised::index);

    /** For contact details: recency, then trust, then input position. */
    private static final Comparator<Normalised> FOR_CONTACT =
            NEWEST_FIRST.thenComparing(MOST_TRUSTED_FIRST).thenComparing(Normalised::index);

    private static final Comparator<SourceRef> SOURCE_ORDER =
            Comparator.comparing(SourceRef::system).thenComparing(SourceRef::mrn);

    private PatientConsolidation() {
    }

    /** A record as received, before validation. All fields are raw strings and any may be null. */
    public record RawRecord(String sourceSystem, String mrn, String firstName, String lastName, String dateOfBirth,
            String phone, String email, String updatedAt) {
    }

    /** Where a consolidated patient came from. */
    public record SourceRef(SourceSystem system, String mrn) {
    }

    /**
     * One consolidated patient. {@code phone} is ten digits or null; {@code email} is lower-cased or null;
     * {@code sources} is sorted by system then MRN.
     */
    public record Patient(String firstName, String lastName, LocalDate dateOfBirth, String phone, String email,
            List<SourceRef> sources) {
    }

    /** A record that was not used; {@code index} is its position in the input list. */
    public record RejectedRecord(int index, String reason) {
    }

    /** Patients sorted by last name, first name, date of birth; rejects sorted by input index. */
    public record Result(List<Patient> patients, List<RejectedRecord> rejected) {
    }

    /**
     * Consolidates the records into one patient each.
     *
     * @param records the records in any order; must not be null, may be empty
     * @return the patients and the rejected records, both in deterministic order
     * @throws IllegalArgumentException if {@code records} is null
     */
    public static Result consolidate(List<RawRecord> records) {
        if (records == null) {
            throw new IllegalArgumentException("records must not be null");
        }

        // 1. Validate and normalise each row; keep bad rows as rejects with the reason, never abort the batch.
        List<RejectedRecord> rejected = new ArrayList<>();
        Map<SourceRef, List<Normalised>> bySource = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            try {
                Normalised normalised = normalise(i, records.get(i));
                bySource.computeIfAbsent(normalised.sourceRef(), k -> new ArrayList<>()).add(normalised);
            } catch (IllegalArgumentException e) {
                rejected.add(new RejectedRecord(i, e.getMessage()));
            }
        }

        // 2. One identity per (source, MRN): the newest decides which patient the versions belong to; older
        //    versions are reported as superseded but their valid contact details still take part in the merge.
        Map<MatchKey, List<Normalised>> byPatient = new LinkedHashMap<>();
        for (List<Normalised> versions : bySource.values()) {
            versions.sort(FOR_CONTACT);
            Normalised newest = versions.get(0);
            byPatient.computeIfAbsent(newest.matchKey(), k -> new ArrayList<>()).addAll(versions);
            for (Normalised older : versions.subList(1, versions.size())) {
                rejected.add(new RejectedRecord(older.index(),
                        "superseded by a newer record with the same source system and MRN"));
            }
        }

        // 3. Merge each patient's records: identity from the most trusted, contact details from the newest valid.
        List<Patient> patients = new ArrayList<>();
        for (List<Normalised> group : byPatient.values()) {
            patients.add(merge(group));
        }
        patients.sort(Comparator.comparing((Patient p) -> p.lastName().toLowerCase(Locale.ROOT))
                .thenComparing(p -> p.firstName().toLowerCase(Locale.ROOT))
                .thenComparing(Patient::dateOfBirth));
        rejected.sort(Comparator.comparing(RejectedRecord::index));

        LOG.info("consolidated {} records into {} patients, {} rejected", records.size(), patients.size(),
                rejected.size());
        return new Result(List.copyOf(patients), List.copyOf(rejected));
    }

    /** A validated, normalised record. {@code phone}, {@code email} and {@code updatedAt} are null when unusable. */
    private record Normalised(int index, SourceSystem system, String mrn, String firstName, String lastName,
            LocalDate dateOfBirth, String phone, String email, Instant updatedAt) {

        SourceRef sourceRef() {
            return new SourceRef(system, mrn);
        }

        MatchKey matchKey() {
            return new MatchKey(fold(lastName), fold(firstName), dateOfBirth);
        }
    }

    /** Case, diacritics and apostrophe variants are spelling, not identity: "Zoë O’Brien" folds to "zoe o'brien". */
    static String fold(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("")
                .replace('’', '\'')
                .toLowerCase(Locale.ROOT);
    }

    private static String echo(String raw) {
        return raw.length() <= MAX_ECHO ? raw : raw.substring(0, MAX_ECHO) + "...";
    }

    /** The identity rule: case-folded names plus date of birth. */
    private record MatchKey(String lastName, String firstName, LocalDate dateOfBirth) {
    }

    private static Patient merge(List<Normalised> group) {
        Normalised identity = group.stream().min(FOR_IDENTITY).orElseThrow();
        List<Normalised> byRecency = group.stream().sorted(FOR_CONTACT).toList();
        String phone = byRecency.stream().map(Normalised::phone).filter(p -> p != null).findFirst().orElse(null);
        String email = byRecency.stream().map(Normalised::email).filter(e -> e != null).findFirst().orElse(null);
        List<SourceRef> sources = group.stream().map(Normalised::sourceRef).distinct().sorted(SOURCE_ORDER).toList();
        return new Patient(identity.firstName(), identity.lastName(), identity.dateOfBirth(), phone, email, sources);
    }

    /** Converts a raw row to a normalised record, or throws IllegalArgumentException with the reason. */
    private static Normalised normalise(int index, RawRecord raw) {
        if (raw == null) {
            throw new IllegalArgumentException("record is null");
        }
        SourceSystem system = SourceSystem.parse(raw.sourceSystem())
                .orElseThrow(() -> new IllegalArgumentException("source system is missing or unknown"));
        String mrn = required("mrn", raw.mrn());
        String firstName = required("first name", raw.firstName());
        String lastName = required("last name", raw.lastName());
        LocalDate dateOfBirth = parseDateOfBirth(raw.dateOfBirth());
        return new Normalised(index, system, mrn, firstName, lastName, dateOfBirth,
                normalisePhone(raw.phone()), normaliseEmail(raw.email()), parseUpdatedAt(raw.updatedAt()));
    }

    /** Trims and collapses inner whitespace; blank is an error. */
    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return WHITESPACE.matcher(value.trim()).replaceAll(" ");
    }

    private static LocalDate parseDateOfBirth(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("date of birth is missing");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date of birth is not yyyy-MM-dd: " + echo(value.trim()));
        }
    }

    /** Ten NANP digits, a leading country code 1 dropped; anything else is unusable and becomes null. */
    static String normalisePhone(String value) {
        if (value == null) {
            return null;
        }
        String digits = NOT_DIGIT.matcher(value).replaceAll("");
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }
        return digits.length() == 10 ? digits : null;
    }

    /** Trimmed and lower-cased; must look like local@domain.tld, otherwise null. */
    static String normaliseEmail(String value) {
        if (value == null) {
            return null;
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        return EMAIL.matcher(email).matches() ? email : null;
    }

    /** Missing or unparseable timestamps are unknown, not errors: the record still counts, it just ranks last. */
    private static Instant parseUpdatedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
