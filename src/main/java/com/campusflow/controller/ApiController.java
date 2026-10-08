package com.campusflow.controller;

import com.campusflow.dto.ApiDtos.AttendanceRequest;
import com.campusflow.dto.ApiDtos.AttendanceResponse;
import com.campusflow.dto.ApiDtos.AttendanceRequest;
import com.campusflow.dto.ApiDtos.DepartmentRequest;
import com.campusflow.dto.ApiDtos.DepartmentResponse;
import com.campusflow.dto.ApiDtos.StudentRequest;
import com.campusflow.dto.ApiDtos.SubjectRequest;
import com.campusflow.dto.ApiDtos.SubjectResponse;
import com.campusflow.dto.ApiDtos.UserRequest;
import com.campusflow.dto.ApiDtos.UserResponse;
import com.campusflow.entity.Attendance;
import com.campusflow.entity.Department;
import com.campusflow.entity.Role;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.ComplaintRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final SubjectRepository subjectRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/pulse/stats")
    public Map<String, Object> getPulseStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalComplaints", complaintRepository.count());
        stats.put("totalUsers", userRepository.count());
        return stats;
    }

    @GetMapping("/admin/students")
    public List<UserResponse> getStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId) {
        String query = search == null || search.isBlank()
                ? null
                : search.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByRole(Role.STUDENT).stream()
                .filter(student -> departmentId == null
                        || student.getDepartment() != null
                        && departmentId.equals(student.getDepartment().getId()))
                .filter(student -> query == null || matchesStudent(student, query))
                .map(this::toUserResponse)
                .toList();
    }

    @GetMapping("/admin/students/{id}")
    public UserResponse getStudent(@PathVariable Long id) {
        return toUserResponse(requireStudent(id));
    }

    @PostMapping("/admin/students")
    public ResponseEntity<UserResponse> createStudent(@Valid @RequestBody StudentRequest request) {
        if (request.password() == null
                || request.password().getBytes(StandardCharsets.UTF_8).length < 8
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Student password must be between 8 and 72 UTF-8 bytes.");
        }
        ensureEmailAvailable(request.email(), null);
        Department department = requireDepartment(request.departmentId());
        User student = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(normalizeEmail(request.email()))
                .password(passwordEncoder.encode(request.password()))
                .role(Role.STUDENT)
                .department(department)
                .currentSemester(request.currentSemester())
                .build();
        User saved = saveUser(student);
        return created("/api/admin/students/" + saved.getId(), toUserResponse(saved));
    }

    @PutMapping("/admin/students/{id}")
    public UserResponse updateStudent(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        User student = requireStudent(id);
        ensureEmailAvailable(request.email(), id);
        Department department = requireDepartment(request.departmentId());
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setEmail(normalizeEmail(request.email()));
        student.setDepartment(department);
        student.setCurrentSemester(request.currentSemester());
        if (request.password() != null && !request.password().isBlank()) {
            validatePasswordBytes(request.password());
            student.setPassword(passwordEncoder.encode(request.password()));
        }
        return toUserResponse(saveUser(student));
    }

    @DeleteMapping("/admin/students/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        User student = requireStudent(id);
        deleteEntity(() -> userRepository.delete(student));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/departments")
    public List<DepartmentResponse> getDepartments() {
        return departmentRepository.findAll().stream().map(this::toDepartmentResponse).toList();
    }

    @GetMapping("/admin/departments/{id}")
    public DepartmentResponse getDepartment(@PathVariable Long id) {
        return toDepartmentResponse(requireDepartment(id));
    }

    @PostMapping("/admin/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        ensureDepartmentUnique(request, null);
        Department department = Department.builder()
                .name(request.name().trim())
                .code(request.code().trim().toUpperCase(Locale.ROOT))
                .build();
        Department saved = saveDepartment(department);
        return created("/api/admin/departments/" + saved.getId(), toDepartmentResponse(saved));
    }

    @PutMapping("/admin/departments/{id}")
    public DepartmentResponse updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        Department department = requireDepartment(id);
        ensureDepartmentUnique(request, id);
        department.setName(request.name().trim());
        department.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        return toDepartmentResponse(saveDepartment(department));
    }

    @DeleteMapping("/admin/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        Department department = requireDepartment(id);
        deleteEntity(() -> departmentRepository.delete(department));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/attendance")
    public List<AttendanceResponse> getAttendance(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer semester) {
        return attendanceRepository.findAllByOrderByDateDesc().stream()
                .filter(record -> subjectId == null || subjectId.equals(record.getSubject().getId()))
                .filter(record -> studentId == null || studentId.equals(record.getStudent().getId()))
                .filter(record -> departmentId == null || record.getStudent().getDepartment() != null
                        && departmentId.equals(record.getStudent().getDepartment().getId()))
                .filter(record -> semester == null || semester.equals(record.getStudent().getCurrentSemester()))
                .map(this::toAttendanceResponse)
                .toList();
    }

    @GetMapping("/admin/attendance/{id}")
    public AttendanceResponse getAttendanceRecord(@PathVariable Long id) {
        return toAttendanceResponse(requireAttendance(id));
    }

    @GetMapping("/faculty/attendance")
    public List<AttendanceResponse> getFacultyAttendance(
            Authentication authentication,
            @RequestParam(required = false) Long subjectId) {
        User faculty = requireAuthenticatedFaculty(authentication);
        return attendanceRepository.findAllByOrderByDateDesc().stream()
                .filter(record -> record.getSubject().getFaculty() != null
                        && faculty.getId().equals(record.getSubject().getFaculty().getId()))
                .filter(record -> subjectId == null || subjectId.equals(record.getSubject().getId()))
                .map(this::toAttendanceResponse)
                .toList();
    }

    @PostMapping("/faculty/attendance")
    public ResponseEntity<AttendanceResponse> createFacultyAttendance(
            Authentication authentication,
            @Valid @RequestBody AttendanceRequest request) {
        User faculty = requireAuthenticatedFaculty(authentication);
        Subject subject = requireFacultySubject(request.subjectId(), faculty);
        User student = requireStudentForSubject(request.studentId(), subject);
        ensureAttendanceUnique(request, null);
        Attendance attendance = Attendance.builder()
                .student(student)
                .subject(subject)
                .date(request.date())
                .status(request.status())
                .build();
        Attendance saved = saveAttendance(attendance);
        return created("/api/faculty/attendance/" + saved.getId(), toAttendanceResponse(saved));
    }

    @PutMapping("/faculty/attendance/{id}")
    public AttendanceResponse updateFacultyAttendance(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AttendanceRequest request) {
        User faculty = requireAuthenticatedFaculty(authentication);
        Attendance attendance = requireAttendance(id);
        requireFacultySubject(attendance.getSubject().getId(), faculty);
        Subject subject = requireFacultySubject(request.subjectId(), faculty);
        attendance.setStudent(requireStudentForSubject(request.studentId(), subject));
        attendance.setSubject(subject);
        attendance.setDate(request.date());
        attendance.setStatus(request.status());
        ensureAttendanceUnique(request, id);
        return toAttendanceResponse(saveAttendance(attendance));
    }

    @GetMapping("/admin/subjects")
    public List<SubjectResponse> getSubjects(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long facultyId) {
        return subjectRepository.findAll().stream()
                .filter(subject -> departmentId == null || subject.getDepartment() != null
                        && departmentId.equals(subject.getDepartment().getId()))
                .filter(subject -> facultyId == null || subject.getFaculty() != null
                        && facultyId.equals(subject.getFaculty().getId()))
                .map(this::toSubjectResponse)
                .toList();
    }

    @GetMapping("/admin/subjects/{id}")
    public SubjectResponse getSubject(@PathVariable Long id) {
        return toSubjectResponse(requireSubject(id));
    }

    @PostMapping("/admin/subjects")
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest request) {
        Subject subject = new Subject();
        updateSubjectFromRequest(subject, request);
        Subject saved = saveSubject(subject);
        return created("/api/admin/subjects/" + saved.getId(), toSubjectResponse(saved));
    }

    @PutMapping("/admin/subjects/{id}")
    public SubjectResponse updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        Subject subject = requireSubject(id);
        updateSubjectFromRequest(subject, request);
        return toSubjectResponse(saveSubject(subject));
    }

    @DeleteMapping("/admin/subjects/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        Subject subject = requireSubject(id);
        deleteEntity(() -> subjectRepository.delete(subject));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/users")
    public List<UserResponse> getUsers(@RequestParam(required = false) Role role) {
        List<User> users = role == null ? userRepository.findAll() : userRepository.findByRole(role);
        return users.stream().map(this::toUserResponse).toList();
    }

    @GetMapping("/admin/users/me")
    public UserResponse getCurrentAdmin(Authentication authentication) {
        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> notFound("Authenticated user not found."));
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Admin access required.");
        }
        return toUserResponse(currentUser);
    }

    @GetMapping("/admin/users/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return toUserResponse(requireUser(id));
    }

    @PostMapping("/admin/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        validatePasswordBytes(request.password());
        ensureEmailAvailable(request.email(), null);
        User user = new User();
        updateUserFromRequest(user, request);
        user.setPassword(passwordEncoder.encode(request.password()));
        User saved = saveUser(user);
        return created("/api/admin/users/" + saved.getId(), toUserResponse(saved));
    }

    @PutMapping("/admin/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        User user = requireUser(id);
        ensureEmailAvailable(request.email(), id);
        updateUserFromRequest(user, request);
        if (request.password() != null && !request.password().isBlank()) {
            validatePasswordBytes(request.password());
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return toUserResponse(saveUser(user));
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {
        User user = requireUser(id);
        if (user.getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(CONFLICT, "The authenticated admin account cannot delete itself.");
        }
        deleteEntity(() -> userRepository.delete(user));
        return ResponseEntity.noContent().build();
    }

    private void updateUserFromRequest(User user, UserRequest request) {
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(normalizeEmail(request.email()));
        user.setRole(request.role());
        user.setDepartment(request.departmentId() == null ? null : requireDepartment(request.departmentId()));
        user.setCurrentSemester(request.currentSemester());
    }

    private void updateSubjectFromRequest(Subject subject, SubjectRequest request) {
        subjectRepository.findByCode(request.code().trim())
                .filter(existing -> !existing.getId().equals(subject.getId()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(CONFLICT, "Subject code is already in use.");
                });
        subject.setName(request.name().trim());
        subject.setCode(request.code().trim());
        subject.setSemester(request.semester());
        subject.setDepartment(request.departmentId() == null ? null : requireDepartment(request.departmentId()));
        subject.setFaculty(request.facultyId() == null ? null : requireFaculty(request.facultyId()));
    }

    private User requireStudent(Long id) {
        User user = requireUser(id);
        if (user.getRole() != Role.STUDENT) {
            throw notFound("Student not found.");
        }
        return user;
    }

    private User requireFaculty(Long id) {
        User user = requireUser(id);
        if (user.getRole() != Role.FACULTY) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Selected user is not a faculty member.");
        }
        return user;
    }

    private User requireAuthenticatedFaculty(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .filter(user -> user.getRole() == Role.FACULTY)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Faculty access required."));
    }

    private Subject requireFacultySubject(Long id, User faculty) {
        Subject subject = requireSubject(id);
        if (subject.getFaculty() == null || !faculty.getId().equals(subject.getFaculty().getId())) {
            throwForbiddenSubject();
        }
        return subject;
    }

    private void throwForbiddenSubject() {
        throw new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Attendance can only be managed for your assigned subjects.");
    }

    private User requireStudentForSubject(Long studentId, Subject subject) {
        User student = requireStudent(studentId);
        if (!studentMatchesSubject(student, subject)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Selected student is not enrolled in this subject's department and semester.");
        }
        return student;
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> notFound("User not found."));
    }

    private Department requireDepartment(Long id) {
        return departmentRepository.findById(id).orElseThrow(() -> notFound("Department not found."));
    }

    private Subject requireSubject(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> notFound("Subject not found."));
    }

    private Attendance requireAttendance(Long id) {
        return attendanceRepository.findById(id).orElseThrow(() -> notFound("Attendance record not found."));
    }

    private void ensureDepartmentUnique(DepartmentRequest request, Long currentId) {
        Department duplicateName = departmentRepository.findAll().stream()
                .filter(department -> department.getName() != null
                        && department.getName().equalsIgnoreCase(request.name().trim()))
                .filter(department -> !department.getId().equals(currentId))
                .findFirst().orElse(null);
        boolean duplicateCode = departmentRepository.existsByCodeIgnoreCase(request.code().trim());
        Department sameCode = duplicateCode ? departmentRepository.findAll().stream()
                .filter(department -> department.getCode() != null
                        && department.getCode().equalsIgnoreCase(request.code().trim()))
                .filter(department -> !department.getId().equals(currentId))
                .findFirst().orElse(null) : null;
        if (duplicateName != null || sameCode != null) {
            throw new ResponseStatusException(CONFLICT, "Department name or code is already in use.");
        }
    }

    private void ensureEmailAvailable(String email, Long currentId) {
        User existing = userRepository.findByEmail(normalizeEmail(email)).orElse(null);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new ResponseStatusException(CONFLICT, "Email address is already in use.");
        }
    }

    private void ensureAttendanceUnique(AttendanceRequest request, Long currentId) {
        attendanceRepository.findByStudentIdAndSubjectIdAndDate(
                        request.studentId(), request.subjectId(), request.date())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(CONFLICT,
                            "Attendance already exists for this student, subject, and date.");
                });
    }

    private boolean studentMatchesSubject(User student, Subject subject) {
        return (subject.getDepartment() == null || student.getDepartment() != null
                && subject.getDepartment().getId().equals(student.getDepartment().getId()))
                && (subject.getSemester() == null || subject.getSemester().equals(student.getCurrentSemester()));
    }

    private boolean matchesStudent(User student, String query) {
        String fullName = ((student.getFirstName() == null ? "" : student.getFirstName())
                + " " + (student.getLastName() == null ? "" : student.getLastName())).toLowerCase(Locale.ROOT);
        return fullName.contains(query) || student.getEmail().toLowerCase(Locale.ROOT).contains(query);
    }

    private User saveUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(CONFLICT, "User data conflicts with an existing database record.");
        }
    }

    private Department saveDepartment(Department department) {
        try {
            return departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(CONFLICT, "Department name is already in use.");
        }
    }

    private Attendance saveAttendance(Attendance attendance) {
        try {
            return attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(CONFLICT, "Attendance data conflicts with an existing record.");
        }
    }

    private Subject saveSubject(Subject subject) {
        try {
            return subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(CONFLICT, "Subject data conflicts with an existing database record.");
        }
    }

    private void deleteEntity(Runnable deleteAction) {
        try {
            deleteAction.run();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(CONFLICT,
                    "This record is referenced by other data and cannot be deleted.");
        }
    }

    private void validatePasswordBytes(String password) {
        if (password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Password must be between 8 and 72 UTF-8 bytes.");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getCurrentSemester());
    }

    private DepartmentResponse toDepartmentResponse(Department department) {
        return new DepartmentResponse(department.getId(), department.getName(), department.getCode());
    }

    private AttendanceResponse toAttendanceResponse(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getStudent().getId(),
                fullName(attendance.getStudent()),
                attendance.getStudent().getEmail(),
                attendance.getSubject().getId(),
                attendance.getSubject().getName(),
                attendance.getSubject().getCode(),
                attendance.getDate(),
                attendance.getStatus());
    }

    private SubjectResponse toSubjectResponse(Subject subject) {
        User faculty = subject.getFaculty();
        Department department = subject.getDepartment();
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getCode(),
                subject.getSemester(),
                department == null ? null : department.getId(),
                department == null ? null : department.getName(),
                faculty == null ? null : faculty.getId(),
                faculty == null ? null : fullName(faculty),
                faculty == null ? null : faculty.getEmail());
    }

    private String fullName(User user) {
        return ((user.getFirstName() == null ? "" : user.getFirstName())
                + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
    }

    private ResponseEntity<UserResponse> created(String path, UserResponse body) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path(path).build().toUri();
        return ResponseEntity.created(location).body(body);
    }

    private <T> ResponseEntity<T> created(String path, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path(path).build().toUri();
        return ResponseEntity.created(location).body(body);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(NOT_FOUND, message);
    }
}
