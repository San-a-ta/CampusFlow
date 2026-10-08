package com.campusflow.controller;

import com.campusflow.entity.Assignment;
import com.campusflow.entity.Attendance;
import com.campusflow.entity.AttendanceStatus;
import com.campusflow.entity.Complaint;
import com.campusflow.entity.ComplaintStatus;
import com.campusflow.entity.Department;
import com.campusflow.entity.Event;
import com.campusflow.entity.Fee;
import com.campusflow.entity.FeePaymentStatus;
import com.campusflow.entity.Role;
import com.campusflow.entity.StudyMaterial;
import com.campusflow.entity.Subject;
import com.campusflow.entity.Submission;
import com.campusflow.entity.User;
import com.campusflow.dto.AdminViewModels.ComplaintSummary;
import com.campusflow.dto.AdminViewModels.DepartmentFees;
import com.campusflow.dto.AdminViewModels.DepartmentStudents;
import com.campusflow.dto.AdminViewModels.StudentSubjectPerformance;
import com.campusflow.repository.AssignmentRepository;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.ComplaintRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.EventRepository;
import com.campusflow.repository.FeeRepository;
import com.campusflow.repository.NoticeRepository;
import com.campusflow.repository.StudyMaterialRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.SubmissionRepository;
import com.campusflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NoticeRepository noticeRepository;
    private final EventRepository eventRepository;
    private final ComplaintRepository complaintRepository;
    private final AttendanceRepository attendanceRepository;
    private final SubjectRepository subjectRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final FeeRepository feeRepository;
    private final SubmissionRepository submissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${campusflow.upload.directory:uploads}")
    private String uploadDirectory;
    
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        
        model.addAttribute("totalStudents", userRepository.findByRole(com.campusflow.entity.Role.STUDENT).size());
        model.addAttribute("totalFaculty", userRepository.findByRole(com.campusflow.entity.Role.FACULTY).size());
        model.addAttribute("totalDepartments", departmentRepository.count());
        model.addAttribute("openComplaints", complaintRepository.count()); // simplified
        
        return "admin/dashboard";
    }

    @GetMapping("/students")
    public String students(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<User> studentList = userRepository.findByRole(Role.STUDENT);
        if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase(Locale.ROOT);
            studentList = studentList.stream()
                    .filter(student -> ((student.getFirstName() == null ? "" : student.getFirstName())
                            + " " + (student.getLastName() == null ? "" : student.getLastName()))
                            .toLowerCase(Locale.ROOT).contains(query)
                            || student.getEmail().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }
        if (departmentId != null) {
            studentList = studentList.stream()
                    .filter(student -> student.getDepartment() != null
                            && departmentId.equals(student.getDepartment().getId()))
                    .toList();
        }
        model.addAttribute("user", user);
        model.addAttribute("students", studentList);
        model.addAttribute("departmentGroups", groupStudentsByDepartment(studentList));
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("search", search);
        model.addAttribute("selectedDepartmentId", departmentId);
        return "admin/students";
    }

    @GetMapping("/students/{id}")
    public String studentAcademicRecord(
            Authentication authentication,
            @PathVariable Long id,
            Model model) {
        User admin = userRepository.findByEmail(authentication.getName()).orElseThrow();
        User student = userRepository.findById(id)
                .filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Student not found."));
        List<Attendance> attendance = attendanceRepository.findByStudentId(student.getId());
        List<Submission> submissions = submissionRepository.findByStudentId(student.getId());
        List<Subject> studentSubjects = subjectRepository.findAll().stream()
                .filter(subject -> student.getDepartment() != null && subject.getDepartment() != null
                        && student.getDepartment().getId().equals(subject.getDepartment().getId()))
                .filter(subject -> student.getCurrentSemester() == null || subject.getSemester() == null
                        || student.getCurrentSemester().equals(subject.getSemester()))
                .sorted(Comparator.comparing(Subject::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<StudentSubjectPerformance> subjectPerformance = studentSubjects.stream()
                .map(subject -> {
                    List<Attendance> subjectAttendance = attendance.stream()
                            .filter(record -> record.getSubject().getId().equals(subject.getId()))
                            .toList();
                    List<Submission> subjectSubmissions = submissions.stream()
                            .filter(submission -> submission.getAssignment() != null
                                    && submission.getAssignment().getSubject() != null
                                    && submission.getAssignment().getSubject().getId().equals(subject.getId()))
                            .toList();
                    long present = subjectAttendance.stream()
                            .filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count();
                    double percentage = subjectAttendance.isEmpty() ? 0 : present * 100.0 / subjectAttendance.size();
                    return new StudentSubjectPerformance(
                            subject, subjectSubmissions, subjectAttendance.size(), present, percentage);
                })
                .toList();
        long presentCount = attendance.stream()
                .filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count();
        model.addAttribute("user", admin);
        model.addAttribute("student", student);
        model.addAttribute("subjectPerformance", subjectPerformance);
        model.addAttribute("attendanceCount", attendance.size());
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("attendancePercentage",
                attendance.isEmpty() ? 0 : presentCount * 100.0 / attendance.size());
        model.addAttribute("markedSubmissionCount", submissions.stream()
                .filter(submission -> submission.getMarksAwarded() != null).count());
        return "admin/student-performance";
    }

    @PostMapping("/students/save")
    public String saveStudent(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String currentSemester,
            RedirectAttributes redirectAttributes) {
        if (firstName == null || firstName.isBlank() || firstName.trim().length() > 100
                || lastName == null || lastName.isBlank() || lastName.trim().length() > 100) {
            redirectAttributes.addFlashAttribute("error", "Enter the student's first and last names (up to 100 characters each).");
            return "redirect:/admin/students";
        }
        if (email == null || email.isBlank() || email.trim().length() > 255
                || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            redirectAttributes.addFlashAttribute("error", "Enter a valid email address.");
            return "redirect:/admin/students";
        }

        User student = id == null ? new User() : userRepository.findById(id).orElse(null);
        if (student == null || (id != null && student.getRole() != Role.STUDENT)) {
            redirectAttributes.addFlashAttribute("error", "Student record not found.");
            return "redirect:/admin/students";
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User emailOwner = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (emailOwner != null && (id == null || !emailOwner.getId().equals(id))) {
            redirectAttributes.addFlashAttribute("error", "An account with that email address already exists.");
            return "redirect:/admin/students";
        }
        if (id == null && (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)) {
            redirectAttributes.addFlashAttribute("error", "A password of 8 to 72 characters is required for a new student.");
            return "redirect:/admin/students";
        }
        Department department = departmentId == null ? null : departmentRepository.findById(departmentId).orElse(null);
        if (department == null) {
            redirectAttributes.addFlashAttribute("error", "Select a valid department.");
            return "redirect:/admin/students";
        }

        Integer parsedSemester = null;
        if (currentSemester != null && !currentSemester.isBlank()) {
            try {
                parsedSemester = Integer.valueOf(currentSemester);
            } catch (NumberFormatException ex) {
                redirectAttributes.addFlashAttribute("error", "Semester must be a number from 1 to 12.");
                return "redirect:/admin/students";
            }
            if (parsedSemester < 1 || parsedSemester > 12) {
                redirectAttributes.addFlashAttribute("error", "Semester must be from 1 to 12.");
                return "redirect:/admin/students";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Select the student's current semester.");
            return "redirect:/admin/students";
        }

        student.setFirstName(firstName.trim());
        student.setLastName(lastName.trim());
        student.setEmail(normalizedEmail);
        student.setRole(Role.STUDENT);
        student.setDepartment(department);
        student.setCurrentSemester(parsedSemester);
        if (id == null) {
            student.setPassword(passwordEncoder.encode(password));
        }
        try {
            userRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "Student could not be saved because the email address is already in use.");
            return "redirect:/admin/students";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "Student could not be saved because of a database error. Please try again.");
            return "redirect:/admin/students";
        }
        redirectAttributes.addFlashAttribute("success", id == null
                ? "Student added successfully."
                : "Student updated successfully.");
        return "redirect:/admin/students";
    }

    @PostMapping("/students/delete")
    public String deleteStudent(@RequestParam(required = false) Long id, RedirectAttributes redirectAttributes) {
        User student = id == null ? null : userRepository.findById(id).orElse(null);
        if (student == null || student.getRole() != Role.STUDENT) {
            redirectAttributes.addFlashAttribute("error", "Student record not found.");
            return "redirect:/admin/students";
        }
        try {
            userRepository.delete(student);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "This student has related records and cannot be deleted.");
            return "redirect:/admin/students";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "Student could not be deleted because of a database error. Please try again.");
            return "redirect:/admin/students";
        }
        redirectAttributes.addFlashAttribute("success", "Student deleted successfully.");
        return "redirect:/admin/students";
    }

    @GetMapping("/faculty")
    public String faculty(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<User> facultyList = userRepository.findByRole(Role.FACULTY);
        if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase(Locale.ROOT);
            facultyList = facultyList.stream()
                    .filter(faculty -> ((faculty.getFirstName() == null ? "" : faculty.getFirstName())
                            + " " + (faculty.getLastName() == null ? "" : faculty.getLastName()))
                            .toLowerCase(Locale.ROOT).contains(query)
                            || faculty.getEmail().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }
        if (departmentId != null) {
            facultyList = facultyList.stream()
                    .filter(faculty -> faculty.getDepartment() != null
                            && departmentId.equals(faculty.getDepartment().getId()))
                    .toList();
        }
        model.addAttribute("user", user);
        model.addAttribute("facultyList", facultyList);
        model.addAttribute("facultyCount", userRepository.findByRole(Role.FACULTY).size());
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("search", search);
        model.addAttribute("selectedDepartmentId", departmentId);
        return "admin/faculty";
    }

    @PostMapping("/faculty/save")
    public String saveFaculty(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) Long departmentId,
            RedirectAttributes redirectAttributes) {
        if (firstName == null || firstName.isBlank() || firstName.trim().length() > 100
                || lastName == null || lastName.isBlank() || lastName.trim().length() > 100) {
            redirectAttributes.addFlashAttribute("error", "Enter the faculty member's first and last names (up to 100 characters each).");
            return "redirect:/admin/faculty";
        }
        if (email == null || email.isBlank() || email.trim().length() > 255
                || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            redirectAttributes.addFlashAttribute("error", "Enter a valid email address.");
            return "redirect:/admin/faculty";
        }

        User faculty = id == null ? new User() : userRepository.findById(id).orElse(null);
        if (faculty == null || (id != null && faculty.getRole() != Role.FACULTY)) {
            redirectAttributes.addFlashAttribute("error", "Faculty record not found.");
            return "redirect:/admin/faculty";
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User emailOwner = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (emailOwner != null && (id == null || !emailOwner.getId().equals(id))) {
            redirectAttributes.addFlashAttribute("error", "An account with that email address already exists.");
            return "redirect:/admin/faculty";
        }
        if (id == null && (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)) {
            redirectAttributes.addFlashAttribute("error", "A password of 8 to 72 characters is required for a new faculty account.");
            return "redirect:/admin/faculty";
        }
        Department department = departmentId == null ? null
                : departmentRepository.findById(departmentId).orElse(null);
        if (departmentId != null && department == null) {
            redirectAttributes.addFlashAttribute("error", "Select a valid department.");
            return "redirect:/admin/faculty";
        }

        faculty.setFirstName(firstName.trim());
        faculty.setLastName(lastName.trim());
        faculty.setEmail(normalizedEmail);
        faculty.setRole(Role.FACULTY);
        faculty.setDepartment(department);
        faculty.setCurrentSemester(null);
        if (id == null) {
            faculty.setPassword(passwordEncoder.encode(password));
        }
        try {
            userRepository.saveAndFlush(faculty);
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "Faculty member could not be saved because the email address is already in use.");
            return "redirect:/admin/faculty";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "Faculty member could not be saved because of a database error. Please try again.");
            return "redirect:/admin/faculty";
        }
        redirectAttributes.addFlashAttribute("success", id == null
                ? "Faculty member added successfully."
                : "Faculty member updated successfully.");
        return "redirect:/admin/faculty";
    }

    @PostMapping("/faculty/delete")
    public String deleteFaculty(
            @RequestParam(required = false) Long id,
            RedirectAttributes redirectAttributes) {
        User faculty = id == null ? null : userRepository.findById(id).orElse(null);
        if (faculty == null || faculty.getRole() != Role.FACULTY) {
            redirectAttributes.addFlashAttribute("error", "Faculty record not found.");
            return "redirect:/admin/faculty";
        }
        try {
            userRepository.delete(faculty);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "This faculty member has related records and cannot be deleted.");
            return "redirect:/admin/faculty";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "Faculty member could not be deleted because of a database error. Please try again.");
            return "redirect:/admin/faculty";
        }
        redirectAttributes.addFlashAttribute("success", "Faculty member deleted successfully.");
        return "redirect:/admin/faculty";
    }

    @GetMapping("/departments")
    public String departments(
            Authentication authentication,
            @RequestParam(required = false) String search,
            Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<Department> departments = departmentRepository.findAll();
        if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase(Locale.ROOT);
            departments = departments.stream()
                    .filter(department -> department.getName().toLowerCase(Locale.ROOT).contains(query)
                            || (department.getCode() != null
                            && department.getCode().toLowerCase(Locale.ROOT).contains(query)))
                    .toList();
        }
        model.addAttribute("user", user);
        model.addAttribute("departments", departments);
        model.addAttribute("departmentOptions", departmentRepository.findAll());
        model.addAttribute("departmentCount", departmentRepository.count());
        model.addAttribute("search", search);
        return "admin/departments";
    }

    @PostMapping("/departments/save")
    public String saveDepartment(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            RedirectAttributes redirectAttributes) {
        if (name == null || name.isBlank() || name.trim().length() > 255
                || code == null || code.isBlank() || code.trim().length() > 50
                || !code.trim().matches("[A-Za-z0-9_-]+")) {
            redirectAttributes.addFlashAttribute("error", "Enter a department name and a valid code (letters, numbers, hyphens, or underscores).");
            return "redirect:/admin/departments";
        }
        Department department = id == null ? new Department() : departmentRepository.findById(id).orElse(null);
        if (department == null) {
            redirectAttributes.addFlashAttribute("error", "Department not found.");
            return "redirect:/admin/departments";
        }
        String normalizedName = name.trim();
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        boolean duplicateName = departmentRepository.existsByNameIgnoreCase(normalizedName);
        boolean duplicateCode = departmentRepository.existsByCodeIgnoreCase(normalizedCode);
        if ((duplicateName && (id == null || !normalizedName.equalsIgnoreCase(department.getName())))
                || (duplicateCode && (id == null || department.getCode() == null
                || !normalizedCode.equalsIgnoreCase(department.getCode())))) {
            redirectAttributes.addFlashAttribute("error", "A department with that name or code already exists.");
            return "redirect:/admin/departments";
        }
        department.setName(normalizedName);
        department.setCode(normalizedCode);
        try {
            departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "Department could not be saved because its name is already in use.");
            return "redirect:/admin/departments";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "Department could not be saved because of a database error. Please try again.");
            return "redirect:/admin/departments";
        }
        redirectAttributes.addFlashAttribute("success", id == null
                ? "Department added successfully."
                : "Department updated successfully.");
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/delete")
    public String deleteDepartment(@RequestParam(required = false) Long id, RedirectAttributes redirectAttributes) {
        if (id == null || !departmentRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Department not found.");
            return "redirect:/admin/departments";
        }
        try {
            departmentRepository.deleteById(id);
            departmentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "This department is assigned to users or other records and cannot be deleted.");
            return "redirect:/admin/departments";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "Department could not be deleted because of a database error. Please try again.");
            return "redirect:/admin/departments";
        }
        redirectAttributes.addFlashAttribute("success", "Department deleted successfully.");
        return "redirect:/admin/departments";
    }
    @GetMapping("/complaints")
    public String complaints(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("complaintStatuses", com.campusflow.entity.ComplaintStatus.values());
        model.addAttribute("complaints", complaintRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        com.campusflow.entity.Complaint::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(complaint -> new ComplaintSummary(
                        complaint.getId(),
                        complaint.getTitle(),
                        complaint.getDescription(),
                        complaint.getStatus() == null ? "UNKNOWN" : complaint.getStatus().name(),
                        complaint.getCreatedAt(),
                        complaint.getUpdatedAt()))
                .toList());
        return "admin/complaints";
    }

    @PostMapping("/complaints/{id}/status")
    public String updateComplaintStatus(
            @PathVariable Long id,
            @RequestParam String status,
            RedirectAttributes redirectAttributes) {
        Complaint complaint = complaintRepository.findById(id).orElse(null);
        if (complaint == null) {
            redirectAttributes.addFlashAttribute("error", "Complaint not found.");
            return "redirect:/admin/complaints";
        }

        ComplaintStatus updatedStatus;
        try {
            updatedStatus = ComplaintStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException ex) {
            redirectAttributes.addFlashAttribute("error", "Select a valid complaint status.");
            return "redirect:/admin/complaints";
        }

        complaint.setStatus(updatedStatus);
        complaint.setUpdatedAt(LocalDateTime.now());
        complaintRepository.save(complaint);
        redirectAttributes.addFlashAttribute("success", "Complaint status updated.");
        return "redirect:/admin/complaints";
    }

    @GetMapping("/notices")
    public String notices(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("notices", noticeRepository.findAll());
        return "admin/notices";
    }

    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        return "admin/settings";
    }

    @GetMapping("/events")
    public String events(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("events", eventRepository.findAll());
        return "admin/events";
    }

    @PostMapping("/events/save")
    public String saveEvent(
            Authentication authentication,
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam String eventDate,
            @RequestParam String location,
            RedirectAttributes redirectAttributes) {
        Event event = id == null
                ? new Event()
                : eventRepository.findById(id).orElse(null);
        if (event == null) {
            redirectAttributes.addFlashAttribute("error", "Event not found.");
            return "redirect:/admin/events";
        }
        if (id == null) {
            event.setOrganizer(userRepository.findByEmail(authentication.getName()).orElseThrow());
        }
        try {
            event.setEventDate(LocalDateTime.parse(eventDate));
        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("error", "Enter a valid event date and time.");
            return "redirect:/admin/events";
        }
        event.setName(name.trim());
        event.setDescription(description == null ? "" : description.trim());
        event.setLocation(location.trim());
        eventRepository.save(event);
        redirectAttributes.addFlashAttribute("success", id == null
                ? "Event created successfully!"
                : "Event updated successfully!");
        return "redirect:/admin/events";
    }

    @PostMapping("/events/delete")
    public String deleteEvent(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        if (!eventRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Event not found.");
            return "redirect:/admin/events";
        }
        eventRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Event deleted successfully!");
        return "redirect:/admin/events";
    }

    @GetMapping("/attendance")
    public String attendance(
            Authentication authentication,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer semester,
            Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<Attendance> allAttendanceList = attendanceRepository.findAllByOrderByDateDesc();
        List<Attendance> attendanceList = allAttendanceList.stream()
                .filter(record -> subjectId == null || subjectId.equals(record.getSubject().getId()))
                .filter(record -> studentId == null || studentId.equals(record.getStudent().getId()))
                .filter(record -> departmentId == null || record.getStudent().getDepartment() != null
                        && departmentId.equals(record.getStudent().getDepartment().getId()))
                .filter(record -> semester == null || semester.equals(record.getStudent().getCurrentSemester()))
                .toList();
        List<User> students = userRepository.findByRole(Role.STUDENT).stream()
                .filter(student -> departmentId == null || student.getDepartment() != null
                        && departmentId.equals(student.getDepartment().getId()))
                .filter(student -> semester == null || semester.equals(student.getCurrentSemester()))
                .toList();
        List<Subject> subjects = subjectRepository.findAll().stream()
                .filter(subject -> departmentId == null || subject.getDepartment() != null
                        && departmentId.equals(subject.getDepartment().getId()))
                .filter(subject -> semester == null || semester.equals(subject.getSemester()))
                .toList();
        long presentCount = allAttendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count();
        long absentCount = allAttendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.ABSENT).count();
        long lateCount = allAttendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.LATE).count();
        long excusedCount = allAttendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.EXCUSED).count();
        double attendancePercentage = allAttendanceList.isEmpty() ? 0
                : presentCount * 100.0 / allAttendanceList.size();
        long filteredPresentCount = attendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count();
        long filteredAbsentCount = attendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.ABSENT).count();
        long filteredLateCount = attendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.LATE).count();
        long filteredExcusedCount = attendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.EXCUSED).count();
        Map<Long, Double> studentAttendancePercentages = attendanceList.stream()
                .collect(Collectors.groupingBy(record -> record.getStudent().getId()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().isEmpty() ? 0.0
                                : entry.getValue().stream()
                                .filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count()
                                * 100.0 / entry.getValue().size()));
        double filteredAttendancePercentage = attendanceList.isEmpty() ? 0
                : filteredPresentCount * 100.0 / attendanceList.size();

        model.addAttribute("user", user);
        model.addAttribute("attendanceList", attendanceList);
        model.addAttribute("students", students);
        model.addAttribute("subjects", subjects);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("semesters", java.util.stream.IntStream.rangeClosed(1, 12).boxed().toList());
        model.addAttribute("totalAttendanceCount", allAttendanceList.size());
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("absentCount", absentCount);
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("excusedCount", excusedCount);
        model.addAttribute("attendancePercentage", attendancePercentage);
        model.addAttribute("filteredAttendanceCount", attendanceList.size());
        model.addAttribute("filteredPresentCount", filteredPresentCount);
        model.addAttribute("filteredAbsentCount", filteredAbsentCount);
        model.addAttribute("filteredLateCount", filteredLateCount);
        model.addAttribute("filteredExcusedCount", filteredExcusedCount);
        model.addAttribute("filteredAttendancePercentage", filteredAttendancePercentage);
        model.addAttribute("studentAttendancePercentages", studentAttendancePercentages);
        model.addAttribute("selectedSubjectId", subjectId);
        model.addAttribute("selectedStudentId", studentId);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute("selectedSemester", semester);
        return "admin/attendance";
    }

    @GetMapping("/assignments")
    public String assignments(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<Assignment> assignmentList = assignmentRepository.findAllByOrderByDueDateDesc();
        List<Subject> subjects = subjectRepository.findAll();
        List<User> facultyList = userRepository.findByRole(com.campusflow.entity.Role.FACULTY);

        model.addAttribute("user", user);
        model.addAttribute("assignmentList", assignmentList);
        model.addAttribute("subjects", subjects);
        model.addAttribute("facultyList", facultyList);
        return "admin/assignments";
    }

    @PostMapping("/assignments/save")
    public String saveAssignment(
            @RequestParam(required = false) Long id,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String dueDate,
            @RequestParam Long subjectId,
            @RequestParam Long facultyId,
            RedirectAttributes redirectAttributes) {

        if (title == null || title.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Assignment title is required.");
            return "redirect:/admin/assignments";
        }

        if (description == null || description.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Assignment description is required.");
            return "redirect:/admin/assignments";
        }

        if (subjectId == null || facultyId == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a valid subject and faculty member.");
            return "redirect:/admin/assignments";
        }

        if (dueDate == null || dueDate.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Due date is required.");
            return "redirect:/admin/assignments";
        }

        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        User faculty = userRepository.findById(facultyId).orElse(null);

        if (subject == null || faculty == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a valid subject and faculty member.");
            return "redirect:/admin/assignments";
        }

        LocalDateTime parsedDueDate;
        try {
            parsedDueDate = LocalDateTime.parse(dueDate);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid date and time for the due date.");
            return "redirect:/admin/assignments";
        }

        Assignment assignment = id != null ? assignmentRepository.findById(id).orElse(null) : new Assignment();
        if (assignment == null) {
            assignment = new Assignment();
        }

        assignment.setTitle(title.trim());
        assignment.setDescription(description.trim());
        assignment.setDueDate(parsedDueDate);
        assignment.setSubject(subject);
        assignment.setFaculty(faculty);
        assignmentRepository.save(assignment);

        redirectAttributes.addFlashAttribute("success", id == null ? "Assignment created successfully." : "Assignment updated successfully.");
        return "redirect:/admin/assignments";
    }

    @PostMapping("/assignments/delete")
    public String deleteAssignment(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        if (id == null) {
            redirectAttributes.addFlashAttribute("error", "Assignment not found.");
            return "redirect:/admin/assignments";
        }

        if (!assignmentRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Assignment not found.");
            return "redirect:/admin/assignments";
        }

        assignmentRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Assignment deleted successfully.");
        return "redirect:/admin/assignments";
    }

    @GetMapping("/documents")
    public String documents(
            Authentication authentication,
            @RequestParam(required = false) Long subjectId,
            Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<StudyMaterial> materialList = subjectId == null
                ? studyMaterialRepository.findAllByOrderByUploadDateDesc()
                : studyMaterialRepository.findBySubjectIdOrderByUploadDateDesc(subjectId);

        model.addAttribute("user", user);
        model.addAttribute("materialList", materialList);
        model.addAttribute("subjects", subjectRepository.findAll());
        model.addAttribute("facultyList", userRepository.findByRole(Role.FACULTY));
        model.addAttribute("selectedSubjectId", subjectId);
        return "admin/documents";
    }

    @PostMapping("/documents/save")
    public String saveStudyMaterial(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) MultipartFile file,
            RedirectAttributes redirectAttributes) {
        if (title == null || title.isBlank() || title.trim().length() > 255) {
            redirectAttributes.addFlashAttribute("error", "Enter a title no longer than 255 characters.");
            return "redirect:/admin/documents";
        }
        if (subjectId == null || facultyId == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a subject and faculty member.");
            return "redirect:/admin/documents";
        }

        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        User faculty = userRepository.findById(facultyId).orElse(null);
        if (subject == null || faculty == null || faculty.getRole() != Role.FACULTY) {
            redirectAttributes.addFlashAttribute("error", "Please select a valid subject and faculty member.");
            return "redirect:/admin/documents";
        }

        StudyMaterial material = id == null
                ? new StudyMaterial()
                : studyMaterialRepository.findById(id).orElse(null);
        if (material == null) {
            redirectAttributes.addFlashAttribute("error", "Study material not found.");
            return "redirect:/admin/documents";
        }
        if ((file == null || file.isEmpty()) && material.getFileUrl() == null) {
            redirectAttributes.addFlashAttribute("error", "Choose a file to upload.");
            return "redirect:/admin/documents";
        }
        if (file != null && !file.isEmpty() && file.getSize() > 10L * 1024 * 1024) {
            redirectAttributes.addFlashAttribute("error", "The uploaded file must be 10 MB or smaller.");
            return "redirect:/admin/documents";
        }

        String previousFileUrl = material.getFileUrl();
        String newFileUrl = null;
        try {
            if (file != null && !file.isEmpty()) {
                newFileUrl = storeStudyMaterialFile(file);
                material.setFileUrl(newFileUrl);
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/documents";
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", "The file could not be saved. Check the upload directory and try again.");
            return "redirect:/admin/documents";
        }

        material.setTitle(title.trim());
        material.setDescription(description == null || description.isBlank() ? null : description.trim());
        material.setSubject(subject);
        material.setFaculty(faculty);
        if (material.getUploadDate() == null) {
            material.setUploadDate(LocalDateTime.now());
        }

        try {
            studyMaterialRepository.save(material);
        } catch (DataAccessException ex) {
            if (newFileUrl != null) {
                try {
                    Files.deleteIfExists(resolveStudyMaterialFile(newFileUrl));
                } catch (IOException cleanupException) {
                    ex.addSuppressed(cleanupException);
                }
            }
            redirectAttributes.addFlashAttribute("error", "Study material could not be saved. Please try again.");
            return "redirect:/admin/documents";
        }

        if (newFileUrl != null && previousFileUrl != null) {
            try {
                Files.deleteIfExists(resolveStudyMaterialFile(previousFileUrl));
            } catch (IOException | IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("error", "Material saved, but the previous uploaded file could not be removed.");
            }
        }
        redirectAttributes.addFlashAttribute("success", id == null
                ? "Study material added successfully."
                : "Study material updated successfully.");
        return "redirect:/admin/documents";
    }

    @PostMapping("/documents/delete")
    public String deleteStudyMaterial(@RequestParam(required = false) Long id, RedirectAttributes redirectAttributes) {
        StudyMaterial material = id == null ? null : studyMaterialRepository.findById(id).orElse(null);
        if (material == null) {
            redirectAttributes.addFlashAttribute("error", "Study material not found.");
            return "redirect:/admin/documents";
        }

        String fileUrl = material.getFileUrl();
        studyMaterialRepository.delete(material);
        redirectAttributes.addFlashAttribute("success", "Study material deleted successfully.");
        if (fileUrl != null) {
            try {
                Files.deleteIfExists(resolveStudyMaterialFile(fileUrl));
            } catch (IOException | IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("error", "Material record deleted, but the uploaded file could not be removed.");
            }
        }
        return "redirect:/admin/documents";
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> downloadStudyMaterial(@PathVariable Long id) {
        StudyMaterial material = studyMaterialRepository.findById(id).orElse(null);
        if (material == null || material.getFileUrl() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = resolveStudyMaterialFile(material.getFileUrl());
            FileSystemResource resource = new FileSystemResource(filePath);
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            String fileName = filePath.getFileName().toString();
            int separator = fileName.indexOf('_');
            if (separator >= 0 && separator < fileName.length() - 1) {
                fileName = fileName.substring(separator + 1);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(fileName).build().toString())
                    .body(resource);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleStudyMaterialUploadTooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "The uploaded file must be 10 MB or smaller.");
        return "redirect:/admin/documents";
    }

    private String storeStudyMaterialFile(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("The uploaded file must have a valid filename.");
        }
        String safeOriginalName = originalName.replace('\\', '/');
        safeOriginalName = safeOriginalName.substring(safeOriginalName.lastIndexOf('/') + 1);
        String extension = safeOriginalName.contains(".")
                ? safeOriginalName.substring(safeOriginalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!Set.of("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv").contains(extension)) {
            throw new IllegalArgumentException("Upload a PDF, Office document, TXT, or CSV file.");
        }

        String safeName = safeOriginalName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 120) {
            safeName = safeName.substring(safeName.length() - 120);
        }
        String storedName = UUID.randomUUID() + "_" + safeName;
        Path directory = studyMaterialDirectory();
        Files.createDirectories(directory);
        Path destination = directory.resolve(storedName).normalize();
        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid uploaded filename.");
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination);
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                ex.addSuppressed(cleanupException);
            }
            throw ex;
        }
        return "study-materials/" + storedName;
    }

    private Path studyMaterialDirectory() {
        return Path.of(uploadDirectory).toAbsolutePath().normalize().resolve("study-materials").normalize();
    }

    private Path resolveStudyMaterialFile(String storedPath) {
        Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path filePath = root.resolve(storedPath).normalize();
        if (!storedPath.startsWith("study-materials/") || !filePath.startsWith(studyMaterialDirectory())) {
            throw new IllegalArgumentException("Invalid stored study material path.");
        }
        return filePath;
    }

    @GetMapping("/fees")
    public String fees(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String academicYear,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            Model model,
            RedirectAttributes redirectAttributes) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        FeePaymentStatus paymentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                paymentStatus = FeePaymentStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("error", "Choose a valid payment status.");
            }
        }

        List<Fee> feeList = feeRepository.searchFees(
                studentId,
                departmentId,
                paymentStatus,
                academicYear == null || academicYear.isBlank() ? null : academicYear.trim(),
                search == null || search.isBlank() ? null : search.trim(),
                overdueOnly,
                LocalDate.now());
        model.addAttribute("user", user);
        model.addAttribute("feeList", feeList);

