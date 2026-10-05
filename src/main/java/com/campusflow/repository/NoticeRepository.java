package com.campusflow.repository;
import com.campusflow.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {
    java.util.List<Notice> findByDepartmentIdOrDepartmentIsNullOrderByPublishDateDesc(Long departmentId);
}
