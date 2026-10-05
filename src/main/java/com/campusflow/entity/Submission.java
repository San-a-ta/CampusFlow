package com.campusflow.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Submission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;
    
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    private LocalDateTime submissionDate;
    private String fileUrl;
    
    @Column(columnDefinition = "TEXT")
    private String studentComments;
    
    private Integer marksAwarded;
    
    @Column(columnDefinition = "TEXT")
    private String facultyFeedback;
}
