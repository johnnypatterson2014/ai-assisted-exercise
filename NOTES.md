# Exercise notes

Filled in during the first five minutes. Written before any code, read back to the interviewer.

## Problem in my words

- Patient demographic records arrive from several systems (EHR, LAB, CRM), each with its own MRN.
- The same person appears more than once, with different spelling, spacing, case and contact details.
- Return one consolidated record per patient: name, date of birth, best phone, best email, and which source records fed it.
- I must state the identity rule (when two records are the same person) and the precedence rule (which contact details win).
- Records too broken to match are reported separately; the batch never fails.

## Assumptions to confirm (ask these)

1. BLOCKER: identity = exact match on normalised first name + last name + date of birth (trim, case, whitespace)? No fuzzy matching of typos in v1; that is a follow-up with a confidence score.
2. Precedence = field by field: the newest valid value for phone wins, the newest valid value for email wins, independently? Ties and missing updatedAt broken by source trust EHR > LAB > CRM?
3. A record with a blank name or an unparseable date of birth cannot be matched: reject it whole, or let it through as its own patient? (I assume reject.) Is "05/01/1980" May 1 (US order) and acceptable, or reject?
4. An invalid phone or email (sample: "555", "not-an-email") drops that field but keeps the record? Should dropped fields be reported?
5. Output: one consolidated record per patient, with the contributing (source, MRN) pairs, sorted by last name, first name, date of birth?

## Edge cases

- Null or empty: null list -> IllegalArgumentException; empty -> no patients, no rejects; null element -> rejected with its index.
- Invalid types or formats: blank or null first or last name; DOB in ISO, US order, or garbage; phone with punctuation, too short, letters; email without @ or with upper case; updatedAt null or garbage; unknown or blank source system; blank MRN.
- Duplicates: same (source, MRN) twice -> the newer one is used, the older is ignored; identical rows; the same person under three MRNs (the sample).
- Ordering and time: input order never changes the result; updatedAt ties; null updatedAt ranks last; a newer record with a null phone must not erase an older valid phone (field-level merge).
- Boundaries: one record -> one patient; same name different DOB -> two patients; same name and DOB -> one patient (name the risk: two people can share both); phone "(604) 555-0101" and "604-555-0101" are the same phone.
- Scale: 10M records -> hash by match key, O(n) grouping, O(k log k) ranking per patient; memory holds all records; fuzzy matching at that volume needs blocking (by DOB or last-name prefix) and a separate pass.

## Data model and public method

```java
enum SourceSystem { EHR, LAB, CRM }   // declaration order = trust order for ties
record RawRecord(String sourceSystem, String mrn, String firstName, String lastName, String dateOfBirth,
                 String phone, String email, String updatedAt) {}                          // as received
record SourceRef(SourceSystem system, String mrn) {}
record Patient(String firstName, String lastName, LocalDate dateOfBirth, String phone, String email,
               List<SourceRef> sources) {}                                                 // phone/email null when none valid
record RejectedRecord(int index, String reason) {}
record Result(List<Patient> patients, List<RejectedRecord> rejected) {}

public static Result consolidate(List<RawRecord> records)
```

Approach: validate and normalise each record (names trimmed and whitespace-collapsed, DOB parsed, phone to
digits, email lower-cased; invalid phone or email becomes absent); group by match key (lower-cased last name,
first name, DOB); within a group rank by updatedAt descending (null last) then source trust; the top record
gives the display name, the first ranked record with a valid phone gives the phone, likewise email; sources
sorted. Patients sorted by last name, first name, DOB. Time O(n log n) worst case, space O(n).

## Plan

1. Tests for the cases above (red).
2. Smallest clean implementation (green).
3. Harden: validation, error handling, adversarial inputs, Runner for ad hoc inputs.
4. Wrap up: README notes, production next steps.

## Decisions made on the way (and why)

- Interviewer answers not available; proceeding on the assumptions above, made concrete as:
  identity = exact match on trimmed, whitespace-collapsed, case-folded first + last name + ISO date of birth.
  Date of birth must be yyyy-MM-dd; "05/01/1980" is rejected, because guessing day/month order on a field used
  for identity is worse than a visible reject. Invalid phone or email drops the field and keeps the record.
- Name and date of birth come from the most trusted source (EHR > LAB > CRM, ties by newest): the EHR is the
  system of record for demographics. Phone and email come from the newest record with a valid value (ties by
  trust): contact details change often, so recency wins there.
- Missing or unparseable updatedAt does not reject the record; it ranks last. Unknown source system or blank
  MRN rejects, since the record cannot be traced back.
- Same (source, MRN) twice: the newer is used, the older is reported as rejected with reason "superseded", so a
  caller can see it was seen and not lost.
- Phone normalised to ten NANP digits (a leading 1 is dropped); anything else is invalid. Email trimmed and
  lower-cased; must look like local@domain.tld.
- Phase 3: two comparators make the precedence rule explicit in code: FOR_IDENTITY (trust, then recency) picks
  the record that gives the name and date of birth; FOR_CONTACT (recency, then trust) orders the records that
  phone and email are taken from. Both end with the input index so the order is total and the result stable.
- Phase 3: duplicates by (source, MRN) are resolved with FOR_CONTACT too, so the newest wins there as well.
