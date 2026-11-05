import java.util.Scanner;

/**
 * Group 21 - Marks Administration System
 * Launcher: asks the user to choose GUI or CLI mode, then starts that mode.
 * You can also skip the question:  java Main gui   |   java Main cli
 */
public class Main {
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].trim().toLowerCase() : "";
        Scanner in = new Scanner(System.in);

        while (!mode.equals("gui") && !mode.equals("cli")) {
            System.out.println("===============================================");
            System.out.println("  CPUT Marks Administration System - Group 21");
            System.out.println("===============================================");
            System.out.println("Select interface mode:");
            System.out.println("  1. GUI (graphical window)");
            System.out.println("  2. CLI (command line)");
            System.out.print("Choice (1/2): ");
            if (!in.hasNextLine()) return;
            String c = in.nextLine().trim().toLowerCase();
            if (c.equals("1") || c.equals("gui") || c.equals("g")) mode = "gui";
            else if (c.equals("2") || c.equals("cli") || c.equals("c")) mode = "cli";
            else System.out.println("Please enter 1 or 2.\n");
        }

        if (mode.equals("gui")) {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                System.out.println("No display available - starting CLI mode instead.");
                CliApp.run(in);
            } else {
                gui.GuiApp.launch();   // Swing takes over; the console is no longer needed
            }
        } else {
            CliApp.run(in);
        }
    }
}
