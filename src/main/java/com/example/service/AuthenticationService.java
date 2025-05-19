package com.example.service;

import com.example.entity.User;
import com.example.model.AuthToken;
import com.example.model.LoginRequest;
import com.example.model.RegisterRequest;
import com.example.repository.UserRepository;
import com.example.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthenticationService(UserRepository userRepository, JwtUtil jwtUtil, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    // 🔐 Authenticate user and return JWT wrapped in AuthToken
    public AuthToken authenticateUser(LoginRequest loginRequest) {
        Optional<User> userOpt = userRepository.findByUsername(loginRequest.getUsername());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                System.out.println("Login succesful user name and password correct");
                String token = jwtUtil.generateToken(user.getUsername());
                return new AuthToken()
                        .token(token)
                        .tokenType("Bearer")
                        .expiresIn(3600); // Or however long your token lasts
            }
        }

        throw new RuntimeException("Invalid credentials");
    }

    // 📝 Register a new user
    public void registerUser(RegisterRequest registerRequest) {
        System.out.println("DEBUG REGISTER: " + registerRequest);

        if (userRepository.findByUsername(registerRequest.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User newUser = new User();
        newUser.setUsername(registerRequest.getUsername());
        newUser.setEmail(registerRequest.getEmail());
        newUser.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
//        newUser.setRole("USER"); // or based on input

        userRepository.save(newUser);
    }
}
