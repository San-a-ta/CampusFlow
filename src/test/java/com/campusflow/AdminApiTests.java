package com.campusflow;

import com.campusflow.entity.Attendance;
import com.campusflow.entity.Department;
import com.campusflow.entity.Role;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTests {

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

    private User admin;
    private User student;
    private User managedUser;
    private Department department;
    private Subject subject;
    private Attendance attendance;

    @BeforeEach
    void setUp() {
        cleanupApiTestFixtures();
        admin = userRepository.save(User.builder()
                .email("admin-api-" + System.nanoTime() + "@campusflow.test")
                .password("encoded-test-password")
                .firstName("API")
                .lastName("Admin")
                .role(Role.ADMIN)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (attendance != null && attendanceRepository.existsById(attendance.getId())) {
            attendanceRepository.deleteById(attendance.getId());
        }
        if (subject != null && subjectRepository.existsById(subject.getId())) {
            subjectRepository.deleteById(subject.getId());
        }
        if (student != null && userRepository.existsById(student.getId())) {
            userRepository.deleteById(student.getId());
        }
        if (managedUser != null && userRepository.existsById(managedUser.getId())) {
            userRepository.deleteById(managedUser.getId());
        }
        if (department != null && departmentRepository.existsById(department.getId())) {
            departmentRepository.deleteById(department.getId());
        }
        if (admin != null && userRepository.existsById(admin.getId())) {
            userRepository.deleteById(admin.getId());
        }
    }

    private void cleanupApiTestFixtures() {
        List<Attendance> oldAttendance = attendanceRepository.findAll().stream()
                .filter(record -> record.getStudent().getEmail().startsWith("api-student-")
                        || "API Test Subject".equals(record.getSubject().getName()))
                .toList();
        attendanceRepository.deleteAll(oldAttendance);

        subjectRepository.deleteAll(subjectRepository.findAll().stream()
                .filter(existing -> "API Test Subject".equals(existing.getName()))
                .toList());
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(existing -> existing.getEmail().startsWith("api-student-"))
                .toList());
        departmentRepository.deleteAll(departmentRepository.findAll().stream()
                .filter(existing -> existing.getName().startsWith("API Department API")
                        || existing.getName().startsWith("API Test Department "))
                .toList());
    }

    @Test
    void pulseApiRemainsAvailableToAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/api/pulse/stats")
                        .with(SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").exists())
                .andExpect(jsonPath("$.totalComplaints").exists());
    }

    @Test
    void adminOnlyApiReturnsUnauthorizedAndForbiddenForUnauthorizedCallers() throws Exception {
        mockMvc.perform(get("/api/admin/departments").accept("application/json"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/departments")
                        .accept("application/json")
                        .with(SecurityMockMvcRequestPostProcessors.user("student@campusflow.test").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void departmentsCanBeCreatedReadUpdatedAndDeleted() throws Exception {
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
        String code = "API" + Long.toString(System.nanoTime(), 36).toUpperCase();
        String createBody = "{\"name\":\"API Department " + code + "\",\"code\":\"" + code + "\"}";
        var created = mockMvc.perform(post("/api/admin/departments")
                        .with(adminRequest).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();
        department = departmentRepository.findById(id).orElseThrow();

        mockMvc.perform(get("/api/admin/departments/{id}", id).with(adminRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
        mockMvc.perform(put("/api/admin/departments/{id}", id)
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Updated API Department\",\"code\":\"" + code + "U\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated API Department"));
        mockMvc.perform(delete("/api/admin/departments/{id}", id).with(adminRequest).with(csrf()))
                .andExpect(status().isNoContent());
        department = null;
    }

    @Test
    void studentAndSubjectAttendanceApisUseExistingEntitiesAndHidePasswords() throws Exception {
        department = departmentRepository.save(Department.builder()
                .name("API Test Department " + System.nanoTime())
                .code("D" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");

        String email = "api-student-" + System.nanoTime() + "@campusflow.test";
        var createdStudent = mockMvc.perform(post("/api/admin/students")
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"firstName":"API","lastName":"Student","email":"%s",
                                 "password":"initial-password-1","departmentId":%d,"currentSemester":2}
                                """.formatted(email, department.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        Long studentId = ((Number) com.jayway.jsonpath.JsonPath.read(
                createdStudent.getResponse().getContentAsString(), "$.id")).longValue();
        student = userRepository.findById(studentId).orElseThrow();
        managedUser = userRepository.save(User.builder()
                .email("api-faculty-" + System.nanoTime() + "@campusflow.test")
                .password("encoded-test-password")
                .firstName("API")
                .lastName("Faculty")
                .role(Role.FACULTY)
                .department(department)
                .build());

        mockMvc.perform(put("/api/admin/students/{id}", studentId)
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Updated","lastName":"Student","email":"%s",
                                 "departmentId":%d,"currentSemester":3}
                                """.formatted(email, department.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
        mockMvc.perform(get("/api/admin/students")
                        .with(adminRequest)
                        .param("search", "Updated Student")
                        .param("departmentId", department.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(studentId));

        String subjectCode = "S" + Long.toString(System.nanoTime(), 36).toUpperCase();
        var createdSubject = mockMvc.perform(post("/api/admin/subjects")
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"API Test Subject","code":"%s","semester":3,"departmentId":%d,"facultyId":%d}
                                """.formatted(subjectCode, department.getId(), managedUser.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentId").value(department.getId()))
                .andReturn();
        Long subjectId = ((Number) com.jayway.jsonpath.JsonPath.read(
                createdSubject.getResponse().getContentAsString(), "$.id")).longValue();
        subject = subjectRepository.findById(subjectId).orElseThrow();

        mockMvc.perform(put("/api/admin/subjects/{id}", subjectId)
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Updated API Subject","code":"%s","semester":3,"departmentId":%d,"facultyId":%d}
                                """.formatted(subjectCode, department.getId(), managedUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated API Subject"));

        String attendanceRequestBody = """
                {"studentId":%d,"subjectId":%d,"date":"2026-10-07","status":"PRESENT"}
                """.formatted(studentId, subjectId);
        mockMvc.perform(post("/api/admin/attendance")
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content(attendanceRequestBody))
                .andExpect(status().isMethodNotAllowed());

        var facultyRequest = SecurityMockMvcRequestPostProcessors.user(managedUser.getEmail()).roles("FACULTY");
        var createdAttendance = mockMvc.perform(post("/api/faculty/attendance")
                        .with(facultyRequest).with(csrf())
                        .contentType("application/json")
                        .content(attendanceRequestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentEmail").value(email))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andReturn();
        Long attendanceId = ((Number) com.jayway.jsonpath.JsonPath.read(
                createdAttendance.getResponse().getContentAsString(), "$.id")).longValue();
        attendance = attendanceRepository.findByStudentIdAndSubjectIdAndDate(
                studentId, subjectId, java.time.LocalDate.parse("2026-10-07")).orElseThrow();

        mockMvc.perform(put("/api/faculty/attendance/{id}", attendanceId)
                        .with(facultyRequest).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"studentId":%d,"subjectId":%d,"date":"2026-10-07","status":"LATE"}
                                """.formatted(studentId, subjectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LATE"));
        mockMvc.perform(get("/api/faculty/attendance").with(adminRequest))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/attendance")
                        .with(adminRequest).param("subjectId", subjectId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].subjectCode").value(subjectCode));
        mockMvc.perform(get("/api/admin/users/me").with(adminRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(admin.getEmail()))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(delete("/api/admin/attendance/{id}", attendance.getId())
                        .with(adminRequest).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        attendanceRepository.delete(attendance);
        attendanceRepository.flush();
        attendance = null;
        mockMvc.perform(delete("/api/admin/subjects/{id}", subjectId)
                        .with(adminRequest).with(csrf()))
                .andExpect(status().isNoContent());
        subject = null;
        userRepository.delete(managedUser);
        managedUser = null;
        mockMvc.perform(delete("/api/admin/students/{id}", studentId)
                        .with(adminRequest).with(csrf()))
                .andExpect(status().isNoContent());
        student = null;
    }

    @Test
    void adminUserApiCreatesUpdatesAndDeletesWithoutExposingPasswords() throws Exception {
        var adminRequest = SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN");
        String email = "managed-api-user-" + System.nanoTime() + "@campusflow.test";
        var created = mockMvc.perform(post("/api/admin/users")
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Managed","lastName":"User","email":"%s",
                                 "password":"initial-password-1","role":"FACULTY"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        Long userId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();
        managedUser = userRepository.findById(userId).orElseThrow();

        mockMvc.perform(put("/api/admin/users/{id}", userId)
                        .with(adminRequest).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Updated","lastName":"User","email":"%s","role":"HOD"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HOD"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(delete("/api/admin/users/{id}", userId)
                        .with(adminRequest).with(csrf()))
                .andExpect(status().isNoContent());
        managedUser = null;
    }

    @Test
    void invalidDepartmentPayloadReturnsStructuredValidationErrors() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .with(SecurityMockMvcRequestPostProcessors.user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\" \",\"code\":\"bad code\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.code").exists());
    }
}
