package com.campusflow.config;

import com.campusflow.entity.Role;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        addStudents();
        addSubjects();
    }

    // =========================================================
    // STUDENTS
    // =========================================================

    private void addStudents() {

        createStudent(
                "Aarav",
                "Shah",
                "aarav.shah@campusflow.com"
        );

        createStudent(
                "Diya",
                "Patel",
                "diya.patel@campusflow.com"
        );

        createStudent(
                "Meera",
                "Nair",
                "meera.nair@campusflow.com"
        );

        createStudent(
                "Rohan",
                "Kulkarni",
                "rohan.kulkarni@campusflow.com"
        );

        createStudent(
                "Ananya",
                "Deshmukh",
                "ananya.deshmukh@campusflow.com"
        );

        createStudent(
                "Aditya",
                "Patil",
                "aditya.patil@campusflow.com"
        );

        createStudent(
                "Sneha",
                "Joshi",
                "sneha.joshi@campusflow.com"
        );

        createStudent(
                "Omkar",
                "More",
                "omkar.more@campusflow.com"
        );

        createStudent(
                "Riya",
                "Kulkarni",
                "riya.kulkarni@campusflow.com"
        );

        createStudent(
                "Vedant",
                "Shinde",
                "vedant.shinde@campusflow.com"
        );

        createStudent(
                "Neha",
                "Pawar",
                "neha.pawar@campusflow.com"
        );

        createStudent(
                "Yash",
                "Thakur",
                "yash.thakur@campusflow.com"
        );

        createStudent(
                "Isha",
                "Desai",
                "isha.desai@campusflow.com"
        );

        createStudent(
                "Kunal",
                "Jadhav",
                "kunal.jadhav@campusflow.com"
        );

        createStudent(
                "Tanvi",
                "Joshi",
                "tanvi.joshi@campusflow.com"
        );
    }

    private void createStudent(
            String firstName,
            String lastName,
            String email) {

        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        User student = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(passwordEncoder.encode("student123"))
                .role(Role.STUDENT)
                .currentSemester(3)
                .build();

        userRepository.save(student);
    }

    // =========================================================
    // MU-STYLE SUBJECTS
    // =========================================================

    private void addSubjects() {

        createSubject(
                "Data Structures",
                "DS",
                3
        );

        createSubject(
                "Database Management Systems",
                "DBMS",
                3
        );

        createSubject(
                "Computer Organization and Architecture",
                "COA",
                3
        );

        createSubject(
                "Operating Systems",
                "OS",
                3
        );

        createSubject(
                "Object Oriented Programming",
                "OOP",
                3
        );

        createSubject(
                "Computer Networks",
                "CN",
                3
        );

        createSubject(
                "Engineering Mathematics III",
                "EM-III",
                3
        );

        createSubject(
                "Java Programming",
                "JAVA",
                3
        );

        createSubject(
                "Python Programming",
                "PYTHON",
                3
        );

        createSubject(
                "Software Engineering",
                "SE",
                3
        );

        createSubject(
                "Data Structures Laboratory",
                "DSL",
                3
        );

        createSubject(
                "Database Management Systems Laboratory",
                "DBMSL",
                3
        );

        createSubject(
                "Computer Networks Laboratory",
                "CNL",
                3
        );

        createSubject(
                "Java Programming Laboratory",
                "JPL",
                3
        );
    }

    private void createSubject(
            String name,
            String code,
            Integer semester) {

        if (subjectRepository.findByCode(code).isPresent()) {
            return;
        }

        Subject subject = Subject.builder()
                .name(name)
                .code(code)
                .semester(semester)
                .build();

        subjectRepository.save(subject);
    }
}