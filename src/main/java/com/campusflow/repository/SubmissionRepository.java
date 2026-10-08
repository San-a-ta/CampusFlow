package com.campusflow.repository;
import com.campusflow.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    java.util.List<Submission> findByAssignmentId(Long assignmentId);
    java.util.List<Submission> findByStudentId(Long studentId);
    java.util.Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);
}
