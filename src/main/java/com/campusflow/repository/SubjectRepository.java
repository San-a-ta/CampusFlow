package com.campusflow.repository;
import com.campusflow.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    java.util.List<Subject> findByFacultyId(Long facultyId);
    java.util.List<Subject> findByDepartmentIdAndSemester(Long deptId, Integer semester);
}
