package com.campusflow.repository;
import com.campusflow.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    java.util.List<Assignment> findBySubjectId(Long subjectId);
}
