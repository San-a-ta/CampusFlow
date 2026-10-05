package com.campusflow.repository;
import com.campusflow.entity.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {
    java.util.List<StudyMaterial> findBySubjectId(Long subjectId);
}
