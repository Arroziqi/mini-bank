package com.bank.core.config;

import com.bank.core.model.Account;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

        private final UserRepository userRepository;
        private final AccountRepository accountRepository;
        private final PasswordEncoder passwordEncoder;

        @Override
        public void run(String... args) {
                if (userRepository.count() == 0) {
                        // Seed Admin
                        User admin = User.builder()
                                        .username("admin")
                                        .password(passwordEncoder.encode("admin123"))
                                        .email("admin@bank.com")
                                        .role(User.Role.ADMIN)
                                        .build();
                        userRepository.save(admin);

                        // Seed Customer 1
                        User customer1 = User.builder()
                                        .username("user1")
                                        .password(passwordEncoder.encode("user123"))
                                        .email("user1@bank.com")
                                        .role(User.Role.CUSTOMER)
                                        .build();
                        userRepository.save(customer1);

                        Account acc1 = Account.builder()
                                        .accountNumber("ACC-123456")
                                        .balance(new BigDecimal("1000.00"))
                                        .status(Account.Status.ACTIVE)
                                        .user(customer1)
                                        .build();
                        accountRepository.save(acc1);

                        // Seed Customer 2
                        User customer2 = User.builder()
                                        .username("user2")
                                        .password(passwordEncoder.encode("user123"))
                                        .email("user2@bank.com")
                                        .role(User.Role.CUSTOMER)
                                        .build();
                        userRepository.save(customer2);

                        Account acc2 = Account.builder()
                                        .accountNumber("ACC-789012")
                                        .balance(new BigDecimal("500.00"))
                                        .status(Account.Status.ACTIVE)
                                        .user(customer2)
                                        .build();
                        accountRepository.save(acc2);

                        System.out.println("Demo data seeded: admin/admin123, user1/user123, user2/user123");
                }
        }
}
