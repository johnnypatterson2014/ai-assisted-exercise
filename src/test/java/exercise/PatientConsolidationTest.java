package exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import exercise.PatientConsolidation.Patient;
import exercise.PatientConsolidation.RawRecord;
import exercise.PatientConsolidation.RejectedRecord;
import exercise.PatientConsolidation.Result;
import exercise.PatientConsolidation.SourceRef;

class PatientConsolidationTest {

    private static final String JAN = "2026-01-01T00:00:00Z";
    private static final String FEB = "2026-02-01T00:00:00Z";
    private static final String MAR = "2026-03-01T00:00:00Z";
    private static final LocalDate DOB = LocalDate.of(1980, 5, 1);

    private static RawRecord rec(String source, String mrn, String first, String last, String dob,
            String phone, String email, String updatedAt) {
        return new RawRecord(source, mrn, first, last, dob, phone, email, updatedAt);
    }

    /** Jane Doe, born 1980-05-01, with the given contact details. */
    private static RawRecord jane(String source, String mrn, String phone, String email, String updatedAt) {
        return rec(source, mrn, "Jane", "Doe", "1980-05-01", phone, email, updatedAt);
    }

    private static Result consolidate(List<RawRecord> records) {
        return PatientConsolidation.consolidate(records);
    }

    private static Patient onlyPatient(Result result) {
        assertThat(result.patients()).hasSize(1);
        return result.patients().get(0);
    }

    @Nested
    class HappyPath {

        @Test
        void singleRecordBecomesOnePatient() {
            Result result = consolidate(List.of(jane("EHR", "E100", "604-555-0101", "jane@example.com", JAN)));

            Patient patient = onlyPatient(result);
            assertThat(patient.firstName()).isEqualTo("Jane");
            assertThat(patient.lastName()).isEqualTo("Doe");
            assertThat(patient.dateOfBirth()).isEqualTo(DOB);
            assertThat(patient.phone()).isEqualTo("6045550101");
            assertThat(patient.email()).isEqualTo("jane@example.com");
            assertThat(patient.sources()).containsExactly(new SourceRef(SourceSystem.EHR, "E100"));
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void sameNameAndDateOfBirthAcrossSystemsMergeIntoOnePatient() {
            Result result = consolidate(List.of(
                    jane("EHR", "E100", null, null, JAN),
                    jane("LAB", "L7", null, null, FEB),
                    jane("CRM", "C1", null, null, MAR)));

            assertThat(onlyPatient(result).sources()).containsExactly(
                    new SourceRef(SourceSystem.EHR, "E100"),
                    new SourceRef(SourceSystem.LAB, "L7"),
                    new SourceRef(SourceSystem.CRM, "C1"));
        }

        @Test
        void namesMatchIgnoringCaseAndSurroundingAndRepeatedWhitespace() {
            Result result = consolidate(List.of(
                    rec("EHR", "E1", "Jane", "Doe", "1980-05-01", null, null, JAN),
                    rec("LAB", "L1", "  JANE ", "doe ", "1980-05-01", null, null, FEB),
                    rec("CRM", "C1", "jane", "DOE", "1980-05-01", null, null, MAR)));

            assertThat(onlyPatient(result).sources()).hasSize(3);
        }

        @Test
        void differentDateOfBirthIsADifferentPatient() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", null, null, JAN),
                    rec("LAB", "L1", "Jane", "Doe", "1981-05-01", null, null, JAN)));

