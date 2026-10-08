# CampusFlow - College Management System

CampusFlow is an integrated college platform built with Java 21, Spring Boot, MySQL, and a modern responsive HTML/CSS/JavaScript frontend (Thymeleaf).

## Features
- **Role-based Authentication**: Secure access for Admin, Faculty, and Students.
- **Dashboards**: Dedicated Admin, Faculty, and Student portals.
- **Campus Pulse**: Analytics dashboard summarizing college metrics.
- **Automated Seeding**: System automatically populates with test users and data on startup.

## Prerequisites
- Java 21
- Maven
- MySQL 8.0+ or Docker (to run MySQL easily)

## Setup Instructions

### 1. Database Setup
You can either set up MySQL locally or use the provided `docker-compose.yml`.

**Using Docker:**
```bash
docker-compose up -d
```

**Using Local MySQL:**
Create a database named `campusflow_db`:
```sql
CREATE DATABASE campusflow_db;
```
Ensure your `src/main/resources/application.properties` matches your local MySQL credentials.

### 2. Build and Run
```bash
mvn clean install
mvn spring-boot:run
```

### 3. Demo Accounts
The application will automatically seed the database with the following demo accounts on startup:

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@campusflow.com` | `admin123` |
| Faculty | `faculty@campusflow.com` | `faculty123` |
| Student | `student@campusflow.com` | `student123` |

Navigate to `http://localhost:8080/` and login with the above credentials.

## Future Enhancements
The codebase is scaffolded with all the entities for assignments, attendances, complaints, and more. You can expand the controllers and frontend to build upon this solid foundation.
