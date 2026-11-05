package gui;

import model.*;
import model.Module;
import service.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Group 21 - Marks Administration System
 * Swing GUI mode. Re-uses the exact same service layer as the CLI
 * (AuthService, MarksService, CsvService), so behaviour is identical:
 * Lecturers see only their own modules, the HoD sees everything.
 *
 * All database / file work runs on SwingWorker threads so the window never freezes.
 */
public class GuiApp {

    private static final AuthService auth  = new AuthService();
    private static final MarksService marks = new MarksService();
    private static final CsvService csv     = new CsvService();

    private static final Color BRAND   = new Color(0x1F3A5F);
    private static final Color FLAG_BG = new Color(0xFFE3E3);
    private static final Color ERR_FG  = new Color(0xFF9A9A);   // readable on the dark theme
    private static final Color OK_FG   = new Color(0x8FE3A4);
    private static final Color NAVY      = new Color(0x1B2250);
    private static final Color TEAL      = new Color(0x2A7B8E);
    private static final Color GOLD      = new Color(0xF5A800);
    private static final Color GOLD_SOFT = new Color(0xFFD36B);
    private static final String KEEP = "keepColor";

    // ------------------------------------------------------------------ entry point
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new LoginFrame().setVisible(true);
        });
    }

    // ------------------------------------------------------------------ helpers
    /** Run a job off the Event Dispatch Thread, then deliver the result back on it. */
    private static <T> void async(Callable<T> job, Consumer<T> onOk, Consumer<Throwable> onFail) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return job.call(); }
            @Override protected void done() {
                try { onOk.accept(get()); }
                catch (ExecutionException e) { onFail.accept(e.getCause() == null ? e : e.getCause()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }.execute();
    }

    private static String messageOf(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String m = root.getMessage();
        return (m == null || m.isBlank()) ? root.getClass().getSimpleName() : m;
    }

    private static void error(Component parent, String title, Throwable t) {
        JOptionPane.showMessageDialog(parent, messageOf(t), title, JOptionPane.ERROR_MESSAGE);
    }

    private static void showText(Component parent, String title, String text) {
        JTextArea area = new JTextArea(text, 18, 80);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(parent, new JScrollPane(area), title, JOptionPane.PLAIN_MESSAGE);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Combo box of lecturers showing "Full Name (username)"; preselects the given user id if present. */
    private static JComboBox<User> lecturerBox(List<User> lecturers, int selectId) {
        JComboBox<User> box = new JComboBox<>(lecturers.toArray(new User[0]));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                                    boolean sel, boolean focus) {
                String text = v instanceof User u ? u.getFullName() + "  (" + u.getUsername() + ")" : "";
                return super.getListCellRendererComponent(l, text, i, sel, focus);
            }
        });
        for (int i = 0; i < lecturers.size(); i++)
            if (lecturers.get(i).getId() == selectId) box.setSelectedIndex(i);
        return box;
    }

    private static JTable readOnlyTable(String... columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(model);
        t.setRowHeight(24);
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        t.getTableHeader().setReorderingAllowed(false);
        t.setRowHeight(26);
        t.setSelectionBackground(TEAL);
        t.setSelectionForeground(Color.WHITE);
        t.setGridColor(new Color(0xD9DEE8));
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel,
                                                                     boolean focus, int row, int col) {
                super.getTableCellRendererComponent(tb, v, false, false, row, col);
                setBackground(NAVY);
                setForeground(Color.WHITE);
                setFont(getFont().deriveFont(Font.BOLD));
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return this;
            }
        });
        return t;
    }

    private static void fill(JTable t, List<String[]> rows) {
        DefaultTableModel m = (DefaultTableModel) t.getModel();
        m.setRowCount(0);
        for (String[] r : rows) m.addRow(r);
    }

    // =================================================================== THEME
    /** Navy-to-teal diagonal gradient with a faint "CPUT" watermark. */
    private static class GradientPanel extends JPanel {
        GradientPanel(LayoutManager lm) { super(lm); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, NAVY, w, h, TEAL));
            g.fillRect(0, 0, w, h);
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(40, (int) Math.min(h * 0.8, w * 0.34)));
            g.setFont(f);
            g.setColor(new Color(255, 255, 255, 14));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("CPUT", (w - fm.stringWidth("CPUT")) / 2, (h + fm.getAscent() - fm.getDescent()) / 2 + h / 8);
            g.dispose();
        }
    }

    /** Translucent rounded "glass" card. */
    private static class Tile extends JPanel {
        private boolean hover;
        Tile() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        }
        void setHover(boolean h) { hover = h; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(255, 255, 255, hover ? 66 : 40));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.setColor(new Color(255, 255, 255, 120));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.dispose();
        }
    }

    /** Gold pill button with navy text. */
    private static class PillButton extends JButton {
        PillButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setForeground(NAVY);
            setFont(getFont().deriveFont(Font.BOLD));
            setBorder(BorderFactory.createEmptyBorder(7, 18, 7, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(!isEnabled() ? new Color(255, 255, 255, 70)
                     : getModel().isRollover() ? GOLD_SOFT : GOLD);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Small gold text-only button (used for Logout). */
    private static JButton linkButton(String text, ActionListener l) {
        JButton b = new JButton(text);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(GOLD);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(l);
        return b;
    }

    /** White badge with the CPUT wordmark (or cput_logo.png from src/gui if you add one) and a gold underline. */
    private static JComponent logoBadge(int width) {
        int height = width * 5 / 13;
        final java.net.URL url = GuiApp.class.getResource("/gui/cput_logo.png");
        final Image img = url == null ? null : new ImageIcon(url).getImage();
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight(), bar = Math.max(4, h / 14);
                g.setColor(Color.WHITE);
                g.fillRoundRect(0, 0, w, h, 20, 20);
                g.setColor(GOLD);
                g.fillRect(0, h - bar, w, bar);
                if (img != null) {
                    int ih = img.getHeight(null), iw = img.getWidth(null);
                    double sc = Math.min((w - 16.0) / iw, (h - bar - 12.0) / ih);
                    int dw = (int) (iw * sc), dh = (int) (ih * sc);
                    g.drawImage(img, (w - dw) / 2, (h - bar - dh) / 2, dw, dh, null);
                } else {
                    g.setColor(NAVY);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, h * 42 / 100));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString("CPUT", (w - fm.stringWidth("CPUT")) / 2, h * 52 / 100);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(8, h * 11 / 100)));
                    fm = g.getFontMetrics();
                    String a = "Cape Peninsula University", b = "of Technology";
                    g.drawString(a, (w - fm.stringWidth(a)) / 2, h * 70 / 100);
                    g.drawString(b, (w - fm.stringWidth(b)) / 2, h * 70 / 100 + fm.getHeight() - 2);
                }
                g.dispose();
            }
        };
        c.setPreferredSize(new Dimension(width, height));
        c.setMinimumSize(new Dimension(width, height));
        return c;
    }

    /** Make a whole component tree see-through with white text, so the gradient shows. */
    private static void theme(Container root) {
        for (Component k : root.getComponents()) {
            if (k instanceof JTable || k instanceof JComboBox || k instanceof javax.swing.text.JTextComponent
                    || k instanceof JScrollPane || k instanceof JButton) continue;
            if (k instanceof JPanel p) p.setOpaque(false);
            if (k instanceof JLabel l && l.getClientProperty(KEEP) == null) l.setForeground(Color.WHITE);
            if (k instanceof Container c) theme(c);
        }
    }

    private static JLabel keep(JLabel l) { l.putClientProperty(KEEP, Boolean.TRUE); return l; }

    /** "L. Ngwenya (Lecturer)" -> "L. Ngwenya"; nothing left to show when the name is just the role. */
    private static String whoText(User u) {
        String role = u.isHod() ? "Head of Department" : "Lecturer";
        String n = u.getFullName().replaceAll("\\s*\\((?:Lecturer|HoD|Head of Department)\\)\\s*$", "").trim();
        return n.isEmpty() || n.equalsIgnoreCase(role) ? role : n + "  •  " + role;
    }

    /** Logo badge + title on the left, user and Logout on the right. */
    private static JComponent themedHeader(User user, ActionListener logout) {
        JPanel bar = new JPanel(new BorderLayout(18, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(18, 28, 8, 28));
        JLabel title = new JLabel("Marks Administration System");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(logoBadge(150));
        left.add(title);
        JLabel who = new JLabel(whoText(user), SwingConstants.RIGHT);
        who.setForeground(Color.WHITE);
        who.setFont(who.getFont().deriveFont(Font.PLAIN, 14f));
        JButton out = linkButton("Logout", logout);
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 2));
        right.setOpaque(false);
        JPanel outRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        outRow.setOpaque(false);
        outRow.add(out);
        right.add(who);
        right.add(outRow);
        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.setOpaque(false);
        rightWrap.add(right, BorderLayout.NORTH);
        bar.add(left, BorderLayout.WEST);
        bar.add(rightWrap, BorderLayout.EAST);
        return bar;
    }

    // =================================================================== LOGIN
    private static class LoginFrame extends JFrame {
        private final JTextField userField = new JTextField(18);
        private final JPasswordField passField = new JPasswordField(18);
        private final JButton loginBtn = new PillButton("Login");
        private final JButton registerBtn = new PillButton("Create account");
        private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
        private int attempts = 0;

        LoginFrame() {
            super("Marks Administration System - Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            GradientPanel root = new GradientPanel(new BorderLayout());
            setContentPane(root);

            JLabel title = new JLabel("Marks Administration System", SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
            title.setForeground(Color.WHITE);
            JLabel sub = new JLabel("Group 21", SwingConstants.CENTER);
            sub.setForeground(GOLD);
            JPanel banner = new JPanel();
            banner.setOpaque(false);
            banner.setLayout(new BoxLayout(banner, BoxLayout.Y_AXIS));
            banner.setBorder(BorderFactory.createEmptyBorder(26, 20, 6, 20));
            JComponent badge = logoBadge(190);
            badge.setMaximumSize(badge.getPreferredSize());
            badge.setAlignmentX(Component.CENTER_ALIGNMENT);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            sub.setAlignmentX(Component.CENTER_ALIGNMENT);
            banner.add(badge);
            banner.add(Box.createVerticalStrut(14));
            banner.add(title);
            banner.add(Box.createVerticalStrut(2));
            banner.add(sub);

            JPanel form = new Tile();
            form.setLayout(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(22, 34, 16, 34));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(6, 6, 6, 6);
            g.anchor = GridBagConstraints.WEST;
            g.gridx = 0; g.gridy = 0; form.add(new JLabel("Username:"), g);
            g.gridx = 1;              form.add(userField, g);
            g.gridx = 0; g.gridy = 1; form.add(new JLabel("Password:"), g);
            g.gridx = 1;              form.add(passField, g);
            g.gridx = 0; g.gridy = 2; g.gridwidth = 2; g.anchor = GridBagConstraints.CENTER;
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            buttons.add(loginBtn);
            buttons.add(registerBtn);
            form.add(buttons, g);
            g.gridy = 3; status.setForeground(ERR_FG);
            keep(status);
            form.add(status, g);

            JPanel formWrap = new JPanel(new GridBagLayout());
            formWrap.setOpaque(false);
            formWrap.setBorder(BorderFactory.createEmptyBorder(8, 40, 30, 40));
            formWrap.add(form);

            add(banner, BorderLayout.NORTH);
            add(formWrap, BorderLayout.CENTER);
            theme(root);
            getRootPane().setDefaultButton(loginBtn);
            loginBtn.addActionListener(e -> doLogin());
            registerBtn.addActionListener(e -> {
                RegisterDialog d = new RegisterDialog(this);
                d.setVisible(true);                       // modal - returns when closed
                if (d.getCreatedUsername() != null) {
                    userField.setText(d.getCreatedUsername());
                    passField.setText("");
                    status.setForeground(OK_FG);
                    status.setText("Account created - please log in.");
                    passField.requestFocusInWindow();
                }
            });

            // make sure default accounts exist (same as CLI start-up)
            loginBtn.setEnabled(false);
            registerBtn.setEnabled(false);
            status.setForeground(new Color(0xDDE6F0));
            status.setText("Connecting to database...");
            async(() -> { auth.seedDefaults(); return Boolean.TRUE; },
                  ok -> { loginBtn.setEnabled(true); registerBtn.setEnabled(true); status.setText(" "); status.setForeground(ERR_FG); },
                  err -> {
                      JOptionPane.showMessageDialog(this,
                          "Could not connect to the database:\n" + messageOf(err)
                          + "\n\nCheck that MySQL is running and schema.sql has been executed.",
                          "Database error", JOptionPane.ERROR_MESSAGE);
                      csv.shutdown();
                      System.exit(1);
                  });

            pack();
            setSize(Math.max(getWidth(), 560), Math.max(getHeight(), 520));
            setMinimumSize(getSize());
            setLocationRelativeTo(null);
            userField.requestFocusInWindow();
        }

        private void doLogin() {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());
            status.setForeground(ERR_FG);
            if (u.isEmpty() || p.isEmpty()) { status.setText("Enter username and password."); return; }
            loginBtn.setEnabled(false);
            status.setText(" ");
            async(() -> auth.login(u, p),
                  found -> {
                      if (found.isPresent()) {
                          new Dashboard(found.get()).setVisible(true);
                          dispose();
                          return;
                      }
                      attempts++;
                      passField.setText("");
                      if (attempts >= 3) {
                          JOptionPane.showMessageDialog(this, "Login failed 3 times. The application will close.",
                                  "Login failed", JOptionPane.ERROR_MESSAGE);
                          csv.shutdown();
                          System.exit(0);
                      }
                      status.setText("Invalid credentials. Attempts left: " + (3 - attempts));
                      loginBtn.setEnabled(true);
                  },
                  err -> { status.setText("Login error: " + messageOf(err)); loginBtn.setEnabled(true); });
        }
    }

    // =================================================================== REGISTER
    private static class RegisterDialog extends JDialog {
        private final JTextField nameField = new JTextField(20);
        private final JTextField userField = new JTextField(20);
        private final JPasswordField passField = new JPasswordField(20);
        private final JPasswordField confirmField = new JPasswordField(20);
        private final JComboBox<String> roleBox =
                new JComboBox<>(new String[]{"Lecturer", "Head of Department (HoD)"});
        private final JLabel codeLabel = new JLabel("HoD access code:");
        private final JPasswordField codeField = new JPasswordField(20);
        private final JLabel msg = new JLabel(" ");
        private final JButton createBtn = new PillButton("Create account");
        private String createdUsername;

        RegisterDialog(Frame owner) {
            super(owner, "Create account", true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            GradientPanel rootPane = new GradientPanel(new BorderLayout());
            setContentPane(rootPane);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(5, 5, 5, 5);
            g.anchor = GridBagConstraints.WEST;
            int row = 0;
            row = addRow(form, g, row, "Full name:", nameField);
            row = addRow(form, g, row, "Username:", userField);
            row = addRow(form, g, row, "Password:", passField);
            row = addRow(form, g, row, "Confirm password:", confirmField);
            row = addRow(form, g, row, "Register as:", roleBox);
            g.gridx = 0; g.gridy = row; form.add(codeLabel, g);
            g.gridx = 1;                form.add(codeField, g);
            row++;
            JLabel hint = new JLabel("<html><small>Username: 3-30 letters, digits, _ or .  |  Password: at least "
                    + AuthService.MIN_PASSWORD_LENGTH + " characters</small></html>");
            g.gridx = 0; g.gridy = row; g.gridwidth = 2; form.add(hint, g);
            row++;
            msg.setForeground(ERR_FG);
            keep(msg);
            g.gridy = row; form.add(msg, g);

            JButton cancel = new PillButton("Cancel");
            cancel.addActionListener(e -> dispose());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            buttons.add(createBtn);
            buttons.add(cancel);

            add(form, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(createBtn);
            theme(rootPane);

            roleBox.addActionListener(e -> updateCodeVisibility());
            createBtn.addActionListener(e -> submit());
            updateCodeVisibility();
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        private static int addRow(JPanel form, GridBagConstraints g, int row, String label, JComponent field) {
            g.gridwidth = 1;
            g.gridx = 0; g.gridy = row; form.add(new JLabel(label), g);
            g.gridx = 1;                form.add(field, g);
            return row + 1;
        }

        private void updateCodeVisibility() {
            boolean hod = roleBox.getSelectedIndex() == 1;
            codeLabel.setVisible(hod);
            codeField.setVisible(hod);
            pack();
        }

        String getCreatedUsername() { return createdUsername; }

        private void submit() {
            String pw = new String(passField.getPassword());
            if (!pw.equals(new String(confirmField.getPassword()))) {
                msg.setText("Passwords do not match.");
                return;
            }
            Role role = roleBox.getSelectedIndex() == 1 ? Role.HOD : Role.LECTURER;
            String name = nameField.getText();
            String user = userField.getText();
            String code = new String(codeField.getPassword());
            msg.setText(" ");
            createBtn.setEnabled(false);
            async(() -> auth.register(name, user, pw, role, code),
                  created -> {
                      createdUsername = created.getUsername();
                      JOptionPane.showMessageDialog(this, "Account created for " + created.getFullName()
                              + " (" + (created.isHod() ? "HoD" : "Lecturer") + ").\nYou can now log in.");
                      dispose();
                  },
                  err -> { msg.setText(messageOf(err)); createBtn.setEnabled(true); });
        }
    }

    // =================================================================== DASHBOARD
    private static class Dashboard extends JFrame {
        private final User user;
        private final JComboBox<Module> moduleBox = new JComboBox<>();
        private final JPanel detailHolder = new JPanel(new CardLayout());
        private final JLabel emptyLabel = new JLabel("Pick a module on the dashboard first.", SwingConstants.CENTER);
        private final Map<Integer, ModulePanel> panels = new HashMap<>();
        private List<Module> modules = new ArrayList<>();
        private final JPanel cards = new JPanel(new CardLayout());
        private final JLabel moduleVal = statValue("-");
        private int statSeq = 0;
        private final JLabel studentsVal = statValue("-");
        private final JLabel assessmentsVal = statValue("-");
        private final JLabel missingVal = statValue("-");

        Dashboard(User user) {
            super(user.isHod() ? "HoD Dashboard - All Modules" : "Lecturer Dashboard");
            this.user = user;
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { csv.shutdown(); System.exit(0); }
            });

            GradientPanel rootPane = new GradientPanel(new BorderLayout());
            setContentPane(rootPane);
            add(buildHeader(), BorderLayout.NORTH);
            cards.setOpaque(false);

            moduleBox.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                                        boolean sel, boolean focus) {
                    String text = v instanceof Module m
                            ? m.getCode() + " : " + m.getName() + "   (" + m.getLecturerName() + ")"
                            : "No modules available";
                    return super.getListCellRendererComponent(l, text, i, sel, focus);
                }
            });

            detailHolder.add(emptyLabel, "empty");
            ((CardLayout) detailHolder.getLayout()).show(detailHolder, "empty");

            // Students tab = the marks table only (plus a way back)
            JButton back = new PillButton("\u2190 Back to dashboard");
            back.addActionListener(e -> showCard("dash"));
            JPanel backBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 28, 6));
            backBar.add(back);
            JPanel studentsTab = new JPanel(new BorderLayout());
            studentsTab.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            studentsTab.add(backBar, BorderLayout.NORTH);
            studentsTab.add(detailHolder, BorderLayout.CENTER);

            if (user.isHod()) {
                cards.add(buildHodOverview(), "dash");            // HoD: management blocks only
            } else {
                cards.add(buildOverview(), "dash");
                cards.add(studentsTab, "students");
            }
            add(cards, BorderLayout.CENTER);
            theme(rootPane);

            setSize(1150, 680);
            setLocationRelativeTo(null);
            loadModules();
        }

        private void showCard(String name) { ((CardLayout) cards.getLayout()).show(cards, name); }

        private static JLabel statValue(String text) {
            JLabel l = new JLabel(text);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 30f));
            l.setForeground(GOLD_SOFT);
            l.putClientProperty(KEEP, Boolean.TRUE);
            return l;
        }

        private JPanel tile(String caption, JComponent value, Runnable onClick) {
            Tile t = new Tile();
            JLabel cap = new JLabel(caption);
            cap.setForeground(Color.WHITE);
            t.add(cap, BorderLayout.NORTH);
            t.add(value, BorderLayout.SOUTH);
            if (onClick != null) {
                t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                t.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
                    @Override public void mouseEntered(MouseEvent e) { t.setHover(true); }
                    @Override public void mouseExited(MouseEvent e)  { t.setHover(false); }
                });
            }
            return t;
        }

        /** Value label for action blocks (+ / arrows): white instead of gold. */
        private static JLabel iconValue(String text) {
            JLabel l = statValue(text);
            l.setForeground(Color.WHITE);
            return l;
        }

        private static JLabel sectionLabel(String text) {
            JLabel l = new JLabel(text);
            l.setForeground(GOLD);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 16f));
            l.putClientProperty(KEEP, Boolean.TRUE);
            return l;
        }

        private JButton button(String text, ActionListener l) {
            JButton b = new PillButton(text);
            b.addActionListener(l);
            return b;
        }

        /** Clicking the Module block: pick another module when the lecturer has more than one. */
        private void chooseModule() {
            if (modules.size() < 2) return;
            Object before = moduleBox.getSelectedItem();
            int ok = JOptionPane.showConfirmDialog(this, moduleBox, "Select module",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) moduleBox.setSelectedItem(before);
        }

        /** The module chosen in the drop-down, or null (with a message) if there is none. */
        private Module chosen() {
            Module m = (Module) moduleBox.getSelectedItem();
            if (m == null) JOptionPane.showMessageDialog(this, "Select a module first.");
            return m;
        }

        /** Get (or lazily build) the marks panel for a module. */
        private ModulePanel panelFor(Module m) {
            ModulePanel panel = panels.get(m.getId());
            if (panel == null) {
                panel = new ModulePanel(this, m, user);
                panels.put(m.getId(), panel);
                detailHolder.add(panel, "m" + m.getId());
            }
            return panel;
        }

        /** Students button: show the marks table of the chosen module. */
        private void openStudents() {
            Module m = chosen();
            if (m == null) return;
            // same access rule as the CLI
            if (m.getLecturerId() != user.getId() && !user.isHod()) {
                JOptionPane.showMessageDialog(this, "Module not found or access denied.");
                return;
            }
            ModulePanel panel = panelFor(m);
            ((CardLayout) detailHolder.getLayout()).show(detailHolder, "m" + m.getId());
            panel.refresh();
            showCard("students");
        }

        /** Landing page: statistic tiles, module picker and the action buttons. */
        private JComponent buildOverview() {
            JPanel page = new JPanel(new BorderLayout(0, 12));
            page.setOpaque(false);
            page.setBorder(BorderFactory.createEmptyBorder(10, 28, 22, 28));
            page.add(sectionLabel("System Overview"), BorderLayout.NORTH);

            moduleVal.setFont(moduleVal.getFont().deriveFont(Font.BOLD, 20f));
            moduleBox.addActionListener(e -> showModuleInfo());

            JPanel grid = new JPanel(new GridLayout(2, 5, 14, 14));
            grid.setOpaque(false);
            grid.add(tile("Module", moduleVal, this::chooseModule));
            grid.add(tile("Students", studentsVal, this::openStudents));
            grid.add(tile("Assessments", assessmentsVal, null));
            grid.add(tile("Missing Entries", missingVal, () -> { Module m = chosen(); if (m != null) panelFor(m).missingReport(); }));
            grid.add(tile("Add student",    iconValue("+"),        () -> { Module m = chosen(); if (m != null) panelFor(m).addStudent(); }));
            grid.add(tile("Add assessment", iconValue("+"),        () -> { Module m = chosen(); if (m != null) panelFor(m).addAssessment(); }));
            grid.add(tile("Enter mark",     iconValue("\u270E"),   () -> { Module m = chosen(); if (m != null) panelFor(m).enterMark(null); }));
            grid.add(tile("Import Student Marks CSV", iconValue("\u2191"),   () -> { Module m = chosen(); if (m != null) panelFor(m).importCsv(); }));
            grid.add(tile("Export Student Marks CSV", iconValue("\u2193"),   () -> { Module m = chosen(); if (m != null) panelFor(m).exportCsv(); }));
            page.add(grid, BorderLayout.CENTER);

            JPanel south = new JPanel(new GridLayout(0, 1, 0, 6));
            south.setOpaque(false);
            JPanel pick = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
            pick.setOpaque(false);
            pick.add(button("Refresh", e -> loadModules()));
            south.add(pick);

            page.add(south, BorderLayout.SOUTH);
            return page;
        }

        private final JLabel hodMissingVal = statValue("-");

        /** HoD landing page: management blocks only, Refresh at the bottom. */
        private JComponent buildHodOverview() {
            JPanel page = new JPanel(new BorderLayout(0, 12));
            page.setOpaque(false);
            page.setBorder(BorderFactory.createEmptyBorder(10, 28, 22, 28));
            page.add(sectionLabel("HoD Overview"), BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(1, 3, 14, 14));
            grid.setOpaque(false);
            grid.add(tile("Manage modules (add / remove / assign)", iconValue("\u2699"),
                    () -> new ManageModulesDialog(this, user).setVisible(true)));
            grid.add(tile("Missing entries (all modules)", hodMissingVal, () -> async(() -> {
                            List<String> all = new ArrayList<>();
                            for (Module m : marks.listModules(null)) all.addAll(marks.missingEntries(m.getId()));
                            return all;
                        },
                        all -> showText(this, "Missing entries - all modules",
                                all.isEmpty() ? "No missing entries across all modules."
                                              : String.join("\n", all.stream().map(x -> "[FLAG] " + x).toList())),
                        err -> error(this, "Report failed", err))));
            grid.add(tile("Reports", iconValue("\u2261"), this::openReports));
            page.add(grid, BorderLayout.CENTER);

            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
            south.setOpaque(false);
            south.add(button("Refresh", e -> loadModules()));
            page.add(south, BorderLayout.SOUTH);
            return page;
        }

        /** Reports block: choose between the module report and the student report. */
        private void openReports() {
            Object[] options = { "Module report", "Student report", "Cancel" };
            int choice = JOptionPane.showOptionDialog(this, "Which report would you like to open?", "Reports",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (choice == 0)
                async(marks::hodSummary,
                      lines -> showText(this, "Module report", String.join("\n", lines)),
                      err -> error(this, "Report failed", err));
            else if (choice == 1)
                new StudentReportDialog(this, user).setVisible(true);
        }

        private void updateHodStats(List<Module> list) {
            async(() -> { int n = 0; for (Module m : list) n += marks.missingEntries(m.getId()).size(); return n; },
                  n -> hodMissingVal.setText(String.valueOf(n)),
                  err -> hodMissingVal.setText("?"));
        }

        /** Update the Module tile and the numbers for the module chosen in the drop-down. */
        private void showModuleInfo() {
            Module m = (Module) moduleBox.getSelectedItem();
            int seq = ++statSeq;
            if (m == null) {
                moduleVal.setText("No module");
                studentsVal.setText("0"); assessmentsVal.setText("0"); missingVal.setText("0");
                return;
            }
            moduleVal.setText("<html>" + esc((m.getCode() + ": " + m.getName()).toUpperCase()) + "</html>");
            async(() -> {
                      int[] c = marks.moduleCounts(m.getId());
                      return new int[]{ c[0], c[1], marks.missingEntries(m.getId()).size() };
                  },
                  r -> { if (seq != statSeq) return;
                         studentsVal.setText(String.valueOf(r[0]));
                         assessmentsVal.setText(String.valueOf(r[1]));
                         missingVal.setText(String.valueOf(r[2])); },
                  err -> { if (seq != statSeq) return;
                           studentsVal.setText("?"); assessmentsVal.setText("?"); missingVal.setText("?"); });
        }

        private JComponent buildHeader() {
            return themedHeader(user, e -> { dispose(); new LoginFrame().setVisible(true); });
        }

        /** Called after the HoD adds / removes / reassigns modules: drop stale cached panels and reload. */
        void modulesChanged() {
            for (ModulePanel mp : panels.values()) detailHolder.remove(mp);
            panels.clear();
            ((CardLayout) detailHolder.getLayout()).show(detailHolder, "empty");
            loadModules();
        }

        private void loadModules() {
            async(() -> marks.listModules(user.isHod() ? null : user),
                  list -> {
                      Module prev = (Module) moduleBox.getSelectedItem();
                      modules = list;
                      if (!user.isHod())
                          emptyLabel.setText(list.isEmpty()
                                  ? "<html><center>No modules are assigned to you yet.<br>"
                                    + "Ask the Head of Department to assign one, then press Refresh.</center></html>"
                                  : "Pick a module on the dashboard first.");
                      moduleBox.setModel(new DefaultComboBoxModel<>(list.toArray(new Module[0])));
                      if (prev != null)
                          for (Module m : list) if (m.getId() == prev.getId()) moduleBox.setSelectedItem(m);
                      if (user.isHod()) updateHodStats(list); else showModuleInfo();
                  },
                  err -> error(this, "Could not load modules", err));
        }
    }

    // =================================================================== STUDENT REPORT (HoD)
    private static class StudentReportDialog extends JDialog {
        private final User user;
        private final JTextField numberField = new JTextField(14);
        private final JLabel heading = new JLabel("Enter a student number and press Search.");
        private final JTable table = readOnlyTable("Module", "Assessment", "Mark", "Out of", "Weight %", "Result");
        private final JButton exportBtn = new PillButton("Export CSV");
        private MarksService.StudentReport current;

        StudentReportDialog(Frame owner, User user) {
            super(owner, "Student report", true);
            this.user = user;
            GradientPanel rootPane = new GradientPanel(new BorderLayout());
            setContentPane(rootPane);
            table.setAutoCreateRowSorter(false);

            JButton search = new PillButton("Search");
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            top.add(new JLabel("Student number:"));
            top.add(numberField);
            top.add(search);
            JPanel north = new JPanel(new BorderLayout());
            north.add(top, BorderLayout.NORTH);
            heading.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
            heading.setFont(heading.getFont().deriveFont(Font.BOLD));
            north.add(heading, BorderLayout.SOUTH);

            JButton close = new PillButton("Close");
            close.addActionListener(e -> dispose());
            exportBtn.setEnabled(false);
            exportBtn.addActionListener(e -> export());
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            bottom.add(exportBtn); bottom.add(close);

            search.addActionListener(e -> search());
            numberField.addActionListener(e -> search());

            add(north, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
            setSize(820, 520);
            setLocationRelativeTo(owner);
            theme(rootPane);
        }

        private void search() {
            String n = numberField.getText().trim();
            if (n.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a student number."); return; }
            async(() -> marks.studentReport(n),
                  opt -> {
                      DefaultTableModel m = (DefaultTableModel) table.getModel();
                      m.setRowCount(0);
                      if (opt.isEmpty()) {
                          current = null; exportBtn.setEnabled(false);
                          heading.setText("No student found with number " + n + ".");
                          return;
                      }
                      current = opt.get();
                      exportBtn.setEnabled(true);
                      heading.setText(current.number + " - " + current.name
                              + "   (" + current.modules.size() + " module" + (current.modules.size() == 1 ? "" : "s") + ")");
                      for (MarksService.ModuleLine ml : current.modules) {
                          String label = ml.code + " : " + ml.name;
                          for (String[] a : ml.assessments)
                              m.addRow(new Object[]{ label, a[0], a[1], a[2], a[3], "" });
                          m.addRow(new Object[]{ label, "MODULE TOTAL", "", "", "",
                                  String.format(Locale.ROOT, "%.2f%%  (%s)", ml.weighted, ml.grade) });
                      }
                  },
                  err -> error(this, "Search failed", err));
        }

        private void export() {
            if (current == null) return;
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Export student report to CSV");
            fc.setSelectedFile(new java.io.File(current.number + "_report.csv"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path target = fc.getSelectedFile().toPath();
            if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                    target.getFileName() + " already exists. Overwrite?", "Confirm overwrite",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            MarksService.StudentReport rep = current;
            async(() -> marks.exportStudentReport(rep, target, user.getId()),
                  done -> JOptionPane.showMessageDialog(this, "Exported to:\n" + target.toAbsolutePath()),
                  err -> error(this, "Export failed", err));
        }
    }

    // =================================================================== MANAGE MODULES (HoD)
    private static class ManageModulesDialog extends JDialog {
        private final Dashboard dash;
        private final User hod;
        private final JTable table = readOnlyTable("Code", "Module", "Lecturer");
        private final JLabel status = new JLabel(" ");
        private List<Module> modules = new ArrayList<>();

        ManageModulesDialog(Dashboard dash, User hod) {
            super(dash, "Manage modules", true);
            GradientPanel rootPane = new GradientPanel(new BorderLayout());
            setContentPane(rootPane);
            this.dash = dash;
            this.hod = hod;
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            JButton add = new PillButton("Add module");
            JButton assign = new PillButton("Assign / reassign lecturer");
            JButton remove = new PillButton("Remove module");
            JButton close = new PillButton("Close");
            add.addActionListener(e -> addModule());
            assign.addActionListener(e -> reassign());
            remove.addActionListener(e -> removeSelected());
            close.addActionListener(e -> dispose());
            buttons.add(add); buttons.add(assign); buttons.add(remove);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
            bottom.add(status, BorderLayout.CENTER);
            bottom.add(close, BorderLayout.EAST);

            JPanel center = new JPanel(new BorderLayout());
            center.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            center.add(new JScrollPane(table), BorderLayout.CENTER);

            add(buttons, BorderLayout.NORTH);
            add(center, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
            setSize(760, 440);
            setLocationRelativeTo(dash);
            theme(rootPane);
            reload();
        }

        private void reload() {
            async(() -> marks.listModules(null),
                  list -> {
                      modules = list;
                      DefaultTableModel m = (DefaultTableModel) table.getModel();
                      m.setRowCount(0);
                      for (Module mod : list)
                          m.addRow(new Object[]{ mod.getCode(), mod.getName(), mod.getLecturerName() });
                  },
                  err -> error(this, "Could not load modules", err));
        }

        private Module selected() {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Select a module in the table first.");
                return null;
            }
            return modules.get(table.convertRowIndexToModel(viewRow));
        }

        private void changed(String message) {
            status.setText(message);
            reload();
            dash.modulesChanged();
        }

        private void withLecturers(Consumer<List<User>> then) {
            async(auth::listLecturers,
                  lecturers -> {
                      if (lecturers.isEmpty()) {
                          JOptionPane.showMessageDialog(this,
                                  "No lecturer accounts exist yet.\nLecturers can create an account from the login screen.");
                          return;
                      }
                      then.accept(lecturers);
                  },
                  err -> error(this, "Could not load lecturers", err));
        }

        private void addModule() {
            withLecturers(lecturers -> {
                JTextField code = new JTextField(12);
                JTextField name = new JTextField(24);
                JComboBox<User> box = lecturerBox(lecturers, -1);
                JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
                p.add(new JLabel("Module code (e.g. SDN260S):")); p.add(code);
                p.add(new JLabel("Module name:"));                p.add(name);
                p.add(new JLabel("Assign to lecturer:"));         p.add(box);
                if (JOptionPane.showConfirmDialog(this, p, "Add module", JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
                User chosen = (User) box.getSelectedItem();
                String c = code.getText(), n = name.getText();
                async(() -> { marks.addModule(hod, c, n, chosen.getId()); return Boolean.TRUE; },
                      ok -> changed("Module " + c.trim().toUpperCase() + " added and assigned to " + chosen.getFullName() + "."),
                      err -> error(this, "Could not add module", err));
            });
        }

        private void reassign() {
            Module m = selected();
            if (m == null) return;
            withLecturers(lecturers -> {
                JComboBox<User> box = lecturerBox(lecturers, m.getLecturerId());
                JPanel p = new JPanel(new GridLayout(2, 1, 4, 6));
                p.add(new JLabel(m.getCode() + " - " + m.getName() + "  (currently: " + m.getLecturerName() + ")"));
                p.add(box);
                if (JOptionPane.showConfirmDialog(this, p, "Assign module", JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
                User chosen = (User) box.getSelectedItem();
                async(() -> { marks.assignModule(hod, m.getId(), chosen.getId()); return Boolean.TRUE; },
                      ok -> changed(m.getCode() + " assigned to " + chosen.getFullName() + "."),
                      err -> error(this, "Could not assign module", err));
            });
        }

        private void removeSelected() {
            Module m = selected();
            if (m == null) return;
            async(() -> marks.moduleCounts(m.getId()),
                  n -> {
                      String text = "<html>Delete module <b>" + esc(m.getCode()) + "</b> (" + esc(m.getName()) + ")?<br><br>"
                              + "This permanently removes its " + n[0] + " enrolment(s), " + n[1]
                              + " assessment(s) and all marks.<br>Student records themselves are kept. "
                              + "This cannot be undone.</html>";
                      int c = JOptionPane.showOptionDialog(this, text, "Remove module",
                              JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                              new String[]{"Delete module", "Cancel"}, "Cancel");
                      if (c != 0) return;
                      async(() -> { marks.removeModule(hod, m.getId()); return Boolean.TRUE; },
                            ok -> changed("Module " + m.getCode() + " removed."),
                            err -> error(this, "Could not remove module", err));
                  },
                  err -> error(this, "Could not check module", err));
        }
    }

    // =================================================================== MODULE PANEL
    private record Sheet(List<String[]> rows, List<Assessment> assessments, double remaining,
                         Map<String, Map<Integer, Double>> raw) { }

    private static class ModulePanel extends JPanel {
        private final Component owner;
        private final Module module;
        private final User user;
        private final JTable table = readOnlyTable("Student No", "Name", "Email", "Weighted %", "Grade", "Flags");
        private static final String MISSING = "MISSING";
        private final JLabel assessmentsLabel = new JLabel(" ");
        private final JLabel status = new JLabel(" ");
        private final JProgressBar busyBar = new JProgressBar();

        ModulePanel(Component owner, Module module, User user) {
            super(new BorderLayout(0, 8));
            this.owner = owner; this.module = module; this.user = user;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 28, 8, 28));

            JLabel title = new JLabel(module.getCode() + " : " + module.getName() + "   (Lecturer: " + module.getLecturerName() + ")");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
            title.setForeground(GOLD_SOFT);
            keep(title);

            JPanel top = new JPanel(new BorderLayout(0, 4));
            top.add(title, BorderLayout.NORTH);
            top.add(assessmentsLabel, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                         boolean focus, int row, int col) {
                    Component c = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                    if (!sel) {
                        int last = t.getColumnCount() - 1;
                        Object flag = t.getValueAt(row, last);
                        boolean flagged = flag != null && !flag.toString().isBlank();
                        boolean missingCell = MISSING.equals(v);
                        c.setBackground(missingCell ? new Color(0xFFB3B3)
                                      : flagged     ? FLAG_BG : t.getBackground());
                    }
                    return c;
                }
            });
            add(new JScrollPane(table), BorderLayout.CENTER);

            busyBar.setIndeterminate(true);
            busyBar.setVisible(false);
            JPanel bottom = new JPanel(new BorderLayout(8, 0));
            bottom.add(status, BorderLayout.CENTER);
            bottom.add(busyBar, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);
            theme(this);
        }

        private JComponent buildToolbar() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            p.add(button("Add student",    e -> addStudent()));
            p.add(button("Add assessment", e -> addAssessment()));
            p.add(button("Import CSV",     e -> importCsv()));
            p.add(button("Export CSV",     e -> exportCsv()));
            p.add(button("Missing entries", e -> missingReport()));
            p.add(button("Refresh",        e -> refresh()));
            return p;
        }

        private JButton button(String text, ActionListener l) {
            JButton b = new PillButton(text);
            b.addActionListener(l);
            return b;
        }

        private void busy(boolean on, String msg) {
            busyBar.setVisible(on);
            status.setText(msg);
        }

        /** Reload marksheet, assessment summary and remaining weight. */
        void refresh() {
            busy(true, "Loading...");
            async(() -> new Sheet(marks.marksheet(module.getId()),
                                  marks.assessments(module.getId()),
                                  marks.remainingWeight(module.getId()),
                                  marks.rawMarks(module.getId())),
                  s -> {
                      fillMarksheet(s);
                      StringBuilder sb = new StringBuilder("<html><b>Assessments:</b> ");
                      if (s.assessments().isEmpty()) sb.append("none yet");
                      for (int i = 0; i < s.assessments().size(); i++) {
                          Assessment a = s.assessments().get(i);
                          if (i > 0) sb.append(" &nbsp;|&nbsp; ");
                          sb.append(a.getName()).append(" (/").append(trim(a.getMaxMarks()))
                            .append(", ").append(trim(a.getWeightPct())).append("%)");
                      }
                      sb.append(" &nbsp;&nbsp;<b>Weight remaining:</b> ").append(trim(s.remaining())).append("%</html>");
                      assessmentsLabel.setText(sb.toString());
                      long flagged = s.rows().stream().filter(r -> !r[4].isBlank()).count();
                      busy(false, s.rows().size() + " students, " + flagged + " flagged for missing entries.");
                  },
                  err -> { busy(false, " "); error(owner, "Could not load module", err); });
        }

        /** Build the full table: Student No | Name | Email | one column per assessment | Weighted % | Grade | Flags. */
        private void fillMarksheet(Sheet s) {
            List<String> cols = new ArrayList<>(List.of("Student No", "Name", "Email"));
            for (Assessment a : s.assessments())
                cols.add(a.getName() + " (/" + trim(a.getMaxMarks()) + ", " + trim(a.getWeightPct()) + "%)");
            cols.addAll(List.of("Weighted %", "Grade", "Flags"));

            DefaultTableModel m = (DefaultTableModel) table.getModel();
            m.setRowCount(0);
            m.setColumnIdentifiers(cols.toArray());
            for (String[] r : s.rows()) {              // r = [studentNo, name, weighted, grade, flags]
                List<String> row = new ArrayList<>();
                row.add(r[0]);
                row.add(r[1]);
                row.add(r[0].trim() + "@mycput.ac.za");   // student email = studentno@mycput.ac.za
                Map<Integer, Double> mine = s.raw().getOrDefault(r[0], Map.of());
                for (Assessment a : s.assessments()) {
                    Double mark = mine.get(a.getId());
                    row.add(mark == null ? MISSING : markText(mark));
                }
                row.add(r[2]);
                row.add(r[3]);
                row.add(r[4]);
                m.addRow(row.toArray());
            }
            // sensible column widths
            TableColumnModel cm = table.getColumnModel();
            if (cm.getColumnCount() >= 3) {
                cm.getColumn(0).setPreferredWidth(100);
                cm.getColumn(1).setPreferredWidth(180);
                cm.getColumn(2).setPreferredWidth(190);
            }
            table.setAutoResizeMode(cols.size() > 7 ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        }

        private static String markText(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.2f", d);
        }

        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.1f", d);
        }

        private void addStudent() {
            JTextField num = new JTextField(14);
            JTextField name = new JTextField(20);
            JPanel p = new JPanel(new GridLayout(2, 2, 6, 6));
            p.add(new JLabel("Student number:")); p.add(num);
            p.add(new JLabel("Full name:"));      p.add(name);
            if (JOptionPane.showConfirmDialog(owner, p, "Add student", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            String n = num.getText().trim().toUpperCase();
            String f = name.getText().trim();
            if (n.isEmpty() || f.isEmpty()) {
                JOptionPane.showMessageDialog(owner, "Student number and full name are required.");
                return;
            }
            busy(true, "Adding student...");
            async(() -> { marks.addStudentAndEnrol(module.getId(), n, f, user.getId()); return Boolean.TRUE; },
                  ok -> { refresh(); status.setText("Student added and enrolled."); },
                  err -> { busy(false, " "); error(owner, "Could not add student", err); });
        }

        private void addAssessment() {
            JTextField name = new JTextField(16);
            JTextField max = new JTextField(8);
            JTextField weight = new JTextField(8);
            JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
            p.add(new JLabel("Name (e.g. Test 2):")); p.add(name);
            p.add(new JLabel("Max marks:"));          p.add(max);
            p.add(new JLabel("Weight %:"));           p.add(weight);
            if (JOptionPane.showConfirmDialog(owner, p, "Add assessment", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            double mx, w;
            try {
                mx = Double.parseDouble(max.getText().trim());
                w  = Double.parseDouble(weight.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(owner, "Max marks and weight must be numbers.");
                return;
            }
            String n = name.getText().trim();
            busy(true, "Adding assessment...");
            async(() -> { marks.addAssessment(module.getId(), n, mx, w, user.getId()); return Boolean.TRUE; },
                  ok -> { refresh(); status.setText("Assessment added."); },
                  err -> { busy(false, " "); error(owner, "Could not add assessment", err); });
        }

        /** Type a single mark in by hand; reopens for the next student until the lecturer cancels. */
        private void enterMark(Assessment preselect) {
            busy(true, "Loading assessments...");
            async(() -> marks.assessments(module.getId()),
                  list -> {
                      busy(false, " ");
                      if (list.isEmpty()) {
                          JOptionPane.showMessageDialog(owner, "Add an assessment first.");
                          return;
                      }
                      JTextField num = new JTextField(14);
                      JComboBox<Assessment> box = new JComboBox<>(list.toArray(new Assessment[0]));
                      box.setRenderer(new DefaultListCellRenderer() {
                          @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                                                  boolean sel, boolean focus) {
                              String t = v instanceof Assessment a
                                      ? a.getName() + "  (out of " + trim(a.getMaxMarks()) + ")" : "";
                              return super.getListCellRendererComponent(l, t, i, sel, focus);
                          }
                      });
                      if (preselect != null)
                          for (int i = 0; i < list.size(); i++)
                              if (list.get(i).getId() == preselect.getId()) box.setSelectedIndex(i);
                      JTextField mark = new JTextField(8);
                      JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
                      p.add(new JLabel("Student number:")); p.add(num);
                      p.add(new JLabel("Assessment:"));     p.add(box);
                      p.add(new JLabel("Mark:"));           p.add(mark);
                      if (JOptionPane.showConfirmDialog(owner, p, "Enter mark - " + module.getCode(),
                              JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
                      Assessment a = (Assessment) box.getSelectedItem();
                      String n = num.getText().trim().toUpperCase();
                      double v;
                      try { v = Double.parseDouble(mark.getText().trim().replace(',', '.')); }
                      catch (NumberFormatException ex) {
                          JOptionPane.showMessageDialog(owner, "Mark must be a number.");
                          enterMark(a);
                          return;
                      }
                      busy(true, "Saving mark...");
                      async(() -> { marks.setMark(module.getId(), n, a.getId(), v, user.getId()); return Boolean.TRUE; },
                            ok -> { refresh(); status.setText("Saved " + n + " " + a.getName() + " = " + markText(v)); enterMark(a); },
                            err -> { busy(false, " "); error(owner, "Could not save mark", err); enterMark(a); });
                  },
                  err -> { busy(false, " "); error(owner, "Could not load assessments", err); });
        }

        private void importCsv() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Choose CSV file to import");
            if (fc.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
            Path file = fc.getSelectedFile().toPath();
            busy(true, "Importing " + file.getFileName() + " ...");
            async(() -> csv.importAsync(module.getId(), file, user.getId()).get(),
                  (ImportResult r) -> {
                      StringBuilder sb = new StringBuilder();
                      sb.append(String.format("%d marks updated, %d new students enrolled, %d cells skipped.%n",
                              r.updated, r.created, r.skipped));
                      if (!r.warnings.isEmpty()) {
                          sb.append("\nWarnings:\n");
                          r.warnings.forEach(w -> sb.append("  ! ").append(w).append('\n'));
                      }
                      refresh();
                      showText(owner, "Import complete", sb.toString());
                  },
                  err -> { busy(false, " "); error(owner, "Import failed", err); });
        }

        private void exportCsv() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Export marks to CSV");
            fc.setSelectedFile(new java.io.File(module.getCode() + "_marks.csv"));
            if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
            Path target = fc.getSelectedFile().toPath();
            if (Files.exists(target) && JOptionPane.showConfirmDialog(owner,
                    target.getFileName() + " already exists. Overwrite?", "Confirm overwrite",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            busy(true, "Exporting...");
            async(() -> csv.exportAsync(module.getId(), target, user.getId()).get(),
                  done -> {
                      busy(false, "Exported to " + target.toAbsolutePath());
                      JOptionPane.showMessageDialog(owner, "Exported to:\n" + target.toAbsolutePath());
                  },
                  err -> { busy(false, " "); error(owner, "Export failed", err); });
        }

        private void missingReport() {
            busy(true, "Checking for missing entries...");
            async(() -> marks.missingEntries(module.getId()),
                  list -> {
                      busy(false, list.size() + " missing entries.");
                      showText(owner, "Missing entries - " + module.getCode(),
                              list.isEmpty() ? "No missing entries - well done."
                                             : String.join("\n", list.stream().map(s -> "[FLAG] " + s).toList()));
                  },
                  err -> { busy(false, " "); error(owner, "Report failed", err); });
        }
    }
}