List<Department> departments = departmentRepository.findAll();
model.addAttribute("feeGroups", groupFeesByDepartment(feeList, departments));
model.addAttribute("departments", departments);
        model.addAttribute("students", userRepository.findByRole(Role.STUDENT));
        model.addAttribute("paymentStatuses", FeePaymentStatus.values());
        model.addAttribute("selectedStudentId", studentId);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute("selectedStatus", paymentStatus == null ? "" : paymentStatus.name());
        model.addAttribute("selectedAcademicYear", academicYear);
        model.addAttribute("search", search);
        model.addAttribute("overdueOnly", overdueOnly);
        model.addAttribute("totalFees", feeRepository.sumTotalAmount());
        model.addAttribute("collectedFees", feeRepository.sumPaidAmount());
        model.addAttribute("pendingFees", feeRepository.sumOutstandingAmount());
        model.addAttribute("overdueFees", feeRepository.sumOverdueAmount(LocalDate.now()));
        return "admin/fees";
    }

    private List<DepartmentFees> groupFeesByDepartment(
        List<Fee> fees,
        List<Department> departments) {
        Map<Long, List<Fee>> byDepartment = new LinkedHashMap<>();
        for (Fee fee : fees) {
            Long departmentId = fee.getStudent().getDepartment() == null
                    ? null
                    : fee.getStudent().getDepartment().getId();
            byDepartment.computeIfAbsent(departmentId, ignored -> new ArrayList<>()).add(fee);
        }
         Map<Long, Department> departmentMap = departments.stream()
        .collect(Collectors.toMap(Department::getId, department -> department));

List<DepartmentFees> groups = new ArrayList<>();
byDepartment.forEach((departmentId, departmentFees) -> {
    Department department = departmentId == null ? null : departmentMap.get(departmentId);
                groups.add(new DepartmentFees(
                    department == null ? "Unassigned" : department.getName(),
                    departmentId,
                    departmentFees));
        });
        return groups;
    }

    @PostMapping("/fees/save")
    public String saveFee(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String totalAmount,
            @RequestParam(required = false) String paidAmount,
            @RequestParam(required = false) String dueDate,
            @RequestParam(required = false) String paymentDate,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester,
            RedirectAttributes redirectAttributes) {
        User student = studentId == null ? null : userRepository.findById(studentId).orElse(null);
        if (student == null || student.getRole() != Role.STUDENT) {
            redirectAttributes.addFlashAttribute("error", "Choose a valid student.");
            return "redirect:/admin/fees";
        }

        BigDecimal parsedTotal;
        BigDecimal parsedPaid;
        try {
            parsedTotal = parseMoney(totalAmount, "Total fee");
            parsedPaid = parseMoney(paidAmount, "Paid amount");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/fees";
        }
        if (parsedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "Total fee must be greater than zero.");
            return "redirect:/admin/fees";
        }
        if (parsedPaid.compareTo(BigDecimal.ZERO) < 0 || parsedPaid.compareTo(parsedTotal) > 0) {
            redirectAttributes.addFlashAttribute("error", "Paid amount must be zero or more and cannot exceed the total fee.");
            return "redirect:/admin/fees";
        }

        LocalDate parsedDueDate;
        LocalDate parsedPaymentDate = null;
        try {
            parsedDueDate = LocalDate.parse(dueDate);
            if (paymentDate != null && !paymentDate.isBlank()) {
                parsedPaymentDate = LocalDate.parse(paymentDate);
            }
        } catch (DateTimeParseException | NullPointerException ex) {
            redirectAttributes.addFlashAttribute("error", "Enter a valid due date and payment date.");
            return "redirect:/admin/fees";
        }
        if (parsedPaid.compareTo(BigDecimal.ZERO) > 0 && parsedPaymentDate == null) {
            parsedPaymentDate = LocalDate.now();
        }

        Integer parsedSemester;
        if (academicYear == null || !academicYear.trim().matches("\\d{4}-\\d{4}")) {
            redirectAttributes.addFlashAttribute("error", "Enter the academic year in YYYY-YYYY format.");
            return "redirect:/admin/fees";
        }
        try {
            parsedSemester = Integer.valueOf(semester);
        } catch (NumberFormatException | NullPointerException ex) {
            redirectAttributes.addFlashAttribute("error", "Enter a valid semester from 1 to 12.");
            return "redirect:/admin/fees";
        }
        if (parsedSemester < 1 || parsedSemester > 12) {
            redirectAttributes.addFlashAttribute("error", "Semester must be from 1 to 12.");
            return "redirect:/admin/fees";
        }
        String[] academicYears = academicYear.trim().split("-");
        if (Integer.parseInt(academicYears[1]) != Integer.parseInt(academicYears[0]) + 1) {
            redirectAttributes.addFlashAttribute("error", "Academic year must contain consecutive years.");
            return "redirect:/admin/fees";
        }

        Fee fee = id == null ? new Fee() : feeRepository.findById(id).orElse(null);
        if (fee == null) {
            redirectAttributes.addFlashAttribute("error", "Fee record not found.");
            return "redirect:/admin/fees";
        }
        fee.setStudent(student);
        fee.setTotalAmount(parsedTotal);
        fee.setPaidAmount(parsedPaid);
        fee.setPaymentStatus(resolvePaymentStatus(parsedTotal, parsedPaid));
        fee.setDueDate(parsedDueDate);
        fee.setPaymentDate(parsedPaymentDate);
        fee.setAcademicYear(academicYear.trim());
        fee.setSemester(parsedSemester);
        feeRepository.save(fee);

        redirectAttributes.addFlashAttribute("success", id == null
                ? "Fee record created successfully."
                : "Fee record updated successfully.");
        return "redirect:/admin/fees";
    }

    @PostMapping("/fees/payment")
    public String recordFeePayment(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String paymentAmount,
            @RequestParam(required = false) String paymentDate,
            RedirectAttributes redirectAttributes) {
        Fee fee = id == null ? null : feeRepository.findById(id).orElse(null);
        if (fee == null) {
            redirectAttributes.addFlashAttribute("error", "Fee record not found.");
            return "redirect:/admin/fees";
        }

        BigDecimal amount;
        try {
            amount = parseMoney(paymentAmount, "Payment amount");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/fees";
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "Payment amount must be greater than zero.");
            return "redirect:/admin/fees";
        }
        if (amount.compareTo(fee.getOutstandingAmount()) > 0) {
            redirectAttributes.addFlashAttribute("error", "Payment cannot exceed the outstanding amount.");
            return "redirect:/admin/fees";
        }

        LocalDate parsedPaymentDate;
        try {
            parsedPaymentDate = paymentDate == null || paymentDate.isBlank()
                    ? LocalDate.now()
                    : LocalDate.parse(paymentDate);
        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("error", "Enter a valid payment date.");
            return "redirect:/admin/fees";
        }
        fee.setPaidAmount(fee.getPaidAmount().add(amount).setScale(2, RoundingMode.UNNECESSARY));
        fee.setPaymentDate(parsedPaymentDate);
        fee.setPaymentStatus(resolvePaymentStatus(fee.getTotalAmount(), fee.getPaidAmount()));
        feeRepository.save(fee);
        redirectAttributes.addFlashAttribute("success", "Payment recorded successfully.");
        return "redirect:/admin/fees";
    }

    @PostMapping("/fees/delete")
    public String deleteFee(@RequestParam(required = false) Long id, RedirectAttributes redirectAttributes) {
        if (id == null || !feeRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Fee record not found.");
            return "redirect:/admin/fees";
        }
        feeRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Fee record deleted successfully.");
        return "redirect:/admin/fees";
    }

    private List<DepartmentStudents> groupStudentsByDepartment(List<User> students) {
        List<DepartmentStudents> groups = departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Department::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(department -> new DepartmentStudents(
                        department.getName(),
                        department.getId(),
                        students.stream()
                                .filter(student -> student.getDepartment() != null
                                        && department.getId().equals(student.getDepartment().getId()))
                                .sorted(Comparator.comparing(
                                        student -> (student.getFirstName() + " " + student.getLastName()).toLowerCase(Locale.ROOT)))
                                .toList()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<User> unassigned = students.stream()
                .filter(student -> student.getDepartment() == null)
                .sorted(Comparator.comparing(
                        student -> (student.getFirstName() + " " + student.getLastName()).toLowerCase(Locale.ROOT)))
                .toList();
        if (!unassigned.isEmpty()) {
            groups.add(new DepartmentStudents("Unassigned", null, unassigned));
        }
        return groups;
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.precision() > 12) {
                throw new IllegalArgumentException(fieldName + " is too large.");
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid amount with up to two decimal places.");
        }
    }

    private FeePaymentStatus resolvePaymentStatus(BigDecimal total, BigDecimal paid) {
        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            return FeePaymentStatus.PENDING;
        }
        if (paid.compareTo(total) >= 0) {
            return FeePaymentStatus.PAID;
        }
        return FeePaymentStatus.PARTIAL;
    }
}
