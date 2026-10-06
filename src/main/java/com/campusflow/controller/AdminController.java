package com.campusflow.controller;

import com.campusflow.entity.Assignment;
import com.campusflow.entity.Attendance;
import com.campusflow.entity.AttendanceStatus;
import com.campusflow.entity.Event;
import com.campusflow.entity.Fee;
import com.campusflow.entity.FeePaymentStatus;
import com.campusflow.entity.Role;
import com.campusflow.entity.StudyMaterial;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.AssignmentRepository;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.ComplaintRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.EventRepository;
import com.campusflow.repository.FeeRepository;
import com.campusflow.repository.NoticeRepository;
import com.campusflow.repository.StudyMaterialRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
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
import java.util.List;

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
    public String students(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("students", userRepository.findByRole(com.campusflow.entity.Role.STUDENT));
        return "admin/students";
    }

    @GetMapping("/faculty")
    public String faculty(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("facultyList", userRepository.findByRole(com.campusflow.entity.Role.FACULTY));
        return "admin/faculty";
    }

    @GetMapping("/departments")
    public String departments(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("departments", departmentRepository.findAll());
        return "admin/departments";
    }
    @GetMapping("/complaints")
    public String complaints(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("complaints", complaintRepository.findAll());
        return "admin/complaints";
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
    public String attendance(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<Attendance> attendanceList = attendanceRepository.findAllByOrderByDateDesc();
        List<User> students = userRepository.findByRole(com.campusflow.entity.Role.STUDENT);
        List<Subject> subjects = subjectRepository.findAll();
        long presentCount = attendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count();
        long absentCount = attendanceList.stream().filter(record -> record.getStatus() == AttendanceStatus.ABSENT).count();

        model.addAttribute("user", user);
        model.addAttribute("attendanceList", attendanceList);
        model.addAttribute("students", students);
        model.addAttribute("subjects", subjects);
        model.addAttribute("statuses", AttendanceStatus.values());
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("absentCount", absentCount);
        return "admin/attendance";
    }

    @PostMapping("/attendance/save")
    public String saveAttendance(
            Authentication authentication,
            @RequestParam(required = false) Long id,
            @RequestParam Long studentId,
            @RequestParam Long subjectId,
            @RequestParam LocalDate date,
            @RequestParam AttendanceStatus status,
            RedirectAttributes redirectAttributes) {

        if (studentId == null || subjectId == null || date == null || status == null) {
            redirectAttributes.addFlashAttribute("error", "Student, subject, date, and status are required.");
            return "redirect:/admin/attendance";
        }

        User student = userRepository.findById(studentId).orElse(null);
        Subject subject = subjectRepository.findById(subjectId).orElse(null);

        if (student == null || subject == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a valid student and subject.");
            return "redirect:/admin/attendance";
        }

        if (attendanceRepository.findByStudentIdAndSubjectIdAndDate(studentId, subjectId, date).filter(existing -> id == null || !existing.getId().equals(id)).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Attendance record already exists for this student, subject, and date.");
            return "redirect:/admin/attendance";
        }

        Attendance attendance = id != null ? attendanceRepository.findById(id).orElse(null) : new Attendance();
        if (attendance == null) {
            attendance = new Attendance();
        }

        attendance.setStudent(student);
        attendance.setSubject(subject);
        attendance.setDate(date);
        attendance.setStatus(status);
        attendanceRepository.save(attendance);

        redirectAttributes.addFlashAttribute("success", id == null ? "Attendance record created successfully." : "Attendance record updated successfully.");
        return "redirect:/admin/attendance";
    }

    @PostMapping("/attendance/delete")
    public String deleteAttendance(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        if (id == null) {
            redirectAttributes.addFlashAttribute("error", "Attendance record not found.");
            return "redirect:/admin/attendance";
        }

        if (!attendanceRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Attendance record not found.");
            return "redirect:/admin/attendance";
        }

        attendanceRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Attendance record deleted successfully.");
        return "redirect:/admin/attendance";
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
                paymentStatus,
                academicYear == null || academicYear.isBlank() ? null : academicYear.trim(),
                search == null || search.isBlank() ? null : search.trim(),
                overdueOnly,
                LocalDate.now());
        model.addAttribute("user", user);
        model.addAttribute("feeList", feeList);
        model.addAttribute("students", userRepository.findByRole(Role.STUDENT));
        model.addAttribute("paymentStatuses", FeePaymentStatus.values());
        model.addAttribute("selectedStudentId", studentId);
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
