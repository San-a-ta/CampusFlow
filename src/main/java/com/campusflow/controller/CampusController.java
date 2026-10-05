package com.campusflow.controller;

import com.campusflow.entity.User;
import com.campusflow.repository.NoticeRepository;
import com.campusflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/campus")
@RequiredArgsConstructor
public class CampusController {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    @GetMapping("/notices")
    public String publicNotices(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        
        // Show notices relevant to the user (campus-wide or their department)
        Long deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        if (user.getRole().name().equals("ADMIN")) {
            model.addAttribute("notices", noticeRepository.findAll());
        } else {
            model.addAttribute("notices", noticeRepository.findByDepartmentIdOrDepartmentIsNullOrderByPublishDateDesc(deptId));
        }
        
        return "campus/notices";
    }
}
