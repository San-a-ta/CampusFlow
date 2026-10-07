package com.campusflow.controller;

import com.campusflow.entity.Attendance;
import com.campusflow.entity.Role;
import com.campusflow.repository.AssignmentRepository;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.ComplaintRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.EventRepository;
import com.campusflow.repository.FeeRepository;
import com.campusflow.repository.NoticeRepository;
import com.campusflow.repository.StudyMaterialRepository;
import com.campusflow.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ComplaintRepository complaintRepository;
    private final NoticeRepository noticeRepository;
    private final EventRepository eventRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final FeeRepository feeRepository;


    @GetMapping
    public String reports(Authentication authentication, Model model) {
        model.addAttribute(
                "user",
                userRepository.findByEmail(authentication.getName()).orElseThrow()
        );

        // =========================
        // BASIC COUNTS
        // =========================

        long students =
                userRepository.findByRole(Role.STUDENT).size();

        long faculty =
                userRepository.findByRole(Role.FACULTY).size();

        long departments =
                departmentRepository.count();

        long complaints =
                complaintRepository.count();

        long notices =
                noticeRepository.count();

        long events =
                eventRepository.count();


        // =========================
        // ATTENDANCE
        // =========================

        List<Attendance> attendanceList =
                attendanceRepository.findAll();

        long totalAttendance =
                attendanceList.size();

        long present =
                attendanceList.stream()
                        .filter(a -> a.getStatus() != null
                                && a.getStatus().name().equalsIgnoreCase("PRESENT"))
                        .count();

        long absent =
                attendanceList.stream()
                        .filter(a -> a.getStatus() != null
                                && a.getStatus().name().equalsIgnoreCase("ABSENT"))
                        .count();

        long late =
                attendanceList.stream()
                        .filter(a -> a.getStatus() != null
                                && a.getStatus().name().equalsIgnoreCase("LATE"))
                        .count();


        double attendancePercentage = 0;

        if (totalAttendance > 0) {
            attendancePercentage =
                    (present * 100.0) / totalAttendance;
        }


        // =========================
        // MODEL
        // =========================

        model.addAttribute("students", students);
        model.addAttribute("faculty", faculty);
        model.addAttribute("departments", departments);

        model.addAttribute("complaints", complaints);
        model.addAttribute("notices", notices);
        model.addAttribute("events", events);

        model.addAttribute("totalAttendance", totalAttendance);
        model.addAttribute("present", present);
        model.addAttribute("absent", absent);
        model.addAttribute("late", late);

        model.addAttribute(
                "attendancePercentage",
                Math.round(attendancePercentage)
        );


        return "admin/reports";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport() {
        List<Attendance> attendanceList = attendanceRepository.findAll();
        long totalAttendance = attendanceList.size();
        long present = countAttendanceStatus(attendanceList, "PRESENT");
        long absent = countAttendanceStatus(attendanceList, "ABSENT");
        long late = countAttendanceStatus(attendanceList, "LATE");
        long excused = countAttendanceStatus(attendanceList, "EXCUSED");
        BigDecimal attendancePercentage = totalAttendance == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(present)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalAttendance), 2, RoundingMode.HALF_UP);

        List<String[]> rows = List.of(
                new String[]{"Total Students", String.valueOf(userRepository.findByRole(Role.STUDENT).size())},
                new String[]{"Total Faculty", String.valueOf(userRepository.findByRole(Role.FACULTY).size())},
                new String[]{"Total Departments", String.valueOf(departmentRepository.count())},
                new String[]{"Total Complaints", String.valueOf(complaintRepository.count())},
                new String[]{"Total Attendance Records", String.valueOf(totalAttendance)},
                new String[]{"Present Attendance Records", String.valueOf(present)},
                new String[]{"Absent Attendance Records", String.valueOf(absent)},
                new String[]{"Late Attendance Records", String.valueOf(late)},
                new String[]{"Excused Attendance Records", String.valueOf(excused)},
                new String[]{"Attendance Percentage", attendancePercentage.toPlainString() + "%"},
                new String[]{"Total Assignments", String.valueOf(assignmentRepository.count())},
                new String[]{"Total Study Materials/Documents", String.valueOf(studyMaterialRepository.count())},
                new String[]{"Total Events", String.valueOf(eventRepository.count())},
                new String[]{"Fee Records", String.valueOf(feeRepository.count())},
                new String[]{"Total Fees", feeRepository.sumTotalAmount().toPlainString()},
                new String[]{"Paid Fees", feeRepository.sumPaidAmount().toPlainString()},
                new String[]{"Pending/Outstanding Fees", feeRepository.sumOutstandingAmount().toPlainString()}
        );

        StringBuilder csv = new StringBuilder("Metric,Value\r\n");
        for (String[] row : rows) {
            csv.append(csvValue(row[0])).append(',')
                    .append(csvValue(row[1])).append("\r\n");
        }

        String filename = "CampusFlow_Admin_Report_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private long countAttendanceStatus(List<Attendance> records, String status) {
        return records.stream()
                .filter(record -> record.getStatus() != null && record.getStatus().name().equals(status))
                .count();
    }

    private String csvValue(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}