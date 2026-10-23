package exercise;

/** A shift on a care unit floor. A night shift is keyed by the day it starts on. */
public enum Shift {
    /** 07:00 to 19:00 local time; needs two staff per floor. */
    DAY(2),
    /** 19:00 to 07:00 the next morning; needs one staff member per floor. */
    NIGHT(1);

    private final int staffPerFloor;

    Shift(int staffPerFloor) {
        this.staffPerFloor = staffPerFloor;
    }

    public int staffPerFloor() {
        return staffPerFloor;
    }
}
