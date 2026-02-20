package com.example.demo.dto;

import com.example.demo.model.Clergy.Role;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ClergyDTO {
    private String hash;
    private String parentHash;
    private String name;
    private Role role;
    private LocalDate startDate;
    private LocalDate papacyStartDate;
    
    // NOTA: Em um cenário real, você também precisaria receber a "assinatura" 
    // ou a transação já assinada pelo admin no frontend para submeter à rede.
}