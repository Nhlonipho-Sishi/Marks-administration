package model;

/** System user - either a Lecturer or the HoD. Immutable value object. */
public class User {
    private final int id;
    private final String username;
    private final String fullName;
    private final Role role;

    public User(int id, String username, String fullName, Role role) {
        this.id = id; this.username = username; this.fullName = fullName; this.role = role;
    }
    public int getId()          { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public Role getRole()       { return role; }
    public boolean isHod()      { return role == Role.HOD; }

    @Override public String toString() { return fullName + " (" + role + ")"; }
}
