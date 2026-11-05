-- ============================================================
-- Group 21 - Marks Administration System  (MySQL 8)
-- ============================================================
CREATE DATABASE IF NOT EXISTS marks_admin
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE marks_admin;

CREATE USER IF NOT EXISTS 'marksuser'@'localhost' IDENTIFIED BY 'markspass';
GRANT ALL PRIVILEGES ON marks_admin.* TO 'marksuser'@'localhost';
FLUSH PRIVILEGES;

CREATE TABLE users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash CHAR(64)     NOT NULL,
    salt          CHAR(16)     NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          ENUM('LECTURER','HOD') NOT NULL
);

CREATE TABLE modules (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(20) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    lecturer_id INT NOT NULL,
    FOREIGN KEY (lecturer_id) REFERENCES users(id)
);

CREATE TABLE students (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    student_number VARCHAR(20) NOT NULL UNIQUE,
    full_name      VARCHAR(100) NOT NULL
);

CREATE TABLE module_students (
    module_id  INT NOT NULL,
    student_id INT NOT NULL,
    PRIMARY KEY (module_id, student_id),
    FOREIGN KEY (module_id)  REFERENCES modules(id)  ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
);

CREATE TABLE assessments (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    module_id  INT NOT NULL,
    name       VARCHAR(100) NOT NULL,
    max_marks  DOUBLE NOT NULL CHECK (max_marks > 0),
    weight_pct DOUBLE NOT NULL CHECK (weight_pct > 0),
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
);

CREATE TABLE marks (
    assessment_id INT NOT NULL,
    student_id    INT NOT NULL,
    mark          DOUBLE,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (assessment_id, student_id),
    FOREIGN KEY (assessment_id) REFERENCES assessments(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id)    REFERENCES students(id)  ON DELETE CASCADE
);

CREATE TABLE audit_log (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT,
    action     VARCHAR(50)  NOT NULL,
    detail     VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_marks_student ON marks(student_id);
CREATE INDEX idx_assess_module ON assessments(module_id);
CREATE INDEX idx_enrol_student ON module_students(student_id);
