package com.example.demo.controller;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.LoginDTO;
import com.example.demo.model.Admin;
import com.example.demo.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")

public class AuthController {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil; 
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO request) {
        Optional<Admin> adminOptional = adminRepository.findByEmail(request.getEmail());

        if (adminOptional.isPresent()) {
            Admin admin = adminOptional.get();
            
            // Compara a senha crua com a hash do banco:
            if (passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
                
                // Gera o token JWT
                String token = jwtUtil.generateToken(admin.getEmail());

                // Monta a resposta em JSON com o token
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("token", token);
                response.put("email", admin.getEmail());

                return ResponseEntity.ok(response);
            }
        }
        
        // Senha errada ou e-mail não encontrado
        return ResponseEntity.status(401).body("{\"success\": false, \"message\": \"Credenciais inválidas\"}");
    }

}

