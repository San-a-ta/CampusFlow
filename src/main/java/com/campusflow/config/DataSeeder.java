package com.campusflow.config;

import com.campusflow.entity.*;
import com.campusflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NoticeRepository noticeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            Department cs = departmentRepository.save(Department.builder().name("Computer Science").code("CS").build());
            
            User admin = User.builder().email("admin@campusflow.com").password(passwordEncoder.encode("admin123"))
                .firstName("Super").lastName("Admin").role(Role.ADMIN).build();
                
            User hod = User.builder().email("hod@campusflow.com").password(passwordEncoder.encode("hod123"))
                .firstName("John").lastName("Doe").role(Role.HOD).department(cs).build();
                
            User faculty = User.builder().email("faculty@campusflow.com").password(passwordEncoder.encode("faculty123"))
                .firstName("Jane").lastName("Smith").role(Role.FACULTY).department(cs).build();
                
            User student = User.builder().email("student@campusflow.com").password(passwordEncoder.encode("student123"))
                .firstName("Alice").lastName("Williams").role(Role.STUDENT).department(cs).currentSemester(3).build();
                
            userRepository.save(admin);
            userRepository.save(hod);
            userRepository.save(faculty);
            userRepository.save(student);
            
            Notice notice = Notice.builder().title("Welcome to CampusFlow").content("System is now live!")
                .publishDate(LocalDateTime.now()).author(admin).build();
            noticeRepository.save(notice);
            
            System.out.println("Data Seeder: Created demo accounts (password: *123 for all roles).");
        }
    }
}
