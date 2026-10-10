
CREATE DATABASE IF NOT EXISTS api_quality_guard
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'aqg'@'localhost' IDENTIFIED BY 'your-password';

GRANT ALL PRIVILEGES ON api_quality_guard.* TO 'aqg'@'localhost';

FLUSH PRIVILEGES;
