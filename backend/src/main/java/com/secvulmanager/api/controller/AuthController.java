package com.secvulmanager.api.controller;

import com.secvulmanager.api.model.AppUser;
import com.secvulmanager.api.model.Customer;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.UserCustomerAccessRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final UserCustomerAccessRepository customerAccessRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          AppUserRepository userRepository,
                          UserCustomerAccessRepository customerAccessRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.customerAccessRepository = customerAccessRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"Username and Password are required\"}");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Persist security context in HTTP Session
            request.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext()
            );

            AppUser user = userRepository.findByUsername(username).orElseThrow();
            return ResponseEntity.ok(buildUserResponse(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Invalid username or password\"}");
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\": \"Not Authenticated\"}");
        }

        AppUser user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\": \"User record not found\"}");
        }

        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    private Map<String, Object> buildUserResponse(AppUser user) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("fullName", user.getFullName());
        response.put("role", user.getRole().name());
        
        List<Map<String, Object>> allowedCustomers = customerAccessRepository.findByUserId(user.getId()).stream()
                .map(access -> {
                    Map<String, Object> cMap = new HashMap<>();
                    Customer c = access.getCustomer();
                    cMap.put("id", c.getId());
                    cMap.put("customerName", c.getCustomerName());
                    return cMap;
                })
                .collect(Collectors.toList());
        
        response.put("allowedCustomers", allowedCustomers);
        return response;
    }
}
