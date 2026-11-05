package model;

public class Student {
    private final int id;
    private final String studentNumber;
    private final String fullName;

    public Student(int id, String studentNumber, String fullName) {
        this.id = id; this.studentNumber = studentNumber; this.fullName = fullName;
    }
    public int getId()               { return id; }
    public String getStudentNumber() { return studentNumber; }
    public String getFullName()      { return fullName; }
}
