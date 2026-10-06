package com.campusflow;

import com.campusflow.repository.FeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class FeeRepositoryTests {

    @Autowired
    private FeeRepository feeRepository;

    @Test
    void feeQueriesReturnDatabaseResults() {
        assertNotNull(feeRepository.searchFees(null, null, null, null, false, LocalDate.now()));
        assertNotNull(feeRepository.sumTotalAmount());
        assertNotNull(feeRepository.sumPaidAmount());
        assertNotNull(feeRepository.sumOutstandingAmount());
        assertNotNull(feeRepository.sumOverdueAmount(LocalDate.now()));
    }
}
