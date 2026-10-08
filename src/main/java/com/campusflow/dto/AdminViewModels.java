package com.campusflow.dto;

import com.campusflow.entity.Fee;
import com.campusflow.entity.Subject;
import com.campusflow.entity.Submission;
import com.campusflow.entity.User;

import java.util.List;

public final class AdminViewModels {

    private AdminViewModels() {
    }

    public record DepartmentStudents(String name, Long departmentId, List<User> students) {
    }

    public record DepartmentFees(String name, Long departmentId, List<Fee> fees) {
    }

    public record ComplaintSummary(
            Long id,
            String title,
            String description,
            String status,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {
    }

    public record StudentSubjectPerformance(
            Subject subject,
            List<Submission> submissions,
            long attendanceRecords,
            long presentRecords,
            double attendancePercentage) {
    }
}
