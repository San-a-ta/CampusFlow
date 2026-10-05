package com.campusflow.repository;
import com.campusflow.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    java.util.List<Attendance> findByStudentIdAndSubjectId(Long studentId, Long subjectId);
}
