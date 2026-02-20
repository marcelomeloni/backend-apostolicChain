// src/main/java/com/example/demo/config/SolanaConfig.java
package com.example.demo.config;

import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SolanaConfig {

    @Value("${solana.rpc.url}")
    private String rpcUrl;

    @Value("${solana.wallet.mnemonic}")
    private String mnemonic;

    @Bean
    public RpcClient solanaConnection() {
        return new RpcClient(rpcUrl);
    }

    @Bean
    public Account adminWallet() {
        List<String> words = Arrays.asList(mnemonic.split(" "));
        return Account.fromBip39Mnemonic(words, "");
    }
}