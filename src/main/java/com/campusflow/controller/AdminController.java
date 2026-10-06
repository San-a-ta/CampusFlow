package com.campusflow.controller;

import com.campusflow.entity.Event;
import com.campusflow.entity.User;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NoticeRepository noticeRepository;
    private final ComplaintRepository complaintRepository;
    private final EventRepository eventRepository;


    // =========================================================
    // DASHBOARD
    // =========================================================

    @GetMapping("/dashboard")
    public String dashboard(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        model.addAttribute(
                "totalStudents",
                userRepository
                        .findByRole(com.campusflow.entity.Role.STUDENT)
                        .size()
        );

        model.addAttribute(
                "totalFaculty",
                userRepository
                        .findByRole(com.campusflow.entity.Role.FACULTY)
                        .size()
        );

        model.addAttribute(
                "totalDepartments",
                departmentRepository.count()
        );

        model.addAttribute(
                "openComplaints",
                complaintRepository.count()
        );

        return "admin/dashboard";
    }


    // =========================================================
    // STUDENTS
    // =========================================================

    @GetMapping("/students")
    public String students(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        model.addAttribute(
                "students",
                userRepository
                        .findByRole(com.campusflow.entity.Role.STUDENT)
        );

        return "admin/students";
    }


    // =========================================================
    // FACULTY
    // =========================================================

    @GetMapping("/faculty")
    public String faculty(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        model.addAttribute(
                "facultyList",
                userRepository
                        .findByRole(com.campusflow.entity.Role.FACULTY)
        );

        return "admin/faculty";
    }


    // =========================================================
    // DEPARTMENTS
    // =========================================================

    @GetMapping("/departments")
    public String departments(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);
        model.addAttribute(
                "departments",
                departmentRepository.findAll()
        );

        return "admin/departments";
    }


    // =========================================================
    // COMPLAINTS
    // =========================================================

    @GetMapping("/complaints")
    public String complaints(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        model.addAttribute(
                "complaints",
                complaintRepository.findAll()
        );

        return "admin/complaints";
    }


    // =========================================================
    // NOTICES
    // =========================================================

    @GetMapping("/notices")
    public String notices(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        model.addAttribute(
                "notices",
                noticeRepository.findAll()
        );

        return "admin/notices";
    }


    // =========================================================
    // EVENTS
    // =========================================================

    @GetMapping("/events")
    public String events(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        model.addAttribute(
                "events",
                eventRepository.findAll()
        );

        return "admin/events";
    }


    // =========================================================
    // ADD / EDIT EVENT
    // =========================================================

    @PostMapping("/events/save")
    public String saveEvent(
            Authentication authentication,

            @RequestParam(required = false)
            Long id,

            @RequestParam String name,

            @RequestParam String description,

            @RequestParam String eventDate,

            @RequestParam String location,

            RedirectAttributes redirectAttributes) {

        User organizer = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        Event event;

        if (id != null) {

            event = eventRepository
                    .findById(id)
                    .orElseThrow();

        } else {

            event = new Event();

            event.setOrganizer(organizer);
        }

        event.setName(name);
        event.setDescription(description);
        event.setLocation(location);

        event.setEventDate(
                LocalDateTime.parse(
                        eventDate,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                )
        );

        eventRepository.save(event);

        redirectAttributes.addFlashAttribute(
                "success",
                id == null
                        ? "Event created successfully!"
                        : "Event updated successfully!"
        );

        return "redirect:/admin/events";
    }


    // =========================================================
    // DELETE EVENT
    // =========================================================

    @PostMapping("/events/delete")
    public String deleteEvent(
            @RequestParam Long id,
            RedirectAttributes redirectAttributes) {

        eventRepository.deleteById(id);

        redirectAttributes.addFlashAttribute(
                "success",
                "Event deleted successfully!"
        );

        return "redirect:/admin/events";
    }


    // =========================================================
    // SETTINGS
    // =========================================================

    @GetMapping("/settings")
    public String settings(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        return "admin/settings";
    }


    // =========================================================
    // ATTENDANCE
    // =========================================================

    @GetMapping("/attendance")
    public String attendance(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        model.addAttribute("user", user);

        return "admin/attendance";
    }
}