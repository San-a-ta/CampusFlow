package com.campusflow.controller;

import com.campusflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pulse")
@RequiredArgsConstructor
public class ApiController {
    
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    
    @GetMapping("/stats")
    public Map<String, Object> getPulseStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalComplaints", complaintRepository.count());
        stats.put("totalUsers", userRepository.count());
        return stats;
    }
}
