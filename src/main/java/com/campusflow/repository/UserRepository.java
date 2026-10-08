package com.campusflow.repository;
import com.campusflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    java.util.Optional<User> findByEmail(String email);
    java.util.List<User> findByRole(com.campusflow.entity.Role role);

    @Query("""
            select distinct student
            from User student
            where student.role = com.campusflow.entity.Role.STUDENT
              and exists (
                  select subject.id
                  from Subject subject
                  where subject.id in :subjectIds
                    and (subject.department is null or subject.department = student.department)
                    and (subject.semester is null or subject.semester = student.currentSemester)
              )
            order by student.lastName, student.firstName
            """)
    List<User> findStudentsForSubjects(@Param("subjectIds") List<Long> subjectIds);
}
