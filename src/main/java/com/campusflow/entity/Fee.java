package com.campusflow.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Fee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeePaymentStatus paymentStatus;

    @NotNull
    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate paymentDate;

    @NotBlank
    @Size(max = 9)
    @Column(nullable = false, length = 9)
    private String academicYear;

    @NotNull
    @Column(nullable = false)
    private Integer semester;

    @Transient
    public BigDecimal getOutstandingAmount() {
        if (totalAmount == null || paidAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    @Transient
    public boolean isOverdue() {
        return dueDate != null
                && dueDate.isBefore(LocalDate.now())
                && getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0;
    }
}
