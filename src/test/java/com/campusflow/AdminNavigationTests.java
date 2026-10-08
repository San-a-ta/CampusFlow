package com.campusflow;

import com.campusflow.entity.Role;
import com.campusflow.entity.Department;
import com.campusflow.entity.Attendance;
import com.campusflow.entity.AttendanceStatus;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.entity.Complaint;
import com.campusflow.entity.ComplaintStatus;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.ComplaintRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
class AdminNavigationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    private User admin;
    private Department department;
    private User student;
    private User faculty;
    private boolean adminCreatedByTest;

    @BeforeEach
    void setUp() {
        String email = "admin-navigation-test@campusflow.test";
        admin = userRepository.findByEmail(email).orElseGet(() -> {
            adminCreatedByTest = true;
            return User.builder()
                    .email(email)
                    .build();
        });
        admin.setPassword("test-password");
        admin.setFirstName("Navigation");
        admin.setLastName("Test");
        admin.setRole(Role.ADMIN);
        admin.setDepartment(null);
        admin.setCurrentSemester(null);
        admin = userRepository.save(admin);
    }

    @AfterEach
    void tearDown() {
        if (student != null && userRepository.existsById(student.getId())) {
            userRepository.deleteById(student.getId());
        }
        if (faculty != null && userRepository.existsById(faculty.getId())) {
            userRepository.deleteById(faculty.getId());
        }
        if (department != null && departmentRepository.existsById(department.getId())) {
            departmentRepository.deleteById(department.getId());
        }
        if (adminCreatedByTest && admin != null) {
            userRepository.deleteById(admin.getId());
        }
    }

    @Test
    void allAdminNavigationPagesRenderSuccessfully() throws Exception {
        String[] routes = {
                "/admin/dashboard",
                "/admin/students",
                "/admin/faculty",
                "/admin/departments",
                "/admin/complaints",
                "/admin/notices",
                "/admin/attendance",
                "/admin/assignments",
                "/admin/documents",
                "/admin/events",
                "/admin/fees",
                "/admin/reports",
                "/admin/settings"
        };

        for (String route : routes) {
            mockMvc.perform(get(route).with(SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void adminCanDownloadDatabaseBackedCsvReport() throws Exception {
        var result = mockMvc.perform(get("/admin/reports/export")
                        .with(SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("CampusFlow_Admin_Report_")))
                .andReturn();

        String report = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(report.startsWith("Metric,Value"));
        org.junit.jupiter.api.Assertions.assertTrue(report.contains("\"Total Students\""));
        org.junit.jupiter.api.Assertions.assertTrue(report.contains("\"Total Attendance Records\""));
        org.junit.jupiter.api.Assertions.assertTrue(report.contains("\"Pending/Outstanding Fees\""));
    }

    @Test
    void nonAdminCannotDownloadAdminReport() throws Exception {
        mockMvc.perform(get("/admin/reports/export")
                        .with(SecurityMockMvcRequestPostProcessors.user("student@campusflow.test").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardProfileShowsAuthenticatedDatabaseUserWithoutPassword() throws Exception {
        var result = mockMvc.perform(get("/admin/dashboard")
                        .with(SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        String page = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Navigation Test"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains(admin.getEmail()));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Administrator profile"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("data-report-url=\"/admin/reports/export\""));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("async function generateReport()"));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("CampusFlow report generation will be connected to the backend next."));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("test-password"));
    }

    @Test
    void departmentAndStudentPagesRenderDatabaseSubmissionForms() throws Exception {
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
        String departmentsPage = mockMvc.perform(get("/admin/departments").with(adminRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(departmentsPage.contains("action=\"/admin/departments/save\""));
        org.junit.jupiter.api.Assertions.assertTrue(departmentsPage.contains("name=\"_csrf\""));

        String studentsPage = mockMvc.perform(get("/admin/students").with(adminRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(studentsPage.contains("action=\"/admin/students/save\""));
        org.junit.jupiter.api.Assertions.assertTrue(studentsPage.contains("name=\"_csrf\""));
        for (String departmentName : List.of("CSE", "ECE", "IT", "AIML")) {
            org.junit.jupiter.api.Assertions.assertTrue(studentsPage.contains(">" + departmentName + "</option>"));
            org.junit.jupiter.api.Assertions.assertTrue(departmentsPage.contains(">" + departmentName + "</option>"));
        }

        String facultyPage = mockMvc.perform(get("/admin/faculty").with(adminRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String departmentName : List.of("CSE", "ECE", "IT", "AIML")) {
            org.junit.jupiter.api.Assertions.assertTrue(facultyPage.contains(">" + departmentName + "</option>"));
        }
    }

    @Test
    void facultyCanBeCreatedEditedSearchedFilteredAndDeleted() throws Exception {
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
        String suffix = Long.toString(System.nanoTime(), 36).toUpperCase();
        String departmentName = "Faculty Test Department " + suffix;
        String departmentCode = "F" + suffix;
        department = departmentRepository.save(Department.builder()
                .name(departmentName)
                .code(departmentCode)
                .build());

        String email = "faculty-test-" + System.nanoTime() + "@campusflow.test";
        mockMvc.perform(post("/admin/faculty/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("firstName", "Aarav")
                        .param("lastName", "Sharma")
                        .param("email", email)
                        .param("password", "faculty-password-123")
                        .param("departmentId", department.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/faculty"));
        faculty = userRepository.findByEmail(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.FACULTY, faculty.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(department.getId(), faculty.getDepartment().getId());
        org.junit.jupiter.api.Assertions.assertNotEquals("faculty-password-123", faculty.getPassword());

        var filteredFaculty = mockMvc.perform(get("/admin/faculty")
                        .with(adminRequest)
                        .param("search", "Aarav Sharma")
                        .param("departmentId", department.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                ((List<User>) filteredFaculty.getModelAndView().getModel().get("facultyList"))
                        .stream().anyMatch(found -> found.getId().equals(faculty.getId())));

        mockMvc.perform(post("/admin/faculty/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", faculty.getId().toString())
                        .param("firstName", "Aarav")
                        .param("lastName", "Mehta")
                        .param("email", email)
                        .param("departmentId", department.getId().toString()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals("Mehta",
                userRepository.findById(faculty.getId()).orElseThrow().getLastName());

        mockMvc.perform(post("/admin/faculty/delete")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", faculty.getId().toString()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertFalse(userRepository.existsById(faculty.getId()));
        faculty = null;

        var filteredDepartments = mockMvc.perform(get("/admin/departments")
                        .with(adminRequest).param("search", departmentCode))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(1,
                ((List<Department>) filteredDepartments.getModelAndView().getModel().get("departments")).size());
        String departmentsPage = filteredDepartments.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(departmentsPage.contains("value=\"CSE\""));
        org.junit.jupiter.api.Assertions.assertTrue(departmentsPage.contains("value=\"ECE\""));
        org.junit.jupiter.api.Assertions.assertTrue(departmentsPage.contains("value=\"IT\""));
        org.junit.jupiter.api.Assertions.assertTrue(departmentsPage.contains("value=\"AIML\""));

        mockMvc.perform(post("/admin/departments/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", department.getId().toString())
                        .param("name", "Faculty Updated Department " + suffix)
                        .param("code", "FU" + suffix))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals("FU" + suffix,
                departmentRepository.findById(department.getId()).orElseThrow().getCode());

        mockMvc.perform(post("/admin/departments/delete")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", department.getId().toString()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertFalse(departmentRepository.existsById(department.getId()));
        department = null;
    }

    @Test
    void attendanceStatisticsReflectOverallAndSelectedSubjectRecords() throws Exception {
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Attendance Test Subject")
                .code("AT-" + System.nanoTime())
                .build());
        User testStudent = userRepository.save(User.builder()
                .email("attendance-test-" + System.nanoTime() + "@campusflow.test")
                .password("test-password")
                .firstName("Attendance")
                .lastName("Test")
                .role(Role.STUDENT)
                .build());
        List<Attendance> records = List.of(
                Attendance.builder().student(testStudent).subject(subject)
                        .date(java.time.LocalDate.now()).status(AttendanceStatus.PRESENT).build(),
                Attendance.builder().student(testStudent).subject(subject)
                        .date(java.time.LocalDate.now().minusDays(1)).status(AttendanceStatus.ABSENT).build()
        );

        try {
            attendanceRepository.saveAllAndFlush(records);
            var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");

            var overallResult = mockMvc.perform(get("/admin/attendance").with(adminRequest))
                    .andExpect(status().isOk())
                    .andReturn();
            var overallModel = overallResult.getModelAndView().getModel();
            org.junit.jupiter.api.Assertions.assertEquals(attendanceRepository.count(),
                    ((Number) overallModel.get("totalAttendanceCount")).longValue());

            var subjectResult = mockMvc.perform(get("/admin/attendance")
                            .with(adminRequest)
                            .param("subjectId", subject.getId().toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            var subjectModel = subjectResult.getModelAndView().getModel();
            org.junit.jupiter.api.Assertions.assertEquals(2, subjectModel.get("filteredAttendanceCount"));
            org.junit.jupiter.api.Assertions.assertEquals(1L, subjectModel.get("filteredPresentCount"));
            org.junit.jupiter.api.Assertions.assertEquals(1L, subjectModel.get("filteredAbsentCount"));
            org.junit.jupiter.api.Assertions.assertEquals(50.0,
                    (Double) subjectModel.get("filteredAttendancePercentage"));
        } finally {
            attendanceRepository.deleteAll(records);
            userRepository.delete(testStudent);
            subjectRepository.delete(subject);
        }
    }

    @Test
    void adminAttendanceIsReadOnlyAndFiltersByDepartmentAndSemester() throws Exception {
        department = departmentRepository.save(Department.builder()
                .name("Read Only Attendance " + System.nanoTime())
                .code("RO" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Read Only Subject")
                .code("RO-" + System.nanoTime())
                .semester(2)
                .department(department)
                .build());
        student = userRepository.save(User.builder()
                .email("readonly-student-" + System.nanoTime() + "@campusflow.test")
                .password("test-password")
                .firstName("Read")
                .lastName("Only")
                .role(Role.STUDENT)
                .department(department)
                .currentSemester(2)
                .build());
        Attendance record = attendanceRepository.saveAndFlush(Attendance.builder()
                .student(student)
                .subject(subject)
                .date(java.time.LocalDate.now())
                .status(AttendanceStatus.PRESENT)
                .build());
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
        try {
            var result = mockMvc.perform(get("/admin/attendance")
                            .with(adminRequest)
                            .param("departmentId", department.getId().toString())
                            .param("semester", "2"))
                    .andExpect(status().isOk())
                    .andReturn();
            org.junit.jupiter.api.Assertions.assertEquals(1, result.getModelAndView().getModel()
                    .get("filteredAttendanceCount"));
            String page = result.getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("/admin/attendance/save"));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("/admin/attendance/delete"));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("Record Attendance"));
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("Student Name"));
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("Branch / Department"));
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("Roll Number"));
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("Attendance Status"));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("0.0%"));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("Subject Attendance"));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("Filtered Attendance Statistics"));

            mockMvc.perform(post("/admin/attendance/save")
                            .with(adminRequest).with(csrf())
                            .param("studentId", student.getId().toString())
                            .param("subjectId", subject.getId().toString())
                            .param("date", java.time.LocalDate.now().toString())
                            .param("status", "ABSENT"))
                    .andExpect(status().isNotFound());
        } finally {
            attendanceRepository.delete(record);
            attendanceRepository.flush();
            subjectRepository.delete(subject);
            subjectRepository.flush();
        }
        userRepository.delete(student);
        userRepository.flush();
        student = null;
        departmentRepository.delete(department);
        departmentRepository.flush();
        department = null;
    }

    @Test
    void adminComplaintListingContainsNoComplainantIdentity() throws Exception {
        student = userRepository.save(User.builder()
                .email("anonymous-complaint-" + System.nanoTime() + "@campusflow.test")
                .password("test-password")
                .firstName("Secret")
                .lastName("Complainant")
                .role(Role.STUDENT)
                .build());
        Complaint complaint = complaintRepository.saveAndFlush(Complaint.builder()
                .title("Library access issue")
                .description("The library portal is unavailable.")
                .student(student)
                .status(ComplaintStatus.OPEN)
                .createdAt(java.time.LocalDateTime.now())
                .build());
        try {
            String page = mockMvc.perform(get("/admin/complaints")
                            .with(SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("Library access issue"));
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("The library portal is unavailable."));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains(student.getEmail()));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("Secret Complainant"));
            org.junit.jupiter.api.Assertions.assertFalse(page.contains("student_id"));
        } finally {
            complaintRepository.delete(complaint);
            complaintRepository.flush();
        }
        userRepository.delete(student);
        userRepository.flush();
        student = null;
    }

    @Test
    void adminCanUpdateComplaintStatusWithoutExposingStudentIdentity() throws Exception {
        student = userRepository.saveAndFlush(User.builder()
                .email("complaint-status-" + System.nanoTime() + "@campusflow.test")
                .password("test-password")
                .firstName("Private")
                .lastName("Student")
                .role(Role.STUDENT)
                .build());
        var studentRequest = SecurityMockMvcRequestPostProcessors.user(student.getEmail()).roles("STUDENT");
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");

        mockMvc.perform(post("/student/helpdesk")
                        .with(studentRequest)
                        .with(csrf())
                        .param("title", "Status update request")
                        .param("description", "Please review this anonymous request."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/helpdesk"));
        Complaint complaint = complaintRepository.findByStudentId(student.getId()).get(0);
        try {
            String adminPage = mockMvc.perform(get("/admin/complaints").with(adminRequest))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(adminPage.contains("Status update request"));
            org.junit.jupiter.api.Assertions.assertFalse(adminPage.contains(student.getEmail()));
            org.junit.jupiter.api.Assertions.assertFalse(adminPage.contains("Private Student"));
            org.junit.jupiter.api.Assertions.assertFalse(adminPage.contains(student.getId().toString()));

            mockMvc.perform(post("/admin/complaints/{id}/status", complaint.getId())
                            .with(studentRequest)
                            .with(csrf())
                            .param("status", "RESOLVED"))
                    .andExpect(status().isForbidden());
            org.junit.jupiter.api.Assertions.assertEquals(ComplaintStatus.OPEN,
                    complaintRepository.findById(complaint.getId()).orElseThrow().getStatus());

            mockMvc.perform(post("/admin/complaints/{id}/status", complaint.getId())
                            .with(adminRequest)
                            .with(csrf())
                            .param("status", "IN_PROGRESS"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/complaints"));
            org.junit.jupiter.api.Assertions.assertEquals(ComplaintStatus.IN_PROGRESS,
                    complaintRepository.findById(complaint.getId()).orElseThrow().getStatus());

            String studentPage = mockMvc.perform(get("/student/helpdesk").with(studentRequest))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(studentPage.contains("IN_PROGRESS"));
        } finally {
            complaintRepository.deleteById(complaint.getId());
            complaintRepository.flush();
        }
        userRepository.delete(student);
        userRepository.flush();
        student = null;
    }

    @Test
    void departmentAndStudentManagementPersistExistingEntities() throws Exception {
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
        mockMvc.perform(post("/admin/departments/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("name", "Navigation Test Department")
                        .param("code", "NAVTEST"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        department = departmentRepository.findAll().stream()
                .filter(item -> "NAVTEST".equals(item.getCode()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/admin/students/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("firstName", "Navigation")
                        .param("lastName", "Student")
                        .param("email", "navigation-student@campusflow.test")
                        .param("password", "test-password-123")
                        .param("departmentId", department.getId().toString())
                        .param("currentSemester", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/students"));

        student = userRepository.findByEmail("navigation-student@campusflow.test").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.STUDENT, student.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(department.getId(), student.getDepartment().getId());
        org.junit.jupiter.api.Assertions.assertNotEquals("test-password-123", student.getPassword());

        String studentPage = mockMvc.perform(get("/admin/students").with(adminRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(studentPage.contains("Department folders"));
        org.junit.jupiter.api.Assertions.assertTrue(studentPage.contains("Navigation Student"));

        String performancePage = mockMvc.perform(get("/admin/students/{id}", student.getId()).with(adminRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(performancePage.contains("Academic performance"));
        org.junit.jupiter.api.Assertions.assertTrue(performancePage.contains("No subjects are linked"));

        var searchResult = mockMvc.perform(get("/admin/students")
                        .with(adminRequest)
                        .param("search", "Navigation Student")
                        .param("departmentId", department.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                ((List<User>) searchResult.getModelAndView().getModel().get("students"))
                        .stream().anyMatch(found -> found.getId().equals(student.getId())));

        mockMvc.perform(post("/admin/students/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", student.getId().toString())
                        .param("firstName", "Updated")
                        .param("lastName", "Student")
                        .param("email", "navigation-student@campusflow.test")
                        .param("departmentId", department.getId().toString())
                        .param("currentSemester", "3"))
                .andExpect(status().is3xxRedirection());

        org.junit.jupiter.api.Assertions.assertEquals("Updated", userRepository.findById(student.getId()).orElseThrow().getFirstName());

        mockMvc.perform(post("/admin/students/delete")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", student.getId().toString()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertFalse(userRepository.existsById(student.getId()));
        student = null;

        mockMvc.perform(post("/admin/departments/save")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", department.getId().toString())
                        .param("name", "Navigation Test Department Updated")
                        .param("code", "NAVTEST2"))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals("NAVTEST2",
                departmentRepository.findById(department.getId()).orElseThrow().getCode());

        mockMvc.perform(post("/admin/departments/delete")
                        .with(adminRequest)
                        .with(csrf())
                        .param("id", department.getId().toString()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertFalse(departmentRepository.existsById(department.getId()));
        department = null;
    }
}
