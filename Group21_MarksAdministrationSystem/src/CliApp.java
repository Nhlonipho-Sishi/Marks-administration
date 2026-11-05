import java.nio.file.*;
import java.util.*;
import model.*;
import model.Module;
import service.*;

/**
 * Group 21 - Marks Administration System
 * Console (CLI) mode. Role-based menus: Lecturer (own modules) vs HoD (all modules).
 */
public class CliApp {
    private static Scanner sc;
    private static User currentUser;
    private static final AuthService auth = new AuthService();
    private static final MarksService marks = new MarksService();
    private static final CsvService csv = new CsvService();

    public static void run(Scanner in) {
        sc = in;
        System.out.println("===============================================");
        System.out.println("  CPUT Marks Administration System - Group 21");
        System.out.println("===============================================");
        auth.seedDefaults();
        if (!entryMenu()) return;

        boolean running = true;
        while (running) {
            if (currentUser.isHod()) running = hodMenu();
            else                     running = lecturerMenu();
        }
        csv.shutdown();
        System.out.println("Goodbye.");
    }

    /** First screen: log in, create an account, or exit. Returns true once someone is logged in. */
    private static boolean entryMenu() {
        while (true) {
            System.out.println("\n1. Login        2. Create account        0. Exit");
            switch (readInt("Choice: ")) {
                case 1 -> { return login(); }          // 3 failed attempts ends the program, as before
                case 2 -> registerFlow();
                case 0 -> { return false; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static void registerFlow() {
        System.out.println("\n--- CREATE ACCOUNT ---");
        System.out.print("Full name: ");
        String name = sc.nextLine().trim();
        System.out.print("Username: ");
        String user = sc.nextLine().trim();
        System.out.print("Password (min " + AuthService.MIN_PASSWORD_LENGTH + " characters): ");
        String pw = sc.nextLine();
        System.out.print("Confirm password: ");
        if (!pw.equals(sc.nextLine())) { System.out.println("Error: passwords do not match."); return; }
        int r = readInt("Register as (1 = Lecturer, 2 = Head of Department): ");
        if (r != 1 && r != 2) { System.out.println("Invalid choice."); return; }
        Role role = r == 2 ? Role.HOD : Role.LECTURER;
        String code = "";
        if (role == Role.HOD) {
            System.out.print("HoD access code: ");
            code = sc.nextLine();
        }
        try {
            User u = auth.register(name, user, pw, role, code);
            System.out.printf("Account created for %s (%s). You can now log in.%n", u.getFullName(), u.getRole());
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static boolean login() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim();
            System.out.print("Password: ");
            String p = sc.nextLine();
            Optional<User> found = auth.login(u, p);
            if (found.isPresent()) {
                currentUser = found.get();
                System.out.printf("Welcome %s (%s)%n", currentUser.getFullName(), currentUser.getRole());
                return true;
            }
            System.out.println("Invalid credentials. Attempts left: " + (3 - attempt));
        }
        System.out.println("Login failed.");
        return false;
    }

    private static boolean lecturerMenu() {
        System.out.println("\n--- LECTURER DASHBOARD (" + currentUser.getFullName() + ") ---");
        System.out.println("1. My modules        2. Open module        0. Logout");
        int c = readInt("Choice: ");
        switch (c) {
            case 1 -> listModules(false);
            case 2 -> openModule(false);
            case 0 -> { return false; }
            default -> System.out.println("Invalid choice.");
        }
        return true;
    }

    private static boolean hodMenu() {
        System.out.println("\n=== HoD DASHBOARD (ALL MODULES) ===");
        System.out.println("1. All modules summary      2. Open module (any)");
        System.out.println("3. Missing entries (all)    4. Summary report");
        System.out.println("5. Manage modules (add / remove / assign to lecturers)");
        System.out.println("0. Logout");
        int c = readInt("Choice: ");
        switch (c) {
            case 1 -> listModules(true);
            case 2 -> openModule(true);
            case 3 -> reportMissingAll();
            case 4 -> printSummary();
            case 5 -> manageModulesMenu();
            case 0 -> { return false; }
            default -> System.out.println("Invalid choice.");
        }
        return true;
    }

    // ------------------------------------------------------------ HoD: module administration
    private static void manageModulesMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- MANAGE MODULES ---");
            System.out.println("1. List modules        2. Add module");
            System.out.println("3. Assign / reassign lecturer        4. Remove module        0. Back");
            switch (readInt("Choice: ")) {
                case 1 -> listModules(true);
                case 2 -> addModuleFlow();
                case 3 -> assignModuleFlow();
                case 4 -> removeModuleFlow();
                case 0 -> back = true;
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    /** Show all lecturers and let the HoD pick one by ID; null if none / invalid. */
    private static User pickLecturer() {
        List<User> lecturers = auth.listLecturers();
        if (lecturers.isEmpty()) {
            System.out.println("No lecturer accounts exist yet. Lecturers can use 'Create account' at the start screen.");
            return null;
        }
        System.out.printf("%-6s %-20s %s%n", "ID", "Username", "Name");
        for (User u : lecturers) System.out.printf("%-6d %-20s %s%n", u.getId(), u.getUsername(), u.getFullName());
        int id = readInt("Lecturer ID: ");
        for (User u : lecturers) if (u.getId() == id) return u;
        System.out.println("No lecturer with that ID.");
        return null;
    }

    private static void addModuleFlow() {
        System.out.print("Module code (e.g. SDN260S): ");
        String code = sc.nextLine().trim();
        System.out.print("Module name: ");
        String name = sc.nextLine().trim();
        User lecturer = pickLecturer();
        if (lecturer == null) return;
        try {
            marks.addModule(currentUser, code, name, lecturer.getId());
            System.out.println("Module added and assigned to " + lecturer.getFullName() + ".");
        } catch (IllegalArgumentException | SecurityException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void assignModuleFlow() {
        listModules(true);
        int id = readInt("Module ID: ");
        Optional<Module> mod = marks.getModule(id);
        if (mod.isEmpty()) { System.out.println("Module not found."); return; }
        System.out.println("Currently assigned to: " + mod.get().getLecturerName());
        User lecturer = pickLecturer();
        if (lecturer == null) return;
        try {
            marks.assignModule(currentUser, id, lecturer.getId());
            System.out.println(mod.get().getCode() + " is now assigned to " + lecturer.getFullName() + ".");
        } catch (IllegalArgumentException | SecurityException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void removeModuleFlow() {
        listModules(true);
        int id = readInt("Module ID to remove: ");
        Optional<Module> mod = marks.getModule(id);
        if (mod.isEmpty()) { System.out.println("Module not found."); return; }
        int[] n = marks.moduleCounts(id);
        System.out.printf("This permanently deletes %s with %d enrolment(s), %d assessment(s) and all marks.%n",
                mod.get().getCode(), n[0], n[1]);
        System.out.println("Student records themselves are kept. This cannot be undone.");
        System.out.print("Type the module code (" + mod.get().getCode() + ") to confirm, or press Enter to cancel: ");
        if (!sc.nextLine().trim().equalsIgnoreCase(mod.get().getCode())) {
            System.out.println("Cancelled - nothing was deleted.");
            return;
        }
        try {
            marks.removeModule(currentUser, id);
            System.out.println("Module removed.");
        } catch (IllegalArgumentException | SecurityException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void listModules(boolean all) {
        List<Module> mods = marks.listModules(all ? null : currentUser);
        if (mods.isEmpty()) { System.out.println("No modules found."); return; }
        System.out.printf("%-6s %-12s %-35s %-20s%n", "ID", "Code", "Name", "Lecturer");
        for (Module m : mods)
            System.out.printf("%-6d %-12s %-35s %-20s%n",
                    m.getId(), m.getCode(), m.getName(), m.getLecturerName());
    }

    private static void openModule(boolean hodCanPickAny) {
        listModules(hodCanPickAny);
        int id = readInt("Module ID: ");
        Optional<Module> mod = marks.getModule(id);
        if (mod.isEmpty() || (mod.get().getLecturerId() != currentUser.getId() && !currentUser.isHod())) {
            System.out.println("Module not found or access denied.");
            return;
        }
        moduleMenu(mod.get());
    }

    private static void moduleMenu(Module m) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- MODULE " + m.getCode() + " : " + m.getName() + " ---");
            System.out.println("1. View marksheet (totals & grades)");
            System.out.println("2. Add student / enrol");
            System.out.println("3. Add assessment (test + weight)");
            System.out.println("4. Import marks from CSV");
            System.out.println("5. Export marks to CSV");
            System.out.println("6. Missing entries report");
            System.out.println("7. Enter / edit a mark manually");
            System.out.println("0. Back");
            switch (readInt("Choice: ")) {
                case 1 -> printMarksheet(m);
                case 2 -> addStudentFlow(m);
                case 3 -> addAssessmentFlow(m);
                case 4 -> importFlow(m);
                case 5 -> exportFlow(m);
                case 6 -> reportMissing(m);
                case 7 -> enterMarkFlow(m);
                case 0 -> back = true;
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static void printMarksheet(Module m) {
        List<String[]> rows = marks.marksheet(m.getId());
        List<Assessment> asmt = marks.assessments(m.getId());
        Map<String, Map<Integer, Double>> raw = marks.rawMarks(m.getId());

        // header: Student No | Name | one column per assessment | Weighted% | Grade | Flags
        StringBuilder head = new StringBuilder(String.format("%-11s %-24s", "StudentNo", "Name"));
        for (Assessment a : asmt)
            head.append(String.format(" %-16s", a.getName() + "(" + fmt(a.getWeightPct()) + "%)"));
        head.append(String.format(" %-10s %-6s %s", "Weighted%", "Grade", "Flags"));
        System.out.println(head);
        System.out.println("-".repeat(head.length()));

        for (String[] r : rows) {                       // r = [studentNo, name, weighted, grade, flags]
            Map<Integer, Double> mine = raw.getOrDefault(r[0], Map.of());
            StringBuilder line = new StringBuilder(String.format("%-11s %-24s", r[0], cut(r[1], 24)));
            for (Assessment a : asmt) {
                Double mark = mine.get(a.getId());
                line.append(String.format(" %-16s", mark == null ? "MISSING" : fmt(mark)));
            }
            line.append(String.format(" %-10s %-6s %s", r[2], r[3], r[4]));
            System.out.println(line);
        }
        System.out.println(rows.size() + " students.");
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    private static void addStudentFlow(Module m) {
        System.out.print("Student number: ");
        String num = sc.nextLine().trim().toUpperCase();
        System.out.print("Full name: ");
        String name = sc.nextLine().trim();
        try {
            marks.addStudentAndEnrol(m.getId(), num, name, currentUser.getId());
            System.out.println("Student added and enrolled.");
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void addAssessmentFlow(Module m) {
        System.out.print("Assessment name (e.g. Test 2): ");
        String name = sc.nextLine().trim();
        double max = readDouble("Max marks: ");
        double w   = readDouble("Weight % (remaining: " + String.format("%.1f", marks.remainingWeight(m.getId())) + "): ");
        try {
            marks.addAssessment(m.getId(), name, max, w, currentUser.getId());
            System.out.println("Assessment added.");
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /** Type marks in one at a time; leave the student number blank to stop. */
    private static void enterMarkFlow(Module m) {
        List<Assessment> asmt = marks.assessments(m.getId());
        if (asmt.isEmpty()) { System.out.println("Add an assessment first (option 3)."); return; }
        System.out.println("\nAssessments:");
        for (int i = 0; i < asmt.size(); i++)
            System.out.printf("  %d. %s (out of %s, %s%%)%n", i + 1, asmt.get(i).getName(),
                    fmt(asmt.get(i).getMaxMarks()), fmt(asmt.get(i).getWeightPct()));
        int pick;
        do { pick = readInt("Choose assessment (1-" + asmt.size() + "): "); }
        while (pick < 1 || pick > asmt.size());
        Assessment a = asmt.get(pick - 1);
        while (true) {
            System.out.print("Student number (blank to stop): ");
            String num = sc.nextLine().trim().toUpperCase();
            if (num.isEmpty()) return;
            double mark = readDouble("Mark for " + a.getName() + " (0-" + fmt(a.getMaxMarks()) + "): ");
            try {
                marks.setMark(m.getId(), num, a.getId(), mark, currentUser.getId());
                System.out.println("Saved: " + num + " " + a.getName() + " = " + fmt(mark));
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (RuntimeException e) {                      // database / connection problems
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                System.out.println("Could not save the mark: " + root.getMessage());
            }
        }
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.2f", d);
    }

    private static void importFlow(Module m) {
        System.out.print("CSV file path: ");
        Path p = Paths.get(sc.nextLine().trim());
        if (!Files.exists(p)) { System.out.println("File not found."); return; }
        System.out.print("Importing on worker thread");
        try {
            var future = csv.importAsync(m.getId(), p, currentUser.getId());
            while (!future.isDone()) { System.out.print("."); Thread.sleep(300); }
            ImportResult r = future.get();
            System.out.printf("%nDone: %d marks updated, %d new students enrolled, %d cells skipped.%n",
                    r.updated, r.created, r.skipped);
            r.warnings.forEach(w -> System.out.println("  ! " + w));
        } catch (Exception e) {
            System.out.println("Import failed: " + e.getMessage());
        }
    }

    private static void exportFlow(Module m) {
        System.out.print("Output file path: ");
        Path p = Paths.get(sc.nextLine().trim());
        try {
            var future = csv.exportAsync(m.getId(), p, currentUser.getId());
            while (!future.isDone()) { System.out.print("."); Thread.sleep(250); }
            future.get();
            System.out.println("\nExported to " + p.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("Export failed: " + e.getMessage());
        }
    }

    private static void reportMissing(Module m) {
        List<String> missing = marks.missingEntries(m.getId());
        if (missing.isEmpty()) { System.out.println("No missing entries - well done."); return; }
        System.out.println("MISSING ENTRIES in " + m.getCode() + ":");
        missing.forEach(s -> System.out.println("  [FLAG] " + s));
    }

    private static void reportMissingAll() {
        List<String> all = new ArrayList<>();
        for (Module m : marks.listModules(null)) all.addAll(marks.missingEntries(m.getId()));
        if (all.isEmpty()) { System.out.println("No missing entries across all modules."); return; }
        all.forEach(s -> System.out.println("  [FLAG] " + s));
    }

    private static void printSummary() {
        for (String line : marks.hodSummary()) System.out.println(line);
    }

    private static int readInt(String prompt) {
        System.out.print(prompt);
        while (true) {
            try { return Integer.parseInt(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.print("Enter a number: "); }
        }
    }

    private static double readDouble(String prompt) {
        System.out.print(prompt);
        while (true) {
            try { return Double.parseDouble(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.print("Enter a number: "); }
        }
    }
}
