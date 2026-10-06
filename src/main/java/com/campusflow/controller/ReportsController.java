package com.campusflow.controller;

import com.campusflow.entity.Attendance;
import com.campusflow.entity.Role;
import com.campusflow.repository.AttendanceRepository;
import com.campusflow.repository.ComplaintRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.EventRepository;
import com.campusflow.repository.NoticeRepository;
import com.campusflow.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
}