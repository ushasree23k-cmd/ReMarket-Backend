
package com.remarket.remarket.controller;

import com.remarket.remarket.entity.User;
import com.remarket.remarket.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public UserController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {

        if (user.getEmail() == null || user.getPassword() == null
                || user.getEmail().isBlank()
                || user.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Email and password are required.");
        }

        String email = user.getEmail().trim();

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This email already exists. Please use a different email.");
        }

        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        User savedUser = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registration successful!");
        response.put("id", savedUser.getId());
        response.put("name", savedUser.getName());
        response.put("email", savedUser.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @RequestBody User loginUser,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (loginUser.getEmail() == null
                || loginUser.getEmail().isBlank()
                || loginUser.getPassword() == null
                || loginUser.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Email and password are required.");
        }

        String email = loginUser.getEmail().trim();
        String submittedPassword = loginUser.getPassword();

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Email is not registered.");
        }

        // Migrate an older plain-text password, if one still exists.
        String storedPassword = user.getPassword();

        if (storedPassword != null
                && !storedPassword.startsWith("$2a$")
                && !storedPassword.startsWith("$2b$")
                && !storedPassword.startsWith("$2y$")) {

            if (!storedPassword.equals(submittedPassword)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Incorrect password.");
            }

            user.setPassword(passwordEncoder.encode(submittedPassword));
            userRepository.save(user);
        }

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    email, submittedPassword));

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(
                    context, request, response);

            Map<String, Object> result = new HashMap<>();
            result.put("message", "Login successful!");
            result.put("id", user.getId());
            result.put("name", user.getName());
            result.put("email", user.getEmail());

            return ResponseEntity.ok(result);

        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Incorrect password.");
        }
    }
}
