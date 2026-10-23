package exercise;

import java.time.LocalDate;
import java.util.List;

/**
 * Consolidates demographic records from several source systems into one record per patient.
 * <p>
 * Identity: two records are the same patient when their trimmed, whitespace-collapsed, case-folded first name
 * and last name and their date of birth are equal. No fuzzy matching.
 * <p>
 * Precedence: name and date of birth come from the most trusted source ({@link SourceSystem} order, ties by
 * newest); phone and email each come from the newest record that has a valid value (ties by trust). A record
 * whose {@code updatedAt} is missing or unparseable ranks last.
 * <p>
 * Records that cannot be matched or traced (blank name, bad date of birth, unknown source, blank MRN) are
 * rejected with a reason and never abort the batch. An invalid phone or email drops that field only.
 */
public final class PatientConsolidation {

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
        throw new UnsupportedOperationException("not implemented");
    }
}
