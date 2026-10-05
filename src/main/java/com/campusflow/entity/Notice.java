package com.campusflow.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Notice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private LocalDateTime publishDate;
    
    @ManyToOne
    @JoinColumn(name = "department_id") // Null means college-wide
    private Department department;
    
    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
}
