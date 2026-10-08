package com.campusflow.repository;

import com.campusflow.entity.Fee;
import com.campusflow.entity.FeePaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeeRepository extends JpaRepository<Fee, Long> {
    @Query("""
            select f from Fee f
            where (:studentId is null or f.student.id = :studentId)
              and (:departmentId is null or f.student.department.id = :departmentId)
              and (:paymentStatus is null or f.paymentStatus = :paymentStatus)
              and (:academicYear is null or f.academicYear = :academicYear)
              and (:search is null or
                   lower(concat(coalesce(f.student.firstName, ''), ' ', coalesce(f.student.lastName, ''))) like lower(concat('%', :search, '%')) or
                   lower(f.student.email) like lower(concat('%', :search, '%')))
              and (:overdueOnly = false or (f.dueDate < :today and f.paidAmount < f.totalAmount))
            order by f.dueDate asc
            """)
    List<Fee> searchFees(
            @Param("studentId") Long studentId,
            @Param("departmentId") Long departmentId,
            @Param("paymentStatus") FeePaymentStatus paymentStatus,
            @Param("academicYear") String academicYear,
            @Param("search") String search,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("today") LocalDate today);

    @Query("select coalesce(sum(f.totalAmount), 0) from Fee f")
    BigDecimal sumTotalAmount();

    @Query("select coalesce(sum(f.paidAmount), 0) from Fee f")
    BigDecimal sumPaidAmount();

    @Query("select coalesce(sum(f.totalAmount - f.paidAmount), 0) from Fee f")
    BigDecimal sumOutstandingAmount();

    @Query("select coalesce(sum(f.totalAmount - f.paidAmount), 0) from Fee f where f.dueDate < :today and f.paidAmount < f.totalAmount")
    BigDecimal sumOverdueAmount(@Param("today") LocalDate today);
}
