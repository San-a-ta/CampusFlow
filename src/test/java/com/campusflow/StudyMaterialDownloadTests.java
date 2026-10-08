package com.campusflow;

import com.campusflow.entity.Department;
import com.campusflow.entity.Role;
import com.campusflow.entity.StudyMaterial;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.StudyMaterialRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudyMaterialDownloadTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Value("${campusflow.upload.directory:uploads}")
    private String uploadDirectory;

    private Department department;
    private User faculty;
    private User authorizedStudent;
    private User unauthorizedStudent;
    private Subject subject;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime(), 36);
        department = departmentRepository.saveAndFlush(Department.builder()
                .name("Material Test " + suffix)
                .code("MAT" + suffix)
                .build());
        faculty = userRepository.saveAndFlush(User.builder()
                .email("material-faculty-" + suffix + "@campusflow.test")
                .password("test-password")
                .firstName("Material")
                .lastName("Faculty")
                .role(Role.FACULTY)
                .build());
        authorizedStudent = userRepository.saveAndFlush(User.builder()
                .email("material-student-" + suffix + "@campusflow.test")
                .password("test-password")
                .firstName("Authorized")
                .lastName("Student")
                .role(Role.STUDENT)
                .department(department)
                .currentSemester(3)
                .build());
        unauthorizedStudent = userRepository.saveAndFlush(User.builder()
                .email("other-material-student-" + suffix + "@campusflow.test")
                .password("test-password")
                .firstName("Other")
                .lastName("Student")
                .role(Role.STUDENT)
                .build());
        subject = subjectRepository.saveAndFlush(Subject.builder()
                .name("Material Test Subject")
                .code("MTS" + suffix)
                .semester(3)
                .department(department)
                .faculty(faculty)
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (subject != null && subjectRepository.existsById(subject.getId())) {
            List<StudyMaterial> materials = studyMaterialRepository.findBySubjectId(subject.getId());
            studyMaterialRepository.deleteAll(materials);
            studyMaterialRepository.flush();
            Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
            Path allowedDirectory = root.resolve("study-materials").normalize();
            for (StudyMaterial material : materials) {
                if (material.getFileUrl() != null && material.getFileUrl().startsWith("study-materials/")) {
                    Path file = root.resolve(material.getFileUrl()).normalize();
                    if (file.startsWith(allowedDirectory)) {
                        Files.deleteIfExists(file);
                    }
                }
            }
            subjectRepository.delete(subject);
            subjectRepository.flush();
        }
        if (authorizedStudent != null && userRepository.existsById(authorizedStudent.getId())) {
            userRepository.delete(authorizedStudent);
        }
        if (unauthorizedStudent != null && userRepository.existsById(unauthorizedStudent.getId())) {
            userRepository.delete(unauthorizedStudent);
        }
        if (faculty != null && userRepository.existsById(faculty.getId())) {
            userRepository.delete(faculty);
        }
        userRepository.flush();
        if (department != null && departmentRepository.existsById(department.getId())) {
            departmentRepository.delete(department);
            departmentRepository.flush();
        }
    }

    @Test
    void adminUploadCreatesDownloadableMaterialAndStudentAccessIsScoped() throws Exception {
        byte[] fileContent = "Study material download test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "course-notes.txt", "text/plain", fileContent);

        mockMvc.perform(multipart("/admin/documents/save")
                        .file(upload)
                        .with(SecurityMockMvcRequestPostProcessors.user("material-admin@campusflow.test").roles("ADMIN"))
                        .with(csrf())
                        .param("title", "Material download test")
                        .param("description", "Uploaded through the Admin document form.")
                        .param("subjectId", subject.getId().toString())
                        .param("facultyId", faculty.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/documents"));

        StudyMaterial material = studyMaterialRepository.findAll().stream()
                .filter(item -> "Material download test".equals(item.getTitle()))
                .findFirst()
                .orElseThrow();
        Path uploadedFile = Path.of(uploadDirectory).toAbsolutePath().normalize()
                .resolve(material.getFileUrl()).normalize();
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(uploadedFile));
        org.junit.jupiter.api.Assertions.assertArrayEquals(fileContent, Files.readAllBytes(uploadedFile));

        mockMvc.perform(get("/student/materials/{id}/download", material.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(authorizedStudent.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(fileContent));

        mockMvc.perform(get("/student/materials/{id}/download", material.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(unauthorizedStudent.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingMaterialFileReturnsClearMessageWithoutChangingItsRecord() throws Exception {
        StudyMaterial staleMaterial = studyMaterialRepository.saveAndFlush(StudyMaterial.builder()
                .title("Missing material file test")
                .fileUrl("study-materials/" + System.nanoTime() + "-missing.txt")
                .subject(subject)
                .faculty(faculty)
                .uploadDate(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/student/materials/{id}/download", staleMaterial.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(authorizedStudent.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("This study material file is unavailable. Please contact your administrator."));

        org.junit.jupiter.api.Assertions.assertTrue(studyMaterialRepository.existsById(staleMaterial.getId()));
    }
}
