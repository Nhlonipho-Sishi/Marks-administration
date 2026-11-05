package model;

/** A subject/module owned by exactly one lecturer. */
public class Module {
    private final int id;
    private final String code;
    private final String name;
    private final int lecturerId;
    private final String lecturerName;

    public Module(int id, String code, String name, int lecturerId, String lecturerName) {
        this.id = id; this.code = code; this.name = name;
        this.lecturerId = lecturerId; this.lecturerName = lecturerName;
    }
    public int getId()             { return id; }
    public String getCode()        { return code; }
    public String getName()        { return name; }
    public int getLecturerId()     { return lecturerId; }
    public String getLecturerName(){ return lecturerName; }
}
