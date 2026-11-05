package service;

import db.Database;
import model.*;
import util.PasswordUtil;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Login, self-registration and one-time seeding of default accounts. */
public class AuthService {

    public static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * Access code a person must enter to register as Head of Department, so that
     * nobody can simply sign themselves up with full access. Override it with the
     * MARKS_HOD_CODE environment variable; change the default before handing in.
     */
    private static final String HOD_ACCESS_CODE =
            System.getenv("MARKS_HOD_CODE") != null ? System.getenv("MARKS_HOD_CODE") : "CPUT-HOD-2026";

    /**
     * Create a new account (Lecturer or HoD) and store it with a salted hash.
     * Throws IllegalArgumentException with a user-friendly message if anything is invalid.
     */
    public User register(String fullName, String username, String password, Role role, String hodCode) {
        String name = fullName == null ? "" : fullName.trim();
        String user = username == null ? "" : username.trim();
        if (name.length() < 2 || name.length() > 100)
            throw new IllegalArgumentException("Full name must be 2-100 characters.");
        if (!user.matches("[A-Za-z0-9_.]{3,30}"))
            throw new IllegalArgumentException("Username must be 3-30 characters: letters, digits, '_' or '.'.");
        if (password == null || password.length() < MIN_PASSWORD_LENGTH)
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        if (role == null)
            throw new IllegalArgumentException("Choose a role.");
        if (role == Role.HOD && !HOD_ACCESS_CODE.equals(hodCode == null ? "" : hodCode.trim()))
            throw new IllegalArgumentException("Invalid Head of Department access code.");

        try (Connection c = Database.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
                    ps.setString(1, user);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new IllegalArgumentException("That username is already taken.");
                    }
                }
                int id = insertUser(c, user, password, name, role);
                Database.audit(c, id, "REGISTER", "New " + role + " account: " + user);
                c.commit();
                return new User(id, user, name, role);
            } catch (SQLIntegrityConstraintViolationException e) {   // lost a race on the UNIQUE index
                c.rollback();
                throw new IllegalArgumentException("That username is already taken.");
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while creating the account", e);
        }
    }

    /** All lecturer accounts, for the HoD's "assign module" pickers. */
    public List<User> listLecturers() {
        List<User> out = new ArrayList<>();
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username, full_name FROM users WHERE role = 'LECTURER' ORDER BY full_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                out.add(new User(rs.getInt(1), rs.getString(2), rs.getString(3), Role.LECTURER));
        } catch (SQLException e) {
            throw new RuntimeException("Could not load lecturers", e);
        }
        return out;
    }

    /** Verify username/password against the salted hash. */
    public Optional<User> login(String username, String password) {
        String sql = "SELECT id, username, full_name, role, password_hash, salt FROM users WHERE username = ?";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                if (!PasswordUtil.verify(password, rs.getString("salt"), rs.getString("password_hash")))
                    return Optional.empty();
                Role role = Role.valueOf(rs.getString("role"));
                return Optional.of(new User(rs.getInt("id"), rs.getString("username"),
                                            rs.getString("full_name"), role));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during login", e);
        }
    }

    /** Create HoD (admin/admin123) and a demo lecturer (lect/lect123) if the users table is empty. */
    public void seedDefaults() {
        try (Connection c = Database.get()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                if (rs.getInt(1) > 0) return;
            }
            c.setAutoCommit(false);
            try {
                int hod  = insertUser(c, "admin", "admin123", "Head of Department", Role.HOD);
                int lect = insertUser(c, "lect",  "lect123",  "L. Ngwenya (Lecturer)", Role.LECTURER);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO modules(code, name, lecturer_id) VALUES (?,?,?)")) {
                    ps.setString(1, "SDN260S");
                    ps.setString(2, "Software Design II");
                    ps.setInt(3, lect);
                    ps.executeUpdate();
                }
                Database.audit(c, hod, "SEED", "Default accounts and demo module created");
                c.commit();
                System.out.println("[seed] Created admin/admin123 (HoD) and lect/lect123 (Lecturer), module SDN260S.");
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Seeding failed - did you run schema.sql?", e);
        }
    }

    private int insertUser(Connection c, String username, String password,
                           String fullName, Role role) throws SQLException {
        String salt = PasswordUtil.newSalt();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users(username, password_hash, salt, full_name, role) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(password, salt));
            ps.setString(3, salt);
            ps.setString(4, fullName);
            ps.setString(5, role.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }
}
