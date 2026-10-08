package com.campusflow.controller;

import com.campusflow.entity.Assignment;
import com.campusflow.entity.Attendance;
import com.campusflow.entity.AttendanceStatus;
import com.campusflow.entity.Complaint;
import com.campusflow.entity.ComplaintStatus;
import com.campusflow.entity.Event;
import com.campusflow.entity.Notice;
import com.campusflow.entity.Role;
import com.campusflow.entity.StudyMaterial;
import com.campusflow.entity.Submission;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.AssignmentRepository;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.ComplaintRepository;
import com.campusflow.repository.EventRepository;
import com.campusflow.repository.FeeRepository;
import com.campusflow.repository.NoticeRepository;
import com.campusflow.repository.StudyMaterialRepository;
import com.campusflow.repository.SubmissionRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
@Slf4j
public class StudentController {

    private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv");

    private final UserRepository userRepository;
    private final NoticeRepository noticeRepository;
    private final SubjectRepository subjectRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final SubmissionRepository submissionRepository;
    private final EventRepository eventRepository;
    private final ComplaintRepository complaintRepository;
    private final FeeRepository feeRepository;

    @Value("${campusflow.upload.directory:uploads}")
    private String uploadDirectory;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "dashboard");
        List<Subject> subjects = relevantSubjects(student);
        List<Attendance> attendance = attendanceRepository.findByStudentId(student.getId());
        List<Submission> submissions = submissionRepository.findByStudentId(student.getId());
        List<AssignmentView> assignments = assignmentViews(subjects, submissions);
        List<NoticeView> notices = noticeViews(student);
        List<EventView> events = eventViews();
        List<FeeView> fees = feeViews(student);
        long present = attendance.stream().filter(item -> item.getStatus() == AttendanceStatus.PRESENT).count();
        long marked = submissions.stream().filter(item -> item.getMarksAwarded() != null).count();
        double averageMarks = submissions.stream()
                .map(Submission::getMarksAwarded)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.NaN);
        long pendingAssignments = assignments.stream()
                .filter(item -> item.submission() == null)
                .filter(item -> item.dueDate() == null || !item.dueDate().isBefore(LocalDateTime.now()))
                .count();
        BigDecimal totalFees = fees.stream().map(FeeView::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidFees = fees.stream().map(FeeView::paidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("attendanceCount", attendance.size());
        model.addAttribute("attendancePercentage", percentage(present, attendance.size()));
        model.addAttribute("markedSubmissionCount", marked);
        model.addAttribute("averageMarks", Double.isNaN(averageMarks) ? null : averageMarks);
        model.addAttribute("pendingAssignmentCount", pendingAssignments);
        model.addAttribute("upcomingEvents", events.stream().filter(item -> !item.past()).limit(3).toList());
        model.addAttribute("recentNotices", notices.stream().limit(4).toList());
        model.addAttribute("totalFees", totalFees);
        model.addAttribute("paidFees", paidFees);
        model.addAttribute("pendingFees", totalFees.subtract(paidFees).max(BigDecimal.ZERO));
        return "student/dashboard";
    }

    @GetMapping("/academics")
    public String academics(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "academics");
        List<Subject> subjects = relevantSubjects(student);
        List<Attendance> attendance = attendanceRepository.findByStudentId(student.getId());
        List<Submission> submissions = submissionRepository.findByStudentId(student.getId());
        Map<Long, List<Attendance>> attendanceBySubject = attendance.stream()
                .filter(item -> item.getSubject() != null)
                .collect(Collectors.groupingBy(item -> item.getSubject().getId()));
        Map<Long, List<Submission>> submissionsBySubject = submissions.stream()
                .filter(item -> item.getAssignment() != null && item.getAssignment().getSubject() != null)
                .collect(Collectors.groupingBy(item -> item.getAssignment().getSubject().getId()));

        List<AcademicSubjectView> performance = subjects.stream()
                .map(subject -> academicSubjectView(
                        subject,
                        attendanceBySubject.getOrDefault(subject.getId(), List.of()),
                        submissionsBySubject.getOrDefault(subject.getId(), List.of())))
                .toList();
        List<Integer> marks = submissions.stream().map(Submission::getMarksAwarded)
                .filter(java.util.Objects::nonNull).toList();
        model.addAttribute("performance", performance);
        model.addAttribute("overallAverage", marks.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN));
        model.addAttribute("gradedCount", marks.size());
        return "student/dashboard";
    }

    @GetMapping("/attendance")
    public String attendance(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "attendance");
        List<Attendance> records = attendanceRepository.findByStudentId(student.getId());
        Map<Long, List<Attendance>> attendanceBySubject = records.stream()
                .filter(item -> item.getSubject() != null)
                .collect(Collectors.groupingBy(item -> item.getSubject().getId()));
        List<AttendanceSubjectView> attendanceBySubjectViews = attendanceBySubject.values().stream()
                .map(items -> {
                    Attendance first = items.get(0);
                    long present = items.stream().filter(item -> item.getStatus() == AttendanceStatus.PRESENT).count();
                    return new AttendanceSubjectView(
                            first.getSubject().getName(),
                            first.getSubject().getCode(),
                            items.stream().map(Attendance::getDate).max(Comparator.naturalOrder()).orElse(null),
                            items.size(),
                            present,
                            percentage(present, items.size()),
                            items.stream().map(item -> new AttendanceRecordView(item.getDate(), item.getStatus()))
                                    .sorted(Comparator.comparing(AttendanceRecordView::date,
                                            Comparator.nullsLast(Comparator.reverseOrder())))
                                    .toList());
                })
                .sorted(Comparator.comparing(AttendanceSubjectView::subjectName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        long present = records.stream().filter(item -> item.getStatus() == AttendanceStatus.PRESENT).count();
        model.addAttribute("attendanceBySubject", attendanceBySubjectViews);
        model.addAttribute("attendanceCount", records.size());
        model.addAttribute("attendancePercentage", percentage(present, records.size()));
        return "student/dashboard";
    }

    @GetMapping("/assignments")
    public String assignments(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "assignments");
        model.addAttribute("assignments", assignmentViews(
                relevantSubjects(student), submissionRepository.findByStudentId(student.getId())));
        return "student/dashboard";
    }

    @PostMapping("/assignments/{assignmentId}/submit")
    public String submitAssignment(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String studentComments,
            RedirectAttributes redirectAttributes) {
        User student = authenticatedStudent(authentication);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .filter(item -> relevantSubjectIds(student).contains(item.getSubject().getId()))
                .orElse(null);
        if (assignment == null) {
            redirectAttributes.addFlashAttribute("error", "That assignment is not available to your account.");
            return "redirect:/student/assignments";
        }
        if (assignment.getDueDate() != null && assignment.getDueDate().isBefore(LocalDateTime.now())) {
            redirectAttributes.addFlashAttribute("error", "The submission deadline has passed.");
            return "redirect:/student/assignments";
        }
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasFile && !StringUtils.hasText(studentComments)) {
            redirectAttributes.addFlashAttribute("error", "Attach a file or enter submission comments.");
            return "redirect:/student/assignments";
        }

        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())
                .orElseGet(Submission::new);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setSubmissionDate(LocalDateTime.now());
        submission.setStudentComments(StringUtils.hasText(studentComments) ? studentComments.trim() : null);
        String previousFileUrl = submission.getFileUrl();
        String storedFileUrl = null;
        try {
            if (hasFile) {
                storedFileUrl = storeSubmissionFile(file);
                submission.setFileUrl(storedFileUrl);
            }
            submissionRepository.saveAndFlush(submission);
        } catch (IOException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/student/assignments";
        } catch (DataAccessException ex) {
            log.error("Could not save student assignment submission for student {}", student.getId(), ex);
            deleteStoredSubmissionFile(storedFileUrl);
            redirectAttributes.addFlashAttribute("error", "The submission could not be saved. Please try again.");
            return "redirect:/student/assignments";
        }
        if (storedFileUrl != null && previousFileUrl != null) {
            deleteStoredSubmissionFile(previousFileUrl);
        }
        redirectAttributes.addFlashAttribute("success", "Assignment submission saved.");
        return "redirect:/student/assignments";
    }

    @GetMapping("/submissions/{submissionId}/download")
    public ResponseEntity<Resource> downloadSubmission(Authentication authentication, @PathVariable Long submissionId) {
        User student = authenticatedStudent(authentication);
        Submission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getStudent().getId().equals(student.getId()))
                .orElse(null);
        if (submission == null || submission.getFileUrl() == null) {
            return ResponseEntity.notFound().build();
        }
        return downloadStoredFile(submission.getFileUrl(), "submissions/");
    }

    @GetMapping("/materials")
    public String materials(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "materials");
        List<Long> subjectIds = relevantSubjectIds(student);
        List<StudyMaterialView> materials = subjectIds.isEmpty()
                ? List.of()
                : studyMaterialRepository.findBySubjectIdInOrderByUploadDateDesc(subjectIds).stream()
                        .map(item -> new StudyMaterialView(
                                item.getId(), item.getTitle(), item.getDescription(), item.getFileUrl(),
                                item.getSubject().getName(), item.getSubject().getCode(),
                                fullName(item.getFaculty()), item.getUploadDate()))
                        .toList();
        model.addAttribute("materials", materials);
        return "student/dashboard";
    }

    @GetMapping("/materials/{materialId}/download")
    public ResponseEntity<Resource> downloadMaterial(
            Authentication authentication, @PathVariable Long materialId) {
        User student = authenticatedStudent(authentication);
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .filter(item -> item.getSubject() != null
                        && relevantSubjectIds(student).contains(item.getSubject().getId()))
                .orElse(null);
        if (material == null || material.getFileUrl() == null) {
            return ResponseEntity.notFound().build();
        }
        return downloadStoredFile(
                material.getFileUrl(),
                "study-materials/",
                "This study material file is unavailable. Please contact your administrator.");
    }

    @GetMapping("/events")
    public String events(Authentication authentication, Model model) {
        preparePage(authentication, model, "events");
        model.addAttribute("events", eventViews());
        return "student/dashboard";
    }

    @GetMapping("/notices")
    public String notices(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "notices");
        model.addAttribute("notices", noticeViews(student));
        return "student/dashboard";
    }

    @GetMapping("/fees")
    public String fees(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "fees");
        List<FeeView> fees = feeViews(student);
        model.addAttribute("fees", fees);
        model.addAttribute("totalFees", fees.stream().map(FeeView::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("paidFees", fees.stream().map(FeeView::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("pendingFees", fees.stream().map(FeeView::pendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return "student/dashboard";
    }

    @GetMapping("/helpdesk")
    public String helpdesk(Authentication authentication, Model model) {
        User student = preparePage(authentication, model, "helpdesk");
        model.addAttribute("complaints", complaintRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())
                .stream()
                .map(item -> new ComplaintView(item.getId(), item.getTitle(), item.getDescription(),
                        item.getStatus(), item.getCreatedAt(), item.getUpdatedAt()))
                .toList());
        return "student/dashboard";
    }

    @PostMapping("/helpdesk")
    public String createComplaint(
            Authentication authentication,
            @RequestParam String title,
            @RequestParam String description,
            RedirectAttributes redirectAttributes) {
        User student = authenticatedStudent(authentication);
        if (!StringUtils.hasText(title) || title.trim().length() > 200 || !StringUtils.hasText(description)) {
            redirectAttributes.addFlashAttribute("error",
                    "Enter a title (up to 200 characters) and describe the issue.");
            return "redirect:/student/helpdesk";
        }
        Complaint complaint = Complaint.builder()
                .title(title.trim())
                .description(description.trim())
                .student(student)
                .status(ComplaintStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        complaintRepository.save(complaint);
        redirectAttributes.addFlashAttribute("success", "Your helpdesk request has been submitted.");
        return "redirect:/student/helpdesk";
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        preparePage(authentication, model, "profile");
        return "student/dashboard";
    }

    private User preparePage(Authentication authentication, Model model, String section) {
        User student = authenticatedStudent(authentication);
        model.addAttribute("student", new StudentProfile(
                student.getId(),
                fullName(student),
                student.getEmail(),
                student.getDepartment() == null ? null : student.getDepartment().getName(),
                student.getCurrentSemester()));
        model.addAttribute("section", section);
        return student;
    }

    private User authenticatedStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByEmail(authentication.getName())
                .filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Student access required."));
    }

    private List<Subject> relevantSubjects(User student) {
        Long departmentId = student.getDepartment() == null ? null : student.getDepartment().getId();
        Integer semester = student.getCurrentSemester();
        return subjectRepository.findAll().stream()
                .filter(subject -> subject.getDepartment() == null
                        || departmentId != null && subject.getDepartment().getId().equals(departmentId))
                .filter(subject -> subject.getSemester() == null
                        || semester != null && subject.getSemester().equals(semester))
                .toList();
    }

    private List<Long> relevantSubjectIds(User student) {
        return relevantSubjects(student).stream().map(Subject::getId).toList();
    }

    private List<AssignmentView> assignmentViews(List<Subject> subjects, List<Submission> submissions) {
        List<Long> subjectIds = subjects.stream().map(Subject::getId).toList();
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Submission> submissionsByAssignment = submissions.stream()
                .filter(item -> item.getAssignment() != null)
                .collect(Collectors.toMap(item -> item.getAssignment().getId(), Function.identity(), (first, last) -> last));
        return assignmentRepository.findBySubjectIdInOrderByDueDateAsc(subjectIds).stream()
                .map(item -> new AssignmentView(
                        item.getId(),
                        item.getTitle(),
                        item.getDescription(),
                        item.getSubject() == null ? null : item.getSubject().getName(),
                        item.getSubject() == null ? null : item.getSubject().getCode(),
                        fullName(item.getFaculty()),
                        item.getDueDate(),
                        submissionView(submissionsByAssignment.get(item.getId()))))
                .toList();
    }

    private SubmissionView submissionView(Submission submission) {
        return submission == null ? null : new SubmissionView(
                submission.getId(),
                submission.getSubmissionDate(),
                submission.getFileUrl(),
                submission.getStudentComments(),
                submission.getMarksAwarded(),
                submission.getFacultyFeedback());
    }

    private List<NoticeView> noticeViews(User student) {
        Long departmentId = student.getDepartment() == null ? null : student.getDepartment().getId();
        return noticeRepository.findByDepartmentIdOrDepartmentIsNullOrderByPublishDateDesc(departmentId).stream()
                .map(item -> new NoticeView(
                        item.getTitle(),
                        item.getContent(),
                        item.getPublishDate(),
                        item.getDepartment() == null ? "All students" : item.getDepartment().getName()))
                .toList();
    }

    private List<EventView> eventViews() {
        LocalDateTime now = LocalDateTime.now();
        return eventRepository.findAllByOrderByEventDateAsc().stream()
                .map(item -> new EventView(
                        item.getName(), item.getDescription(), item.getEventDate(), item.getLocation(),
                        fullName(item.getOrganizer()), item.getEventDate() != null && item.getEventDate().isBefore(now)))
                .sorted(Comparator.comparing(EventView::past)
                        .thenComparing(EventView::eventDate,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<FeeView> feeViews(User student) {
        return feeRepository.searchFees(student.getId(), null, null, null, null, false,
                        java.time.LocalDate.now()).stream()
                .map(item -> new FeeView(
                        item.getAcademicYear(),
                        item.getSemester(),
                        item.getTotalAmount(),
                        item.getPaidAmount(),
                        item.getOutstandingAmount(),
                        item.getPaymentStatus(),
                        item.getDueDate()))
                .toList();
    }

    private AcademicSubjectView academicSubjectView(
            Subject subject, List<Attendance> attendance, List<Submission> submissions) {
        long present = attendance.stream().filter(item -> item.getStatus() == AttendanceStatus.PRESENT).count();
        List<Integer> marks = submissions.stream().map(Submission::getMarksAwarded)
                .filter(java.util.Objects::nonNull).toList();
        double averageMarks = marks.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
        List<GradeView> grades = submissions.stream()
                .filter(item -> item.getMarksAwarded() != null)
                .map(item -> new GradeView(item.getAssignment().getTitle(), item.getMarksAwarded()))
                .toList();
        return new AcademicSubjectView(
                subject.getName(),
                subject.getCode(),
                subject.getSemester(),
                attendance.size(),
                percentage(present, attendance.size()),
                Double.isNaN(averageMarks) ? null : averageMarks,
                grades);
    }

    private double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : numerator * 100.0 / denominator;
    }

    private String fullName(User user) {
        if (user == null) {
            return null;
        }
        return ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
    }

    private String storeSubmissionFile(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Choose a file with a valid filename.");
        }
        String safeOriginalName = originalName.replace('\\', '/');
        safeOriginalName = safeOriginalName.substring(safeOriginalName.lastIndexOf('/') + 1);
        String extension = safeOriginalName.contains(".")
                ? safeOriginalName.substring(safeOriginalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_UPLOAD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Upload a PDF, Office document, TXT, or CSV file.");
        }
        String safeName = safeOriginalName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 120) {
            safeName = safeName.substring(safeName.length() - 120);
        }
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize().resolve("submissions").normalize();
        Files.createDirectories(directory);
        String storedName = UUID.randomUUID() + "_" + safeName;
        Path destination = directory.resolve(storedName).normalize();
        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid submission filename.");
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
        return "submissions/" + storedName;
    }

    private void deleteStoredSubmissionFile(String storedPath) {
        if (storedPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoredFile(storedPath, "submissions/"));
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Could not remove replaced assignment submission file {}", storedPath, ex);
        }
    }

    private ResponseEntity<Resource> downloadStoredFile(String storedPath, String allowedPrefix) {
        return downloadStoredFile(storedPath, allowedPrefix, null);
    }

    private ResponseEntity<Resource> downloadStoredFile(
            String storedPath, String allowedPrefix, String unavailableMessage) {
        try {
            Path filePath = resolveStoredFile(storedPath, allowedPrefix);
            FileSystemResource resource = new FileSystemResource(filePath);
            if (!resource.exists() || !resource.isReadable()) {
                if (unavailableMessage != null) {
                    return unavailableFileResponse(unavailableMessage);
                }
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
            if (unavailableMessage != null) {
                return unavailableFileResponse(unavailableMessage);
            }
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<Resource> unavailableFileResponse(String message) {
        Resource body = new ByteArrayResource(message.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    private Path resolveStoredFile(String storedPath, String allowedPrefix) {
        Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path filePath = root.resolve(storedPath).normalize();
        Path allowedDirectory = root.resolve(allowedPrefix).normalize();
        if (!storedPath.startsWith(allowedPrefix) || !filePath.startsWith(allowedDirectory)) {
            throw new IllegalArgumentException("Invalid stored file path.");
        }
        return filePath;
    }

    public record StudentProfile(Long id, String name, String email, String department, Integer semester) { }
    public record AssignmentView(
            Long id, String title, String description, String subjectName, String subjectCode,
            String facultyName, LocalDateTime dueDate, SubmissionView submission) { }
    public record SubmissionView(
            Long id, LocalDateTime submissionDate, String fileUrl, String studentComments,
            Integer marksAwarded, String facultyFeedback) { }
    public record AttendanceRecordView(java.time.LocalDate date, AttendanceStatus status) { }
    public record AttendanceSubjectView(
            String subjectName, String subjectCode, java.time.LocalDate lastClassDate,
            int totalClasses, long presentClasses, double percentage, List<AttendanceRecordView> records) { }
    public record StudyMaterialView(
            Long id, String title, String description, String fileUrl, String subjectName,
            String subjectCode, String facultyName, LocalDateTime uploadDate) { }
    public record EventView(
            String name, String description, LocalDateTime eventDate, String location,
            String organizer, boolean past) { }
    public record NoticeView(String title, String content, LocalDateTime publishDate, String departmentName) { }
    public record FeeView(
            String academicYear, Integer semester, BigDecimal totalAmount, BigDecimal paidAmount,
            BigDecimal pendingAmount, com.campusflow.entity.FeePaymentStatus paymentStatus,
            java.time.LocalDate dueDate) { }
    public record ComplaintView(
            Long id, String title, String description, ComplaintStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record AcademicSubjectView(
            String subjectName, String subjectCode, Integer semester, int attendanceCount,
            double attendancePercentage, Double averageMarks, List<GradeView> grades) { }
    public record GradeView(String assignmentTitle, Integer marks) { }
}
