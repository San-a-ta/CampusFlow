package com.campusflow;

import com.campusflow.entity.Role;
import com.campusflow.entity.User;
import com.campusflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminNavigationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .email("admin-navigation-test@campusflow.test")
                .password("test-password")
                .firstName("Navigation")
                .lastName("Test")
                .role(Role.ADMIN)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (admin != null) {
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
}
