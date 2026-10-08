package com.campusflow;

import com.campusflow.entity.Complaint;
import com.campusflow.entity.Role;
import com.campusflow.entity.User;
import com.campusflow.repository.ComplaintRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentPortalTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    private User student;

    @BeforeEach
    void setUp() {
        student = userRepository.saveAndFlush(User.builder()
                .email("student-portal-" + System.nanoTime() + "@campusflow.test")
                .password("test-password")
                .firstName("Portal")
                .lastName("Student")
                .role(Role.STUDENT)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (student != null && userRepository.existsById(student.getId())) {
            List<Complaint> complaints = complaintRepository.findByStudentId(student.getId());
            complaintRepository.deleteAll(complaints);
            userRepository.deleteById(student.getId());
        }
    }

    @Test
    void studentNavigationPagesRenderForTheAuthenticatedStudent() throws Exception {
        var studentRequest = SecurityMockMvcRequestPostProcessors.user(student.getEmail()).roles("STUDENT");
        for (String route : List.of(
                "/student/dashboard",
                "/student/academics",
                "/student/attendance",
                "/student/assignments",
                "/student/materials",
                "/student/events",
                "/student/notices",
                "/student/fees",
                "/student/helpdesk",
                "/student/profile")) {
            mockMvc.perform(get(route).with(studentRequest))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void studentsCannotOpenOtherRolePortals() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(SecurityMockMvcRequestPostProcessors.user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/student/dashboard")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin@campusflow.test").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void helpdeskRequestIsStoredForTheAuthenticatedStudent() throws Exception {
        mockMvc.perform(post("/student/helpdesk")
                        .with(SecurityMockMvcRequestPostProcessors.user(student.getEmail()).roles("STUDENT"))
                        .with(csrf())
                        .param("title", "Portal test request")
                        .param("description", "A request created by a Student Portal test."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/helpdesk"));

        List<Complaint> complaints = complaintRepository.findByStudentId(student.getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, complaints.size());
        org.junit.jupiter.api.Assertions.assertEquals("Portal test request", complaints.get(0).getTitle());
    }
}
