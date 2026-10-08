package com.campusflow.config;

import com.campusflow.entity.*;
import com.campusflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String[] STANDARD_DEPARTMENTS = {"CSE", "ECE", "IT", "AIML"};
    private static final String[] SEEDED_SUBJECT_CODES = {
            "DS", "DBMS", "COA", "OS", "OOP", "CN", "EM-III", "JAVA", "PYTHON", "SE",
            "DSL", "DBMSL", "CNL", "JPL"
    };

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NoticeRepository noticeRepository;
    private final SubjectRepository subjectRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${campusflow.upload.directory:uploads}")
    private String uploadDirectory;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            Department cs = departmentRepository.save(Department.builder().name("Computer Science").code("CS").build());
            
            User admin = User.builder().email("admin@campusflow.com").password(passwordEncoder.encode("admin123"))
                .firstName("Super").lastName("Admin").role(Role.ADMIN).build();
                
            User faculty = User.builder().email("faculty@campusflow.com").password(passwordEncoder.encode("faculty123"))
                .firstName("Jane").lastName("Smith").role(Role.FACULTY).department(cs).build();

            User facultyRahul = User.builder().email("rahul.kulkarni@campusflow.com").password(passwordEncoder.encode("faculty123"))
                .firstName("Rahul").lastName("Kulkarni").role(Role.FACULTY).department(cs).build();

            User facultyPriya = User.builder().email("priya.iyer@campusflow.com").password(passwordEncoder.encode("faculty123"))
                .firstName("Priya").lastName("Iyer").role(Role.FACULTY).department(cs).build();

            User facultyAnanya = User.builder().email("ananya.deshmukh@campusflow.com").password(passwordEncoder.encode("faculty123"))
                .firstName("Ananya").lastName("Deshmukh").role(Role.FACULTY).department(cs).build();
                
            User student = User.builder().email("student@campusflow.com").password(passwordEncoder.encode("student123"))
                .firstName("Alice").lastName("Williams").role(Role.STUDENT).department(cs).currentSemester(3).build();
                
            userRepository.save(admin);
            userRepository.save(faculty);
            userRepository.save(facultyRahul);
            userRepository.save(facultyPriya);
            userRepository.save(facultyAnanya);
            userRepository.save(student);
            
            Notice notice = Notice.builder().title("Welcome to CampusFlow").content("System is now live!")
                .publishDate(LocalDateTime.now()).author(admin).build();
            noticeRepository.save(notice);
            
            System.out.println("Data Seeder: Created demo accounts (password: *123 for all roles).");
        }

        ensureStandardDepartments();
        ensureSeededSubjectFacultyAssignments();
        ensureFacultyDemoContent();
    }

    private void ensureStandardDepartments() {
        for (String name : STANDARD_DEPARTMENTS) {
            if (departmentRepository.existsByNameIgnoreCase(name)) {
                continue;
            }

            String code = name;
            int suffix = 2;
            while (departmentRepository.existsByCodeIgnoreCase(code)) {
                code = name + "-" + suffix++;
            }
            departmentRepository.save(Department.builder().name(name).code(code).build());
        }
    }

    private void ensureSeededSubjectFacultyAssignments() {
        List<User> facultyMembers = userRepository.findByRole(Role.FACULTY).stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();
        if (facultyMembers.isEmpty()) {
            return;
        }

        for (int subjectIndex = 0; subjectIndex < SEEDED_SUBJECT_CODES.length; subjectIndex++) {
            Subject subject = subjectRepository.findByCode(SEEDED_SUBJECT_CODES[subjectIndex])
                    .orElse(null);
            if (subject == null || subject.getFaculty() != null) {
                continue;
            }

            List<User> eligibleFaculty = subject.getDepartment() == null
                    ? facultyMembers
                    : facultyMembers.stream()
                            .filter(member -> member.getDepartment() != null
                                    && member.getDepartment().getId().equals(subject.getDepartment().getId()))
                            .toList();
            if (eligibleFaculty.isEmpty()) {
                continue;
            }

            subject.setFaculty(eligibleFaculty.get(subjectIndex % eligibleFaculty.size()));
            subjectRepository.save(subject);
        }
    }

    private void ensureFacultyDemoContent() throws Exception {
        List<DemoContent> demoContent = List.of(
                new DemoContent("DS", "Binary Search Tree Practice", "Binary Search Tree Study Notes",
                        "Practice insertion, traversal, and search operations on binary search trees.",
                        "Data Structures Study Notes.txt", "Binary Search Trees\n\n"
                                + "A binary search tree stores smaller keys in the left subtree and larger keys in the right subtree.\n"
                                + "Practice insertion, in-order traversal, search, and deletion. Analyze average and worst-case complexity."),
                new DemoContent("CN", "Network Protocol Analysis", "Network Protocol Study Notes",
                        "Review layered network protocols and packet-flow analysis.",
                        "Computer Networks Study Notes.txt", "Network Protocol Analysis\n\n"
                                + "Review encapsulation across application, transport, network, and link layers.\n"
                                + "For each protocol, identify its addressing, reliability, and routing responsibilities."),
                new DemoContent("JAVA", "Object-Oriented Programming Lab", "Java OOP Lab Guide",
                        "Exercises for classes, inheritance, interfaces, and exception handling.",
                        "Java Programming Lab Notes.txt", "Object-Oriented Programming Lab\n\n"
                                + "Build small Java examples using encapsulation, inheritance, interfaces, and polymorphism.\n"
                                + "Add input validation and handle expected exceptions explicitly."));

        int assignmentIndex = 0;
        for (DemoContent content : demoContent) {
            Subject subject = subjectRepository.findByCode(content.subjectCode()).orElse(null);
            if (subject == null || subject.getFaculty() == null) {
                continue;
            }
            boolean assignmentExists = assignmentRepository.findBySubjectId(subject.getId()).stream()
                    .anyMatch(item -> content.assignmentTitle().equalsIgnoreCase(item.getTitle()));
            if (!assignmentExists) {
                assignmentRepository.saveAndFlush(Assignment.builder()
                        .title(content.assignmentTitle())
                        .description(content.description())
                        .dueDate(LocalDateTime.now().plusDays(14 + assignmentIndex * 7)
                                .withSecond(0).withNano(0))
                        .subject(subject)
                        .faculty(subject.getFaculty())
                        .build());
            }
            assignmentIndex++;

            boolean materialExists = studyMaterialRepository.findBySubjectId(subject.getId()).stream()
                    .anyMatch(item -> content.materialTitle().equalsIgnoreCase(item.getTitle()));
            if (!materialExists) {
                String storedPath = writeDemoMaterial(content);
                try {
                    studyMaterialRepository.saveAndFlush(StudyMaterial.builder()
                            .title(content.materialTitle())
                            .description(content.description())
                            .fileUrl(storedPath)
                            .subject(subject)
                            .faculty(subject.getFaculty())
                            .uploadDate(LocalDateTime.now())
                            .build());
                } catch (RuntimeException ex) {
                    deleteDemoMaterial(storedPath, ex);
                    throw ex;
                }
            }
        }
    }

    private String writeDemoMaterial(DemoContent content) throws IOException {
        Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path directory = root.resolve("study-materials").normalize();
        Files.createDirectories(directory);
        String safeName = content.materialFileName().replaceAll("[^A-Za-z0-9._-]", "_");
        Path destination = directory.resolve(UUID.randomUUID() + "_" + safeName).normalize();
        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid demo study material filename.");
        }
        Files.writeString(destination, content.materialText());
        return "study-materials/" + destination.getFileName();
    }

    private void deleteDemoMaterial(String storedPath, RuntimeException saveFailure) {
        try {
            Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
            Path file = root.resolve(storedPath).normalize();
            if (storedPath.startsWith("study-materials/") && file.startsWith(root.resolve("study-materials").normalize())) {
                Files.deleteIfExists(file);
            }
        } catch (Exception cleanupFailure) {
            saveFailure.addSuppressed(cleanupFailure);
        }
    }

    private record DemoContent(
            String subjectCode,
            String assignmentTitle,
            String materialTitle,
            String description,
            String materialFileName,
            String materialText) {
    }
}
