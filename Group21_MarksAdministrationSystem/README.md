# Group 21 – Marks Administration System

A Java (Swing GUI + command-line) application for capturing and managing student marks. Lecturers manage their own modules, and the Head of Department (HoD) oversees all modules. Data is stored in a **MySQL** database (run through **XAMPP**) and accessed with **JDBC**.

---

## Quick summary

1. Install **XAMPP** and start **MySQL**.
2. Import **`schema.sql`** once (this creates the database and the database user).
3. Add the **MySQL Connector/J** jar to the project.
4. **Run `Main.java`.** That is the only file you need to run.

---

## Requirements

| Requirement | Notes |
|---|---|
| **JDK** | The NetBeans project is configured for Java 26 (`javac.source=26`). If you use an older JDK, lower the source/target level under *Project Properties → Sources*. |
| **XAMPP** | Used to run the MySQL server (download from <https://www.apachefriends.org>). Only the **MySQL** module is needed – Apache is optional. |
| **MySQL Connector/J** | The JDBC driver is included in the project as `mysqljdbcdriver.zip` (see below). |
| **IDE** *(recommended)* | Apache NetBeans (the project is a NetBeans Ant project). Any IDE that can add jars to the classpath also works. |

---

## 1. Install the database (XAMPP + MySQL)

### Step 1 – Install and start MySQL

1. Download and install **XAMPP**.
2. Open the **XAMPP Control Panel**.
3. Click **Start** next to **MySQL**. It should turn green and show port **3306**.

> The application expects MySQL on `localhost:3306`. If another MySQL/MariaDB service is already using that port, stop it or change the port in XAMPP.

### Step 2 – Import `schema.sql`

**Option A – phpMyAdmin (easiest)**

1. In the XAMPP Control Panel, click **Admin** next to MySQL (or open <http://localhost/phpmyadmin>). Apache must be running for phpMyAdmin to load.
2. Click the **Import** tab at the top.
3. Click **Choose File** and select `schema.sql` from the project folder.
4. Scroll down and click **Import** (or **Go**).

**Option B – XAMPP Shell / command line**

1. In the XAMPP Control Panel, click **Shell**.
2. Run (adjust the path to where you saved the project):

   ```bash
   mysql -u root -p < "C:\path\to\Group21_MarksAdministrationSystem\schema.sql"
   ```

   XAMPP's `root` user has **no password** by default – just press **Enter** when prompted.

### What `schema.sql` creates

- A database named **`marks_admin`**
- A database user the application connects with:
  - **Username:** `marksuser`
  - **Password:** `markspass`
  - Granted all privileges on `marks_admin` only
- Tables: `users`, `modules`, `students`, `module_students`, `assessments`, `marks`, `audit_log`

You do **not** need to insert any data manually. The first time the app starts and finds an empty `users` table, it seeds the default accounts below automatically.

> **Run `schema.sql` only once.** Running it again on an existing database will fail because the tables already exist. To start fresh, drop the `marks_admin` database in phpMyAdmin and import `schema.sql` again.

---

## 2. Database connector (Java ↔ MySQL)

The project connects to MySQL using **MySQL Connector/J** (the official JDBC driver).

| | |
|---|---|
| **Connector** | MySQL Connector/J – `mysql-connector-j-26.7.0.jar` |
| **Supporting library** | `protobuf-java-4.31.1.jar` |
| **Location** | Inside **`mysqljdbcdriver.zip`** in the project root |
| **JDBC URL** | `jdbc:mysql://localhost:3306/marks_admin?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| **User / password** | `marksuser` / `markspass` |

The connection is configured in `src/db/Database.java`.

### Adding the connector to the project

1. **Unzip** `mysqljdbcdriver.zip` somewhere permanent (for example, next to the project folder).
2. In **NetBeans**: right-click the project → **Properties** → **Libraries** → **Classpath** → **Add JAR/Folder**.
3. Add **both** `mysql-connector-j-26.7.0.jar` and `protobuf-java-4.31.1.jar`.
4. Remove any entries shown with a red/broken-reference warning. The project was originally set up with jar paths from another computer (e.g. `C:\Users\Admin\Downloads\…`), which won't exist on yours.
5. Click **OK**.

*Not using NetBeans?* Add the same two jars to your IDE's classpath, or compile and run from the command line:

```bash
# from the project root, with the jars unzipped into a "lib" folder
javac -cp "lib/*" -d out src/*.java src/*/*.java
java  -cp "out;lib/*" Main        # use ":" instead of ";" on macOS/Linux
```

---

## 3. Run the application

**You only need to run the main file: `src/Main.java`.**

- In NetBeans: right-click **`Main.java`** → **Run File** (or press **Shift + F6**), or simply click **Run Project** (**F6**). `Main` is already set as the project's main class.
- `Main` is the launcher – it starts everything else (database connection, seeding, GUI or CLI). There is no other file to run.

When it starts, you will be asked to choose a mode:

```
Select interface mode:
  1. GUI (graphical window)
  2. CLI (command line)
Choice (1/2):
```

You can also skip the prompt by passing an argument: `java Main gui` or `java Main cli`.

> **Remember:** MySQL must be **running in XAMPP** every time you start the app.

---

## 4. Default login accounts

Created automatically on first run:

| Role | Username | Password |
|---|---|---|
| Head of Department (HoD) | `admin` | `admin123` |
| Lecturer | `lect` | `lect123` |

A demo module (**SDN260S – Software Design II**) is also created and assigned to the demo lecturer.

New users can register from the login screen. Registering as **HoD** requires the access code **`CPUT-HOD-2026`** (override it by setting the `MARKS_HOD_CODE` environment variable).

---

## Features

- Secure login with salted password hashing
- Lecturer and HoD roles
- Create, assign and remove modules (HoD)
- Enrol students and define assessments with weightings
- Capture marks, with weighted totals and grades calculated automatically
- Import marks from CSV and export marksheets to CSV
- Missing-entries report and summary report
- Audit log of key actions

### CSV import format

```
StudentNumber,StudentName,<AssessmentName>,<AssessmentName>,...
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Communications link failure` / `Connection refused` | MySQL isn't running. Start it in the XAMPP Control Panel and make sure it is on port 3306. |
| `Access denied for user 'marksuser'` | `schema.sql` hasn't been imported (or didn't finish). Import it again. |
| `Unknown database 'marks_admin'` | `schema.sql` hasn't been imported. |
| `No suitable driver found` / `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | The Connector/J jar isn't on the classpath. Redo **Adding the connector to the project**. |
| `Seeding failed - did you run schema.sql?` | Import `schema.sql` first, then run the app again. |
| `Table … already exists` when importing | The schema was already imported. Drop the `marks_admin` database first if you want a clean start. |
| Port 3306 already in use | Stop the other MySQL service, or change XAMPP's MySQL port (and update the port in `src/db/Database.java`). |
| Build error about Java version | Set the project's JDK / source level to match the JDK you have installed. |

> **Note:** Recent XAMPP versions bundle **MariaDB**, which is MySQL-compatible. `schema.sql` is written for MySQL 8 but uses standard syntax, so it should import fine on XAMPP's MariaDB. If you see an import error, tell your group and check the exact message in phpMyAdmin.

---

## Project structure

```
Group21_MarksAdministrationSystem/
├── schema.sql                 # Database + tables + DB user (import once)
├── mysqljdbcdriver.zip        # MySQL Connector/J + protobuf jars
├── src/
│   ├── Main.java              # ← RUN THIS (launcher: GUI or CLI)
│   ├── CliApp.java            # Command-line interface
│   ├── db/Database.java       # JDBC connection settings
│   ├── gui/GuiApp.java        # Swing GUI
│   ├── model/                 # User, Role, Module, Student, Assessment
│   ├── service/               # AuthService, MarksService, CsvService, ImportResult
│   └── util/PasswordUtil.java # Salted password hashing
└── nbproject/, build.xml      # NetBeans / Ant project files
```

---

*Group 21 – CPUT Marks Administration System*
