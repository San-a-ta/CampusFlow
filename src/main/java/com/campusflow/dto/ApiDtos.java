package com.campusflow.dto;

import com.campusflow.entity.AttendanceStatus;
import com.campusflow.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record DepartmentRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code) {
    }

    public record DepartmentResponse(Long id, String name, String code) {
    }

    public record StudentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(min = 8, max = 72) String password,
            @NotNull Long departmentId,
            @NotNull @Min(1) @Max(12) Integer currentSemester) {
    }

    public record UserRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            Long departmentId,
            @Min(1) @Max(12) Integer currentSemester) {
    }

    public record UserResponse(
            Long id,
            String firstName,
            String lastName,
            String email,
            Role role,
            Long departmentId,
            String departmentName,
            Integer currentSemester) {
    }

    public record AttendanceRequest(
            @NotNull Long studentId,
            @NotNull Long subjectId,
            @NotNull java.time.LocalDate date,
            @NotNull AttendanceStatus status) {
    }

    public record AttendanceResponse(
            Long id,
            Long studentId,
            String studentName,
            String studentEmail,
            Long subjectId,
            String subjectName,
            String subjectCode,
            java.time.LocalDate date,
            AttendanceStatus status) {
    }

    public record SubjectRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 50) String code,
            @Min(1) @Max(12) Integer semester,
            Long departmentId,
            Long facultyId) {
    }

    public record SubjectResponse(
            Long id,
            String name,
            String code,
            Integer semester,
            Long departmentId,
            String departmentName,
            Long facultyId,
            String facultyName,
            String facultyEmail) {
    }

    public record ApiError(
            java.time.Instant timestamp,
            int status,
            String error,
            String message,
            java.util.Map<String, String> validationErrors) {
    }
}
