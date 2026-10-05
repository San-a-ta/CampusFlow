package com.campusflow.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Complaint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @ManyToOne
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo; // Faculty or Admin
    
    @Enumerated(EnumType.STRING)
    private ComplaintStatus status;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
