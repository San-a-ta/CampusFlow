package com.campusflow.repository;
import com.campusflow.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    java.util.List<Complaint> findByStudentId(Long studentId);
    java.util.List<Complaint> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    java.util.List<Complaint> findByAssignedToId(Long facultyId);
}
