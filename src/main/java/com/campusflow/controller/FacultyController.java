package com.campusflow.controller;

import com.campusflow.entity.Attendance;
import com.campusflow.entity.AttendanceStatus;
import com.campusflow.entity.Assignment;
import com.campusflow.entity.Role;
import com.campusflow.entity.StudyMaterial;
import com.campusflow.entity.Submission;
import com.campusflow.entity.Subject;
import com.campusflow.entity.User;
import com.campusflow.repository.AssignmentRepository;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.StudyMaterialRepository;
import com.campusflow.repository.SubmissionRepository;
import com.campusflow.repository.SubjectRepository;
import com.campusflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/faculty")
@RequiredArgsConstructor
@Slf4j
public class FacultyController {

    private static final Set<String> ALLOWED_MATERIAL_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv");
    
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final AttendanceRepository attendanceRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final StudyMaterialRepository studyMaterialRepository;

    @Value("${campusflow.upload.directory:uploads}")
    private String uploadDirectory;
    
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        return "faculty/dashboard";
    }

    @GetMapping("/assignments")
    public String assignments(Authentication authentication, Model model) {
        User faculty = requireFaculty(authentication);
        List<Subject> subjects = assignedSubjects(faculty);
        List<Long> subjectIds = subjects.stream().map(Subject::getId).toList();
        List<Assignment> assignments = subjectIds.isEmpty()
                ? List.of()
                : assignmentRepository.findBySubjectIdInOrderByDueDateAsc(subjectIds).stream()
                        .filter(assignment -> assignment.getFaculty() != null
                                && faculty.getId().equals(assignment.getFaculty().getId())
                                && assignment.getSubject() != null
                                && assignment.getSubject().getFaculty() != null
                                && faculty.getId().equals(assignment.getSubject().getFaculty().getId()))
                        .toList();
        List<FacultyAssignmentView> assignmentViews = assignments.stream()
                .map(assignment -> {
                    List<User> roster = userRepository.findStudentsForSubjects(
                            List.of(assignment.getSubject().getId()));
                    Map<Long, Submission> submissionsByStudent = submissionRepository
                            .findByAssignmentId(assignment.getId()).stream()
                            .filter(submission -> submission.getStudent() != null)
                            .collect(Collectors.toMap(
                                    submission -> submission.getStudent().getId(),
                                    submission -> submission,
                                    (first, last) -> last));
                    return new FacultyAssignmentView(
                            assignment,
                            roster.stream()
                                    .map(student -> new FacultySubmissionView(
                                            student, submissionsByStudent.get(student.getId())))
                                    .toList());
                })
                .toList();

        model.addAttribute("user", faculty);
        model.addAttribute("subjects", subjects);
        model.addAttribute("assignments", assignmentViews);
        return "faculty/assignments";
    }

    @PostMapping("/assignments/save")
    public String saveAssignment(
            Authentication authentication,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) LocalDateTime dueDate,
            RedirectAttributes redirectAttributes) {
        User faculty = requireFaculty(authentication);
        Subject subject = assignedSubject(faculty, subjectId);
        if (title == null || title.isBlank() || title.trim().length() > 255
                || description == null || description.isBlank()
                || subject == null || dueDate == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Enter a title, description, assigned subject, and due date.");
            return "redirect:/faculty/assignments";
        }

        assignmentRepository.saveAndFlush(Assignment.builder()
                .title(title.trim())
                .description(description.trim())
                .subject(subject)
                .faculty(faculty)
                .dueDate(dueDate)
                .build());
        redirectAttributes.addFlashAttribute("success", "Assignment created successfully.");
        return "redirect:/faculty/assignments";
    }

    @PostMapping("/assignments/{assignmentId}/submissions/{submissionId}/grade")
    public String gradeSubmission(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId,
            @RequestParam(required = false) Integer marksAwarded,
            @RequestParam(required = false) String facultyFeedback,
            RedirectAttributes redirectAttributes) {
        User faculty = requireFaculty(authentication);
        Assignment assignment = ownedAssignment(faculty, assignmentId);
        Submission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getAssignment() != null
                        && assignmentId.equals(item.getAssignment().getId()))
                .orElse(null);
        if (assignment == null || submission == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found.");
        }
        if (userRepository.findStudentsForSubjects(List.of(assignment.getSubject().getId())).stream()
                .noneMatch(student -> student.getId().equals(submission.getStudent().getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found.");
        }
        if (marksAwarded != null && marksAwarded < 0) {
            redirectAttributes.addFlashAttribute("error", "Marks cannot be negative.");
            return "redirect:/faculty/assignments";
        }

        submission.setMarksAwarded(marksAwarded);
        submission.setFacultyFeedback(StringUtils.hasText(facultyFeedback) ? facultyFeedback.trim() : null);
        submissionRepository.saveAndFlush(submission);
        redirectAttributes.addFlashAttribute("success", "Submission grade saved.");
        return "redirect:/faculty/assignments";
    }

    @GetMapping("/assignments/{assignmentId}/submissions/{submissionId}/download")
    public ResponseEntity<Resource> downloadSubmission(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId) {
        User faculty = requireFaculty(authentication);
        Assignment assignment = ownedAssignment(faculty, assignmentId);
        Submission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getAssignment() != null
                        && assignmentId.equals(item.getAssignment().getId()))
                .orElse(null);
        if (assignment == null || submission == null || submission.getFileUrl() == null
                || userRepository.findStudentsForSubjects(List.of(assignment.getSubject().getId())).stream()
                        .noneMatch(student -> student.getId().equals(submission.getStudent().getId()))) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path filePath = resolveStoredFile(submission.getFileUrl(), "submissions/");
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

    @GetMapping("/materials")
    public String materials(Authentication authentication, Model model) {
        User faculty = requireFaculty(authentication);
        List<Subject> subjects = assignedSubjects(faculty);
        List<Long> subjectIds = subjects.stream().map(Subject::getId).toList();
        List<StudyMaterial> materials = subjectIds.isEmpty()
                ? List.of()
                : studyMaterialRepository.findBySubjectIdInOrderByUploadDateDesc(subjectIds).stream()
                        .filter(material -> material.getFaculty() != null
                                && faculty.getId().equals(material.getFaculty().getId())
                                && material.getSubject() != null
                                && material.getSubject().getFaculty() != null
                                && faculty.getId().equals(material.getSubject().getFaculty().getId()))
                        .toList();

        model.addAttribute("user", faculty);
        model.addAttribute("subjects", subjects);
        model.addAttribute("materials", materials);
        return "faculty/materials";
    }

    @PostMapping("/materials/save")
    public String saveMaterial(
            Authentication authentication,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) MultipartFile file,
            RedirectAttributes redirectAttributes) {
        User faculty = requireFaculty(authentication);
        Subject subject = assignedSubject(faculty, subjectId);
        if (title == null || title.isBlank() || title.trim().length() > 255
                || subject == null || file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Enter a title, select an assigned subject, and choose a file.");
            return "redirect:/faculty/materials";
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            redirectAttributes.addFlashAttribute("error", "The uploaded file must be 10 MB or smaller.");
            return "redirect:/faculty/materials";
        }

        String storedPath;
        try {
            storedPath = storeStudyMaterialFile(file);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/faculty/materials";
        } catch (IOException ex) {
            log.error("Could not store study material for faculty {}", faculty.getId(), ex);
            redirectAttributes.addFlashAttribute("error", "The file could not be saved. Please try again.");
            return "redirect:/faculty/materials";
        }

        try {
            studyMaterialRepository.saveAndFlush(StudyMaterial.builder()
                    .title(title.trim())
                    .description(StringUtils.hasText(description) ? description.trim() : null)
                    .fileUrl(storedPath)
                    .subject(subject)
                    .faculty(faculty)
                    .uploadDate(LocalDateTime.now())
                    .build());
        } catch (DataAccessException ex) {
            deleteStudyMaterialFile(storedPath);
            log.error("Could not save study material metadata for faculty {}", faculty.getId(), ex);
            redirectAttributes.addFlashAttribute("error", "Study material could not be saved. Please try again.");
            return "redirect:/faculty/materials";
        }
        redirectAttributes.addFlashAttribute("success", "Study material uploaded successfully.");
        return "redirect:/faculty/materials";
    }

    @GetMapping("/materials/{materialId}/download")
    public ResponseEntity<Resource> downloadMaterial(
            Authentication authentication, @PathVariable Long materialId) {
        User faculty = requireFaculty(authentication);
        StudyMaterial material = studyMaterialRepository.findById(materialId)
                .filter(item -> item.getFaculty() != null && faculty.getId().equals(item.getFaculty().getId()))
                .filter(item -> item.getSubject() != null
                        && item.getSubject().getFaculty() != null
                        && faculty.getId().equals(item.getSubject().getFaculty().getId()))
                .orElse(null);
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
    public String handleMaterialUploadTooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "The uploaded file must be 10 MB or smaller.");
        return "redirect:/faculty/materials";
    }

    @GetMapping("/attendance")
    public String attendance(
            Authentication authentication,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long sheetSubjectId,
            @RequestParam(required = false) LocalDate sheetDate,
            Model model) {
        User faculty = requireFaculty(authentication);
        List<Subject> subjects = subjectRepository.findByFacultyIdOrderByNameAsc(faculty.getId());
        LocalDate selectedSheetDate = sheetDate == null ? LocalDate.now() : sheetDate;
        Subject sheetSubject = sheetSubjectId == null
                ? null
                : subjects.stream().filter(subject -> sheetSubjectId.equals(subject.getId())).findFirst().orElse(null);
        if (sheetSubjectId != null && sheetSubject == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found.");
        }
        List<Long> ownedSubjectIds = subjects.stream().map(Subject::getId).toList();
        List<Attendance> records = attendanceRepository.findAllByOrderByDateDesc().stream()
                .filter(record -> ownedSubjectIds.contains(record.getSubject().getId()))
                .filter(record -> subjectId == null || subjectId.equals(record.getSubject().getId()))
                .toList();
        List<User> students = sheetSubject == null
                ? List.of()
                : userRepository.findStudentsForSubjects(List.of(sheetSubject.getId()));
        Map<Long, AttendanceStatus> existingStatuses = new HashMap<>();
        if (sheetSubject != null) {
            Set<Long> rosterStudentIds = students.stream().map(User::getId).collect(Collectors.toSet());
            for (Attendance record : attendanceRepository.findBySubjectIdAndDate(sheetSubject.getId(), selectedSheetDate)) {
                if (rosterStudentIds.contains(record.getStudent().getId())) {
                    if (existingStatuses.containsKey(record.getStudent().getId())) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Duplicate attendance records exist for this class and date.");
                    }
                    existingStatuses.put(record.getStudent().getId(), record.getStatus());
                }
            }
        }

        model.addAttribute("user", faculty);
        model.addAttribute("subjects", subjects);
        model.addAttribute("students", students);
        model.addAttribute("attendanceRecords", records);
        model.addAttribute("statuses", AttendanceStatus.values());
        model.addAttribute("selectedSubjectId", subjectId);
        model.addAttribute("sheetSubject", sheetSubject);
        model.addAttribute("sheetSubjectId", sheetSubjectId);
        model.addAttribute("sheetDate", selectedSheetDate);
        model.addAttribute("existingStatuses", existingStatuses);
        return "faculty/attendance";
    }

    @PostMapping("/attendance/save")
    @Transactional
    public String saveAttendance(
            Authentication authentication,
            @RequestParam Long subjectId,
            @RequestParam LocalDate date,
            @RequestParam List<Long> studentIds,
            @RequestParam List<AttendanceStatus> statuses,
            RedirectAttributes redirectAttributes) {
        User faculty = requireFaculty(authentication);
        Subject subject = subjectRepository.findById(subjectId)
                .filter(item -> item.getFaculty() != null && item.getFaculty().getId().equals(faculty.getId()))
                .orElse(null);
        if (subject == null || date == null || studentIds == null || statuses == null
                || studentIds.isEmpty() || studentIds.size() != statuses.size()) {
            redirectAttributes.addFlashAttribute("error",
                    "Select one of your subjects and a date, then choose a status for every student.");
            return attendanceSheetRedirect(subjectId, date);
        }

        List<User> roster = userRepository.findStudentsForSubjects(List.of(subject.getId()));
        Set<Long> rosterIds = roster.stream().map(User::getId).collect(Collectors.toSet());
        Set<Long> submittedIds = new HashSet<>(studentIds);
        if (submittedIds.size() != studentIds.size() || !submittedIds.equals(rosterIds)
                || statuses.stream().anyMatch(java.util.Objects::isNull)) {
            redirectAttributes.addFlashAttribute("error",
                    "The attendance sheet does not match the current student roster. Reload the class and try again.");
            return attendanceSheetRedirect(subjectId, date);
        }

        List<Attendance> existingRecords = attendanceRepository.findBySubjectIdAndDate(subjectId, date);
        Map<Long, Attendance> existingByStudent = new HashMap<>();
        for (Attendance record : existingRecords) {
            if (rosterIds.contains(record.getStudent().getId())) {
                if (existingByStudent.putIfAbsent(record.getStudent().getId(), record) != null) {
                    redirectAttributes.addFlashAttribute("error",
                            "Duplicate attendance records already exist for this class and date. No changes were saved.");
                    return attendanceSheetRedirect(subjectId, date);
                }
            }
        }

        Map<Long, User> rosterById = roster.stream()
                .collect(Collectors.toMap(User::getId, student -> student));
        List<Attendance> updatedRecords = new ArrayList<>(roster.size());
        for (int index = 0; index < studentIds.size(); index++) {
            User student = rosterById.get(studentIds.get(index));
            Attendance attendance = existingByStudent.getOrDefault(student.getId(), new Attendance());
            attendance.setStudent(student);
            attendance.setSubject(subject);
            attendance.setDate(date);
            attendance.setStatus(statuses.get(index));
            updatedRecords.add(attendance);
        }

        if (updatedRecords.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "There are no students in this subject's roster to save attendance for.");
            return attendanceSheetRedirect(subjectId, date);
        }

        attendanceRepository.saveAllAndFlush(updatedRecords);
        redirectAttributes.addFlashAttribute("success", "Class attendance saved successfully.");
        return attendanceSheetRedirect(subjectId, date);
    }

    private String attendanceSheetRedirect(Long subjectId, LocalDate date) {
        if (subjectId == null || date == null) {
            return "redirect:/faculty/attendance";
        }
        return "redirect:/faculty/attendance?sheetSubjectId=" + subjectId + "&sheetDate=" + date;
    }

    private User requireFaculty(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .filter(user -> user.getRole() == Role.FACULTY)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Faculty access required."));
    }

    private List<Subject> assignedSubjects(User faculty) {
        return subjectRepository.findByFacultyIdOrderByNameAsc(faculty.getId());
    }

    private Subject assignedSubject(User faculty, Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        return subjectRepository.findById(subjectId)
                .filter(subject -> subject.getFaculty() != null
                        && faculty.getId().equals(subject.getFaculty().getId()))
                .orElse(null);
    }

    private Assignment ownedAssignment(User faculty, Long assignmentId) {
        if (assignmentId == null) {
            return null;
        }
        return assignmentRepository.findById(assignmentId)
                .filter(assignment -> assignment.getFaculty() != null
                        && faculty.getId().equals(assignment.getFaculty().getId()))
                .filter(assignment -> assignment.getSubject() != null
                        && assignment.getSubject().getFaculty() != null
                        && faculty.getId().equals(assignment.getSubject().getFaculty().getId()))
                .orElse(null);
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
        if (!ALLOWED_MATERIAL_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Upload a PDF, Office document, TXT, or CSV file.");
        }
        String safeName = safeOriginalName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 120) {
            safeName = safeName.substring(safeName.length() - 120);
        }
        Path directory = studyMaterialDirectory();
        Files.createDirectories(directory);
        Path destination = directory.resolve(UUID.randomUUID() + "_" + safeName).normalize();
        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid uploaded filename.");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                ex.addSuppressed(cleanupException);
            }
            throw ex;
        }
        return "study-materials/" + destination.getFileName();
    }

    private void deleteStudyMaterialFile(String storedPath) {
        try {
            Files.deleteIfExists(resolveStudyMaterialFile(storedPath));
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Could not remove unreferenced study material upload {}", storedPath, ex);
        }
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

    private Path resolveStoredFile(String storedPath, String allowedPrefix) {
        Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path allowedDirectory = root.resolve(allowedPrefix).normalize();
        Path filePath = root.resolve(storedPath).normalize();
        if (!storedPath.startsWith(allowedPrefix) || !filePath.startsWith(allowedDirectory)) {
            throw new IllegalArgumentException("Invalid stored file path.");
        }
        return filePath;
    }

    public record FacultyAssignmentView(Assignment assignment, List<FacultySubmissionView> submissions) {
    }

    public record FacultySubmissionView(User student, Submission submission) {
    }

}
