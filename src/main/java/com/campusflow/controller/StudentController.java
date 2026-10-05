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
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {
    
    private final UserRepository userRepository;
    private final NoticeRepository noticeRepository;
    
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        
        Long deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        model.addAttribute("notices", noticeRepository.findByDepartmentIdOrDepartmentIsNullOrderByPublishDateDesc(deptId));
        
        return "student/dashboard";
    }
}
