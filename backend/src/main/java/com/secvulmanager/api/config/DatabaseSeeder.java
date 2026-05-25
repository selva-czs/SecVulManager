package com.secvulmanager.api.config;

import com.secvulmanager.api.model.AppUser;
import com.secvulmanager.api.model.CustomerSoftwareAccess;
import com.secvulmanager.api.model.CustomerTemplate;
import com.secvulmanager.api.model.SecuritySoftware;
import com.secvulmanager.api.model.Enums;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.CustomerSoftwareAccessRepository;
import com.secvulmanager.api.repository.CustomerTemplateRepository;
import com.secvulmanager.api.repository.SecuritySoftwareRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final SecuritySoftwareRepository softwareRepository;
    private final CustomerTemplateRepository templateRepository;
    private final CustomerSoftwareAccessRepository customerSoftwareAccessRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(AppUserRepository userRepository,
                          SecuritySoftwareRepository softwareRepository,
                          CustomerTemplateRepository templateRepository,
                          CustomerSoftwareAccessRepository customerSoftwareAccessRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.softwareRepository = softwareRepository;
        this.templateRepository = templateRepository;
        this.customerSoftwareAccessRepository = customerSoftwareAccessRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Seed default Super Admin
        if (userRepository.findByUsername("admin").isEmpty()) {
            AppUser admin = new AppUser(
                "admin",
                passwordEncoder.encode("admin_pass"),
                "Super Administrator",
                Enums.UserRole.SUPER_ADMIN
            );
            userRepository.save(admin);
            System.out.println("[DatabaseSeeder] Seeded default Super Admin user: admin / admin_pass");
        }

        // 2. Seed standard security software options
        for (String softwareName : Arrays.asList("Kaseya", "Rapidfire", "Nessus")) {
            if (softwareRepository.findBySoftwareName(softwareName).isEmpty()) {
                SecuritySoftware sw = new SecuritySoftware(softwareName, true);
                softwareRepository.save(sw);
                System.out.println("[DatabaseSeeder] Seeded standard software registry item: " + softwareName);
            }
        }

        // 3. Preserve current customer-specific template usability without granting all global software.
        for (CustomerTemplate template : templateRepository.findAll()) {
            if (template.getCustomer() != null && template.getSoftware() != null
                    && customerSoftwareAccessRepository.findByCustomerIdAndSoftwareId(
                        template.getCustomer().getId(),
                        template.getSoftware().getId()
                    ).isEmpty()) {
                customerSoftwareAccessRepository.save(new CustomerSoftwareAccess(template.getCustomer(), template.getSoftware(), true));
                System.out.println("[DatabaseSeeder] Seeded customer software assignment from existing customer template: "
                    + template.getCustomer().getCustomerName() + " / " + template.getSoftware().getSoftwareName());
            }
        }
    }
}