            assertThat(result.patients()).hasSize(2);
        }

        @Test
        void newestValidPhoneWins() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", "604-555-0101", null, JAN),
                    jane("CRM", "C1", "604-555-0202", null, FEB)));

            assertThat(onlyPatient(result).phone()).isEqualTo("6045550202");
        }

        @Test
        void newestValidEmailWins() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", null, "old@example.com", JAN),
                    jane("CRM", "C1", null, "new@example.com", FEB)));

            assertThat(onlyPatient(result).email()).isEqualTo("new@example.com");
        }

        @Test
        void nameSpellingComesFromTheMostTrustedSourceEvenWhenOlder() {
            Result result = consolidate(List.of(
                    rec("LAB", "L1", "JANE", "DOE", "1980-05-01", null, null, FEB),
                    rec("EHR", "E1", "Jane", "Doe", "1980-05-01", null, null, JAN)));

            Patient patient = onlyPatient(result);
            assertThat(patient.firstName()).isEqualTo("Jane");
            assertThat(patient.lastName()).isEqualTo("Doe");
        }

        @Test
        void sampleInputFromTheStatement() {
            List<RawRecord> sample = List.of(
                    rec("EHR", "E100", "Jane", "Doe", "1980-05-01", "(604) 555-0101", null, JAN),
                    rec("LAB", "L7", " JANE", "doe ", "1980-05-01", null, "Jane.Doe@Example.com", FEB),
                    rec("CRM", "C1", "Jane", "", "05/01/1980", "555", "not-an-email", null));

            Result result = consolidate(sample);

            Patient patient = onlyPatient(result);
            assertThat(patient.firstName()).isEqualTo("Jane");
            assertThat(patient.lastName()).isEqualTo("Doe");
            assertThat(patient.dateOfBirth()).isEqualTo(DOB);
            assertThat(patient.phone()).isEqualTo("6045550101");
            assertThat(patient.email()).isEqualTo("jane.doe@example.com");
            assertThat(patient.sources()).containsExactly(
                    new SourceRef(SourceSystem.EHR, "E100"),
                    new SourceRef(SourceSystem.LAB, "L7"));
            assertThat(result.rejected()).extracting(RejectedRecord::index).containsExactly(2);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyInputGivesNoPatientsAndNoRejects() {
            Result result = consolidate(List.of());

            assertThat(result.patients()).isEmpty();
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void newerRecordWithNoPhoneDoesNotEraseAnOlderValidPhone() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", "604-555-0101", null, JAN),
                    jane("CRM", "C1", null, null, FEB)));

            assertThat(onlyPatient(result).phone()).isEqualTo("6045550101");
        }

        @Test
        void invalidPhoneIsDroppedAndTheRecordIsKept() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", "604-555-0101", null, JAN),
                    jane("CRM", "C1", "555", null, FEB)));

            assertThat(onlyPatient(result).phone()).isEqualTo("6045550101");
            assertThat(onlyPatient(result).sources()).hasSize(2);
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void invalidEmailIsDroppedAndTheRecordIsKept() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", null, "jane@example.com", JAN),
                    jane("CRM", "C1", null, "not-an-email", FEB)));

            assertThat(onlyPatient(result).email()).isEqualTo("jane@example.com");
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        void noValidContactGivesNullPhoneAndEmail() {
            Result result = consolidate(List.of(jane("EHR", "E1", "555", "nope", JAN)));

            Patient patient = onlyPatient(result);
            assertThat(patient.phone()).isNull();
            assertThat(patient.email()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"(604) 555-0101", "604-555-0101", "604.555.0101", "6045550101", "+1 604 555 0101",
                "1-604-555-0101", " 604 555 0101 "})
        void phoneFormatsNormaliseToTenDigits(String phone) {
            Result result = consolidate(List.of(jane("EHR", "E1", phone, null, JAN)));

            assertThat(onlyPatient(result).phone()).isEqualTo("6045550101");
        }

        @Test
        void emailIsTrimmedAndLowerCased() {
            Result result = consolidate(List.of(jane("EHR", "E1", null, "  Jane.Doe@Example.COM ", JAN)));

            assertThat(onlyPatient(result).email()).isEqualTo("jane.doe@example.com");
        }

        @Test
        void missingUpdatedAtRanksLast() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", "604-555-0101", null, null),
                    jane("CRM", "C1", "604-555-0202", null, JAN)));

            assertThat(onlyPatient(result).phone()).isEqualTo("6045550202");
        }

        @Test
        void tieOnUpdatedAtIsBrokenBySourceTrust() {
            Result result = consolidate(List.of(
                    jane("CRM", "C1", "604-555-0303", null, JAN),
                    jane("LAB", "L1", "604-555-0202", null, JAN),
                    jane("EHR", "E1", "604-555-0101", null, JAN)));

            assertThat(onlyPatient(result).phone()).isEqualTo("6045550101");
        }

        @Test
        void duplicateSourceAndMrnKeepsTheNewerAndReportsTheOlder() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", "604-555-0101", null, JAN),
                    jane("EHR", "E1", "604-555-0202", null, FEB)));

            assertThat(onlyPatient(result).phone()).isEqualTo("6045550202");
            assertThat(onlyPatient(result).sources()).containsExactly(new SourceRef(SourceSystem.EHR, "E1"));
            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isZero();
                assertThat(rejected.reason()).containsIgnoringCase("superseded");
            });
        }

        @Test
        void inputOrderDoesNotChangeTheResult() {
            List<RawRecord> input = List.of(
                    jane("EHR", "E1", "604-555-0101", null, JAN),
                    jane("LAB", "L1", null, "jane@example.com", FEB),
                    rec("CRM", "C1", "John", "Doe", "1975-01-01", "604-555-0909", null, MAR));
            List<RawRecord> reversed = new ArrayList<>(input);
            Collections.reverse(reversed);

            assertThat(consolidate(reversed).patients()).isEqualTo(consolidate(input).patients());
        }

        @Test
        void patientsAreSortedByLastNameThenFirstNameThenDateOfBirth() {
            Result result = consolidate(List.of(
                    rec("EHR", "E1", "Zoe", "Adams", "1990-01-01", null, null, JAN),
                    rec("EHR", "E2", "Amy", "Baker", "1990-01-01", null, null, JAN),
                    rec("EHR", "E3", "Amy", "Adams", "1995-01-01", null, null, JAN),
                    rec("EHR", "E4", "Amy", "Adams", "1990-01-01", null, null, JAN)));

            assertThat(result.patients()).extracting(Patient::lastName, Patient::firstName, Patient::dateOfBirth)
                    .containsExactly(
                            tuple("Adams", "Amy", LocalDate.of(1990, 1, 1)),
                            tuple("Adams", "Amy", LocalDate.of(1995, 1, 1)),
                            tuple("Adams", "Zoe", LocalDate.of(1990, 1, 1)),
                            tuple("Baker", "Amy", LocalDate.of(1990, 1, 1)));
        }

        @Test
        void inputListIsNotMutated() {
            List<RawRecord> input = new ArrayList<>(List.of(
                    jane("LAB", "L1", null, null, FEB),
                    jane("EHR", "E1", null, null, JAN)));
            List<RawRecord> copy = List.copyOf(input);

            consolidate(input);

            assertThat(input).containsExactlyElementsOf(copy);
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void nullListIsRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> PatientConsolidation.consolidate(null))
                    .withMessageContaining("records");
        }

        @Test
        void nullElementIsRejectedWithItsIndexAndTheBatchContinues() {
            List<RawRecord> input = Arrays.asList(jane("EHR", "E1", null, null, JAN), null);

            Result result = consolidate(input);

            assertThat(result.patients()).hasSize(1);
            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isEqualTo(1);
                assertThat(rejected.reason()).contains("null");
            });
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(nullValues = "NULL", value = {
                "blank first name,   EHR,  E1,   '  ', Doe,  1980-05-01, first name",
                "null last name,     EHR,  E1,   Jane, NULL, 1980-05-01, last name",
                "us date of birth,   EHR,  E1,   Jane, Doe,  05/01/1980, date of birth",
                "garbage dob,        EHR,  E1,   Jane, Doe,  yesterday,  date of birth",
                "null dob,           EHR,  E1,   Jane, Doe,  NULL,       date of birth",
                "unknown source,     HR,   E1,   Jane, Doe,  1980-05-01, source",
                "blank source,       '  ', E1,   Jane, Doe,  1980-05-01, source",
                "blank mrn,          EHR,  '  ', Jane, Doe,  1980-05-01, mrn"
        })
        void unusableRecordIsRejectedWithAReason(String name, String source, String mrn, String first,
                String last, String dob, String expectedInReason) {
            Result result = consolidate(List.of(rec(source, mrn, first, last, dob, null, null, JAN)));

            assertThat(result.patients()).isEmpty();
            assertThat(result.rejected()).singleElement().satisfies(rejected -> {
                assertThat(rejected.index()).isZero();
                assertThat(rejected.reason()).containsIgnoringCase(expectedInReason);
            });
        }

        @Test
        void garbageUpdatedAtIsTreatedAsUnknownNotRejected() {
            Result result = consolidate(List.of(
                    jane("CRM", "C1", "604-555-0202", null, "last week"),
                    jane("EHR", "E1", "604-555-0101", null, JAN)));

            assertThat(result.rejected()).isEmpty();
            assertThat(onlyPatient(result).phone()).isEqualTo("6045550101");
            assertThat(onlyPatient(result).sources()).hasSize(2);
        }

        @Test
        void rejectedRecordDoesNotHideTheOthers() {
            Result result = consolidate(List.of(
                    jane("EHR", "E1", null, null, JAN),
                    rec("EHR", "E2", "", "", "", null, null, JAN),
                    rec("EHR", "E3", "John", "Doe", "1975-01-01", null, null, JAN)));

            assertThat(result.patients()).hasSize(2);
            assertThat(result.rejected()).hasSize(1);
        }
    }
}
