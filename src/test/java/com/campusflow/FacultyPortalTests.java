package com.campusflow;

import com.campusflow.entity.Attendance;
import com.campusflow.entity.AttendanceStatus;
import com.campusflow.entity.Assignment;
import com.campusflow.entity.Department;
import com.campusflow.entity.Role;
import com.campusflow.entity.StudyMaterial;
import com.campusflow.entity.Submission;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.AssignmentRepository;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.StudyMaterialRepository;
import com.campusflow.repository.SubmissionRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FacultyPortalTests {

    private static final List<String> SEEDED_SUBJECT_CODES = List.of(
            "DS", "DBMS", "COA", "OS", "OOP", "CN", "EM-III", "JAVA", "PYTHON", "SE",
            "DSL", "DBMSL", "CNL", "JPL");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Value("${campusflow.upload.directory:uploads}")
    private String uploadDirectory;

    private Department department;
    private Department unrelatedDepartment;
    private User faculty;
    private User otherFaculty;
    private User admin;
    private User relatedStudent;
    private User secondRelatedStudent;
    private User otherSemesterStudent;
    private User unrelatedDepartmentStudent;
    private Subject assignedSubject;
    private Subject otherFacultySubject;
    private Subject otherSemesterSubject;
    private Attendance otherFacultyAttendance;
    private final List<Assignment> fixtureAssignments = new ArrayList<>();
    private final List<StudyMaterial> fixtureMaterials = new ArrayList<>();
    private final List<String> fixtureFileUrls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime(), 36);
        department = departmentRepository.saveAndFlush(Department.builder()
                .name("Faculty roster " + suffix)
                .code("FR" + suffix)
                .build());
        unrelatedDepartment = departmentRepository.saveAndFlush(Department.builder()
                .name("Unrelated roster " + suffix)
                .code("UR" + suffix)
                .build());

        faculty = saveUser("faculty-roster-" + suffix, "Faculty", "Owner", Role.FACULTY, department, null);
        otherFaculty = saveUser("faculty-other-" + suffix, "Other", "Faculty", Role.FACULTY, department, null);
        admin = saveUser("faculty-admin-" + suffix, "Roster", "Admin", Role.ADMIN, null, null);
        relatedStudent = saveUser("faculty-related-" + suffix, "Relevant", "Student", Role.STUDENT, department, 3);
        secondRelatedStudent = saveUser("faculty-related-second-" + suffix, "Second", "Student", Role.STUDENT, department, 3);
        otherSemesterStudent = saveUser("faculty-semester-" + suffix, "OtherSemester", "Student", Role.STUDENT, department, 2);
        unrelatedDepartmentStudent = saveUser(
                "faculty-department-" + suffix, "OtherDepartment", "Student", Role.STUDENT, unrelatedDepartment, 3);

        assignedSubject = saveSubject("Assigned roster subject " + suffix, "FRS" + suffix, department, faculty, 3);
        otherFacultySubject = saveSubject("Private other faculty subject " + suffix, "OFS" + suffix, department, otherFaculty, 3);
        otherSemesterSubject = saveSubject("Other semester subject " + suffix, "OSS" + suffix, department, otherFaculty, 2);
        otherFacultyAttendance = attendanceRepository.saveAndFlush(Attendance.builder()
                .student(relatedStudent)
                .subject(otherFacultySubject)
                .date(LocalDate.now().minusDays(1))
                .status(AttendanceStatus.ABSENT)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (!fixtureAssignments.isEmpty()) {
            List<Long> assignmentIds = fixtureAssignments.stream().map(Assignment::getId).toList();
            submissionRepository.deleteAll(submissionRepository.findAll().stream()
                    .filter(submission -> submission.getAssignment() != null
                            && assignmentIds.contains(submission.getAssignment().getId()))
                    .toList());
            submissionRepository.flush();
            assignmentRepository.deleteAll(assignmentRepository.findAllById(assignmentIds));
            assignmentRepository.flush();
            fixtureAssignments.clear();
        }
        if (!fixtureMaterials.isEmpty()) {
            studyMaterialRepository.deleteAll(fixtureMaterials);
            studyMaterialRepository.flush();
            fixtureMaterials.clear();
        }
        for (String fileUrl : fixtureFileUrls) {
            try {
                Files.deleteIfExists(Path.of(uploadDirectory).toAbsolutePath().normalize().resolve(fileUrl).normalize());
            } catch (Exception ex) {
                throw new IllegalStateException("Could not clean Faculty portal test upload " + fileUrl, ex);
            }
        }
        fixtureFileUrls.clear();
        List<Long> fixtureSubjectIds = new ArrayList<>();
        if (assignedSubject != null) {
            fixtureSubjectIds.add(assignedSubject.getId());
        }
        if (otherFacultySubject != null) {
            fixtureSubjectIds.add(otherFacultySubject.getId());
        }
        if (otherSemesterSubject != null) {
            fixtureSubjectIds.add(otherSemesterSubject.getId());
        }
        if (!fixtureSubjectIds.isEmpty()) {
            attendanceRepository.deleteAll(attendanceRepository.findAll().stream()
                    .filter(record -> fixtureSubjectIds.contains(record.getSubject().getId()))
                    .toList());
            attendanceRepository.flush();
            subjectRepository.deleteAll(subjectRepository.findAllById(fixtureSubjectIds));
            subjectRepository.flush();
        }
        for (User user : new User[]{
                relatedStudent, secondRelatedStudent, otherSemesterStudent, unrelatedDepartmentStudent,
                faculty, otherFaculty, admin}) {
            if (user != null && userRepository.existsById(user.getId())) {
                userRepository.delete(user);
            }
        }
        userRepository.flush();
        for (Department value : new Department[]{department, unrelatedDepartment}) {
            if (value != null && departmentRepository.existsById(value.getId())) {
                departmentRepository.delete(value);
            }
        }
        departmentRepository.flush();
    }

    @Test
    void seededSubjectsAreAssignedToExistingFacultyWithoutCreatingDuplicates() {
        for (String code : SEEDED_SUBJECT_CODES) {
            Subject seededSubject = subjectRepository.findByCode(code).orElseThrow();
            org.junit.jupiter.api.Assertions.assertNotNull(seededSubject.getFaculty(), code);
            org.junit.jupiter.api.Assertions.assertEquals(Role.FACULTY, seededSubject.getFaculty().getRole(), code);
            org.junit.jupiter.api.Assertions.assertEquals(3, seededSubject.getSemester(), code);
        }
        org.junit.jupiter.api.Assertions.assertEquals(14, subjectRepository.findAll().stream()
                .filter(subject -> SEEDED_SUBJECT_CODES.contains(subject.getCode()))
                .count());
    }

    @Test
    void facultyPageShowsOnlyOwnedSubjectsAndTheirMatchingStudentRoster() throws Exception {
        String page = mockMvc.perform(get("/faculty/attendance")
                        .with(facultyRequest())
                        .param("sheetSubjectId", assignedSubject.getId().toString())
                        .param("sheetDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(page.contains(assignedSubject.getName()));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains(otherSemesterSubject.getName()));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains(otherFacultySubject.getName()));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Relevant Student"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Second Student"));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("OtherSemester Student"));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("OtherDepartment Student"));
    }

    @Test
    void classSheetLoadsExistingStatusesAndUpdatesWholeClassWithoutDuplicates() throws Exception {
        LocalDate date = LocalDate.now().minusDays(2);
        Attendance firstRecord = attendanceRepository.saveAndFlush(Attendance.builder()
                .student(relatedStudent)
                .subject(assignedSubject)
                .date(date)
                .status(AttendanceStatus.PRESENT)
                .build());
        Attendance secondRecord = attendanceRepository.saveAndFlush(Attendance.builder()
                .student(secondRelatedStudent)
                .subject(assignedSubject)
                .date(date)
                .status(AttendanceStatus.LATE)
                .build());

        String page = mockMvc.perform(get("/faculty/attendance")
                        .with(facultyRequest())
                        .param("sheetSubjectId", assignedSubject.getId().toString())
                        .param("sheetDate", date.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("value=\"PRESENT\"")
                && page.contains("selected=\"selected\""));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("value=\"LATE\"")
                && page.contains("selected=\"selected\""));

        mockMvc.perform(post("/faculty/attendance/save")
                        .with(facultyRequest())
                        .with(csrf())
                        .param("subjectId", assignedSubject.getId().toString())
                        .param("date", date.toString())
                        .param("studentIds", relatedStudent.getId().toString(), secondRelatedStudent.getId().toString())
                        .param("statuses", "ABSENT", "EXCUSED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/faculty/attendance?sheetSubjectId="
                        + assignedSubject.getId() + "&sheetDate=" + date));

        org.junit.jupiter.api.Assertions.assertEquals(2,
                attendanceRepository.findBySubjectIdAndDate(assignedSubject.getId(), date).size());
        org.junit.jupiter.api.Assertions.assertEquals(AttendanceStatus.ABSENT,
                attendanceRepository.findById(firstRecord.getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(AttendanceStatus.EXCUSED,
                attendanceRepository.findById(secondRecord.getId()).orElseThrow().getStatus());
    }

    @Test
    void selectingAnotherFacultysSubjectDoesNotExposeItsSubjectOrStudents() throws Exception {
        String page = mockMvc.perform(get("/faculty/attendance")
                        .with(facultyRequest())
                        .param("subjectId", otherFacultySubject.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(page.contains(otherFacultySubject.getName()));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("Relevant Student"));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("OtherSemester Student"));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("OtherDepartment Student"));
    }

    @Test
    void facultyAttendanceReadsAndWritesRemainLimitedToOwnedSubjects() throws Exception {
        mockMvc.perform(get("/api/faculty/attendance")
                        .with(facultyRequest())
                        .param("subjectId", otherFacultySubject.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[]"));

        mockMvc.perform(post("/faculty/attendance/save")
                        .with(facultyRequest())
                        .with(csrf())
                        .param("studentIds", relatedStudent.getId().toString(), secondRelatedStudent.getId().toString())
                        .param("statuses", "PRESENT", "ABSENT")
                        .param("subjectId", otherFacultySubject.getId().toString())
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/faculty/attendance?sheetSubjectId="
                        + otherFacultySubject.getId() + "&sheetDate=" + LocalDate.now()));

        mockMvc.perform(post("/faculty/attendance/save")
                        .with(facultyRequest())
                        .with(csrf())
                        .param("studentIds", relatedStudent.getId().toString(), secondRelatedStudent.getId().toString())
                        .param("statuses", "PRESENT", "ABSENT")
                        .param("subjectId", assignedSubject.getId().toString())
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/faculty/attendance?sheetSubjectId="
                        + assignedSubject.getId() + "&sheetDate=" + LocalDate.now()));
        org.junit.jupiter.api.Assertions.assertTrue(attendanceRepository
                .findByStudentIdAndSubjectIdAndDate(
                        relatedStudent.getId(), assignedSubject.getId(), LocalDate.now())
                .isPresent());
    }

    @Test
    void adminAndStudentAttendanceRemainViewOnly() throws Exception {
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
        var studentRequest = SecurityMockMvcRequestPostProcessors.user(relatedStudent.getEmail()).roles("STUDENT");

        mockMvc.perform(get("/admin/attendance").with(adminRequest))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/attendance/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("studentId", relatedStudent.getId().toString())
                        .param("subjectId", assignedSubject.getId().toString())
                        .param("date", LocalDate.now().toString())
                        .param("status", "PRESENT"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/attendance")
                        .with(adminRequest)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"studentId":%d,"subjectId":%d,"date":"%s","status":"PRESENT"}
                                """.formatted(relatedStudent.getId(), assignedSubject.getId(), LocalDate.now())))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/student/attendance").with(studentRequest))
                .andExpect(status().isOk());
        mockMvc.perform(post("/faculty/attendance/save")
                        .with(studentRequest)
                        .with(csrf())
                        .param("studentIds", relatedStudent.getId().toString())
                        .param("statuses", "PRESENT")
                        .param("subjectId", assignedSubject.getId().toString())
                        .param("date", LocalDate.now().toString())
                )
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/student/attendance")
                        .with(studentRequest)
                        .with(csrf())
                        .param("status", "ABSENT"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void facultyCanCreateAssignmentGradeSubmissionAndStudentSeesFeedback() throws Exception {
        LocalDateTime dueDate = LocalDateTime.now().plusDays(10).withSecond(0).withNano(0);
        mockMvc.perform(post("/faculty/assignments/save")
                        .with(facultyRequest())
                        .with(csrf())
                        .param("title", "Faculty-owned assignment " + assignedSubject.getCode())
                        .param("description", "Complete the subject exercise.")
                        .param("subjectId", assignedSubject.getId().toString())
                        .param("dueDate", dueDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/faculty/assignments"));
        Assignment assignment = assignmentRepository.findBySubjectId(assignedSubject.getId()).stream()
                .filter(item -> item.getTitle().startsWith("Faculty-owned assignment "))
                .findFirst().orElseThrow();
        fixtureAssignments.add(assignment);

        mockMvc.perform(get("/faculty/assignments").with(facultyRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(assignment.getTitle())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Not submitted")));

        mockMvc.perform(post("/student/assignments/{id}/submit", assignment.getId())
                        .with(studentRequest())
                        .with(csrf())
                        .param("studentComments", "My completed work"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/assignments"));
        Submission submission = submissionRepository
                .findByAssignmentIdAndStudentId(assignment.getId(), relatedStudent.getId()).orElseThrow();

        mockMvc.perform(post("/faculty/assignments/{assignmentId}/submissions/{submissionId}/grade",
                                assignment.getId(), submission.getId())
                        .with(facultyRequest())
                        .with(csrf())
                        .param("marksAwarded", "17")
                        .param("facultyFeedback", "Clear explanation and correct result."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/faculty/assignments"));

        mockMvc.perform(get("/faculty/assignments").with(facultyRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Submitted")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("17")));
        mockMvc.perform(get("/student/assignments").with(studentRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Marks: 17")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Faculty feedback: Clear explanation and correct result.")));

        mockMvc.perform(post("/faculty/assignments/{assignmentId}/submissions/{submissionId}/grade",
                                assignment.getId(), submission.getId())
                        .with(otherFacultyRequest())
                        .with(csrf())
                        .param("marksAwarded", "99"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/faculty/assignments/{assignmentId}/submissions/{submissionId}/grade",
                                assignment.getId(), submission.getId())
                        .with(studentRequest())
                        .with(csrf())
                        .param("marksAwarded", "99"))
                .andExpect(status().isForbidden());
        Assertions.assertEquals(17, submissionRepository.findById(submission.getId()).orElseThrow().getMarksAwarded());
    }

    @Test
    void facultyCannotCreateOrViewAnotherFacultysAssignments() throws Exception {
        Assignment privateAssignment = assignmentRepository.saveAndFlush(Assignment.builder()
                .title("Private assignment " + otherFacultySubject.getCode())
                .description("Other faculty work")
                .dueDate(LocalDateTime.now().plusDays(5))
                .subject(otherFacultySubject)
                .faculty(otherFaculty)
                .build());
        fixtureAssignments.add(privateAssignment);

        mockMvc.perform(get("/faculty/assignments").with(facultyRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(privateAssignment.getTitle()))));
        mockMvc.perform(post("/faculty/assignments/save")
                        .with(facultyRequest())
                        .with(csrf())
                        .param("title", "Unauthorized assignment")
                        .param("description", "Should not be created")
                        .param("subjectId", otherFacultySubject.getId().toString())
                        .param("dueDate", LocalDateTime.now().plusDays(4)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))))
                .andExpect(status().is3xxRedirection());
        Assertions.assertFalse(assignmentRepository.findBySubjectId(otherFacultySubject.getId()).stream()
                .anyMatch(item -> item.getTitle().equals("Unauthorized assignment")));
        mockMvc.perform(get("/faculty/assignments").with(adminRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void facultyMaterialsUploadAndDownloadAreScopedAndStudentAccessUsesExistingFlow() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "faculty-notes.txt", "text/plain", "Course notes for download".getBytes());
        mockMvc.perform(multipart("/faculty/materials/save")
                        .file(upload)
                        .param("title", "Faculty material " + assignedSubject.getCode())
                        .param("description", "Test upload")
                        .param("subjectId", assignedSubject.getId().toString())
                        .with(facultyRequest())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/faculty/materials"));
        StudyMaterial material = studyMaterialRepository.findBySubjectId(assignedSubject.getId()).stream()
                .filter(item -> item.getTitle().startsWith("Faculty material "))
                .findFirst().orElseThrow();
        fixtureMaterials.add(material);
        fixtureFileUrls.add(material.getFileUrl());
        Assertions.assertTrue(Files.isRegularFile(
                Path.of(uploadDirectory).toAbsolutePath().normalize().resolve(material.getFileUrl()).normalize()));

        mockMvc.perform(get("/faculty/materials").with(facultyRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(material.getTitle())));
        mockMvc.perform(get("/faculty/materials/{id}/download", material.getId()).with(facultyRequest()))
                .andExpect(status().isOk())
                .andExpect(content().bytes("Course notes for download".getBytes()));
        mockMvc.perform(get("/student/materials/{id}/download", material.getId()).with(studentRequest()))
                .andExpect(status().isOk())
                .andExpect(content().bytes("Course notes for download".getBytes()));
        mockMvc.perform(get("/student/materials/{id}/download", material.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(unrelatedDepartmentStudent.getEmail())
                                .roles("STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/faculty/materials/{id}/download", material.getId()).with(otherFacultyRequest()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/faculty/materials").with(studentRequest()))
                .andExpect(status().isForbidden());
    }

    private User saveUser(
            String emailPrefix, String firstName, String lastName, Role role, Department userDepartment, Integer semester) {
        return userRepository.saveAndFlush(User.builder()
                .email(emailPrefix + "@campusflow.test")
                .password("test-password")
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .department(userDepartment)
                .currentSemester(semester)
                .build());
    }

    private Subject saveSubject(
            String name, String code, Department subjectDepartment, User subjectFaculty, Integer semester) {
        return subjectRepository.saveAndFlush(Subject.builder()
                .name(name)
                .code(code)
                .department(subjectDepartment)
                .faculty(subjectFaculty)
                .semester(semester)
                .build());
    }

    private RequestPostProcessor facultyRequest() {
        return SecurityMockMvcRequestPostProcessors.user(faculty.getEmail()).roles("FACULTY");
    }

    private RequestPostProcessor otherFacultyRequest() {
        return SecurityMockMvcRequestPostProcessors.user(otherFaculty.getEmail()).roles("FACULTY");
    }

    private RequestPostProcessor studentRequest() {
        return SecurityMockMvcRequestPostProcessors.user(relatedStudent.getEmail()).roles("STUDENT");
    }

    private RequestPostProcessor adminRequest() {
        return SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
    }
}
