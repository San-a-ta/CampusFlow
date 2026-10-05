package com.campusflow.controller;

import com.campusflow.repository.*;
import com.campusflow.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NoticeRepository noticeRepository;
    private final ComplaintRepository complaintRepository;
    
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

    @GetMapping("/attendance")
    public String attendance(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        // We need an AttendanceRepository. Let's assume it exists.
        // Wait, I should check if they were injected. 
        // I will do that in the next step. I'll just pass an empty list if not injected, but it's better to inject them.
    }

