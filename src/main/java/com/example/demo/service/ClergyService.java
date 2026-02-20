package com.example.demo.service;

import com.example.demo.dto.ClergyDTO;
import com.example.demo.dto.DashboardStatsDTO;
import com.example.demo.dto.GenesisDTO;
import com.example.demo.model.Clergy;
import com.example.demo.repository.ClergyRepository;
import com.example.demo.util.AnchorDiscriminator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ClergyService {

    @Autowired
    private ClergyRepository clergyRepository;

    @Autowired
    private RpcClient solanaConnection;

    @Autowired
    private Account adminWallet;

    @Value("${solana.program.id}")
    private String programIdString;

    private static final String JESUS_DETERMINISTIC_INPUT = "GOD_Jesus Cristo_ROOT_1970-01-01";

    // -------------------------------------------------------------------------
    // PÚBLICOS
    // -------------------------------------------------------------------------

    public List<Clergy> getPopes() {
        return clergyRepository.findByRole(Clergy.Role.POPE.name());
    }

    public List<Clergy> getBishops() {
        return clergyRepository.findByRole(Clergy.Role.BISHOP.name());
    }

    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setInitialized(checkIfInitializedOnChain());
        
        // 👇 Substitua o countByRole por isto:
        stats.setTotalBishops(clergyRepository.countBishops());
        stats.setTotalPopes(clergyRepository.countPopes());
        
        stats.setTotalViews(0);
        return stats;
    }

    // -------------------------------------------------------------------------
    // CHECK GENESIS ON-CHAIN
    // Usa hashToSeedBytes (raw [u8;32]) porque initialize_genesis.rs
    // declara: seeds = [b"clergy", jesus_hash.as_ref()]  ← bytes fixos
    // -------------------------------------------------------------------------
    private boolean checkIfInitializedOnChain() {
        try {
            String jesusHash = generateHashRaw(JESUS_DETERMINISTIC_INPUT);
            PublicKey programId = new PublicKey(programIdString);

            // SEED = bytes raw do hash (sem prefixo "0x"), 32 bytes
            PublicKey pdaJesus = PublicKey.findProgramAddress(
                    Arrays.asList(
                        "clergy".getBytes(StandardCharsets.UTF_8),
                        hashToSeedBytes(jesusHash)   // [u8; 32]
                    ),
                    programId
            ).getAddress();

            var accountInfo = solanaConnection.getApi().getAccountInfo(pdaJesus);
            boolean initialized = accountInfo != null && accountInfo.getValue() != null;

            System.out.println("=== CHECK GENESIS ===");
            System.out.println("PDA Jesus: " + pdaJesus.toBase58());
            System.out.println("AccountInfo: " + (accountInfo != null ? accountInfo.getValue() : "null"));
            System.out.println("Initialized: " + initialized);

            return initialized;
        } catch (Exception e) {
            System.out.println("=== CHECK GENESIS ERRO: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // CREATE CLERGY
    // create_clergy.rs usa: seeds = [b"clergy", hash.as_bytes()]
    // ou seja, seed = bytes UTF-8 da String "0x<64hex>" — NÃO raw bytes
    // -------------------------------------------------------------------------
    @Transactional
    public Clergy createClergy(ClergyDTO dto) throws Exception {
        String deterministicHash = generateDeterministicHash(dto);
        dto.setHash(deterministicHash);

        if (clergyRepository.existsById(dto.getHash())) {
            throw new RuntimeException("Hash já registrado. Este clérigo já existe.");
        }

        String txSignature = sendTransactionToSolana(dto);
        if (txSignature == null || txSignature.isEmpty()) {
            throw new RuntimeException("Falha ao assinar e enviar transação na Solana.");
        }

        // Aguarda confirmação antes de salvar no banco
        waitForConfirmation(txSignature);

        Clergy newClergy = new Clergy();
        newClergy.setHash(dto.getHash());
        newClergy.setParentHash(dto.getParentHash());
        newClergy.setName(dto.getName());
        newClergy.setRole(dto.getRole());
        newClergy.setStartDate(dto.getStartDate());
        newClergy.setPapacyStartDate(dto.getPapacyStartDate());

        return clergyRepository.save(newClergy);
    }

    // -------------------------------------------------------------------------
    // INITIALIZE GENESIS
    // initialize_genesis.rs usa: seeds = [b"clergy", jesus_hash.as_ref()]
    // jesus_hash é [u8; 32] — raw bytes do SHA-256
    // -------------------------------------------------------------------------
    @Transactional
    public void initializeGenesis(GenesisDTO dto) throws Exception {
        System.out.println("=== INICIO GENESIS ===");
        System.out.println("PeterName: " + dto.getPeterName());
        System.out.println("PeterStartDate: " + dto.getPeterStartDate());

        String jesusHash = generateHashRaw(JESUS_DETERMINISTIC_INPUT);
        String peterInput = jesusHash + "_" + dto.getPeterName() + "_POPE_" + dto.getPeterStartDate();
        String peterHash = generateHashRaw(peterInput);

        System.out.println("Jesus Hash: " + jesusHash);
        System.out.println("Peter Hash: " + peterHash);

        PublicKey programId = new PublicKey(programIdString);

        // PDA derivado com raw bytes [u8;32] — igual ao Rust
        byte[] jesusHashBytes = hashToSeedBytes(jesusHash);
        byte[] peterHashBytes = hashToSeedBytes(peterHash);

        PublicKey pdaJesus = PublicKey.findProgramAddress(
                Arrays.asList(
                    "clergy".getBytes(StandardCharsets.UTF_8),
                    jesusHashBytes
                ),
                programId
        ).getAddress();

        PublicKey pdaPeter = PublicKey.findProgramAddress(
                Arrays.asList(
                    "clergy".getBytes(StandardCharsets.UTF_8),
                    peterHashBytes
                ),
                programId
        ).getAddress();

        System.out.println("PDA Jesus: " + pdaJesus.toBase58());
        System.out.println("PDA Peter: " + pdaPeter.toBase58());
        System.out.println("Admin Wallet: " + adminWallet.getPublicKey().toBase58());

        // Contas na ordem exata do Rust: jesus, peter, user, system_program
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(pdaJesus, false, true));
        keys.add(new AccountMeta(pdaPeter, false, true));
        keys.add(new AccountMeta(adminWallet.getPublicKey(), true, true));
        keys.add(new AccountMeta(new PublicKey("11111111111111111111111111111111"), false, false));

        byte[] discriminator = AnchorDiscriminator.forInstruction("initialize_genesis");

        long peterStartDateEpochDay = dto.getPeterStartDate().toEpochDay();

        System.out.println("=== INSTRUCTION DATA DEBUG ===");
        System.out.println("Discriminator: " + Arrays.toString(discriminator));
        System.out.println("Jesus hash bytes length: " + jesusHashBytes.length);
        System.out.println("Peter hash bytes length: " + peterHashBytes.length);
        System.out.println("Peter name: " + dto.getPeterName()
                + " (" + dto.getPeterName().getBytes(StandardCharsets.UTF_8).length + " bytes)");
        System.out.println("Peter start_date (epochDay): " + peterStartDateEpochDay);

        // Serialização Borsh / Anchor (little-endian)
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(discriminator);                   // [u8; 8]
        buffer.put(jesusHashBytes);                  // [u8; 32]
        buffer.put(peterHashBytes);                  // [u8; 32]
        putAnchorString(buffer, dto.getPeterName()); // Borsh String: u32_len + utf8
        buffer.putLong(peterStartDateEpochDay);      // i64

        byte[] instructionData = Arrays.copyOf(buffer.array(), buffer.position());

        System.out.println("Instruction data length: " + instructionData.length);
        System.out.println("Instruction data (hex): " + bytesToHex(instructionData));

        String recentBlockhash = solanaConnection.getApi().getLatestBlockhash().getValue().getBlockhash();
        System.out.println("=== RECENT BLOCKHASH: " + recentBlockhash);

        Transaction transaction = new Transaction();
        transaction.addInstruction(new TransactionInstruction(programId, keys, instructionData));
        transaction.setRecentBlockHash(recentBlockhash);

        String txSignature;
        try {
            txSignature = solanaConnection.getApi().sendTransaction(transaction, adminWallet);
            System.out.println("=== TX SIGNATURE: " + txSignature);
            System.out.println("=== Explorer: https://explorer.solana.com/tx/" + txSignature + "?cluster=devnet");
        } catch (Exception e) {
            System.out.println("=== ERRO AO ENVIAR TX: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erro na transação Solana: " + e.getMessage());
        }

        if (txSignature == null || txSignature.isEmpty()) {
            throw new RuntimeException("Falha na transação Genesis - signature nula.");
        }

        // Aguarda confirmação real antes de salvar no banco
        waitForConfirmation(txSignature);

        System.out.println("=== SALVANDO NO BANCO ===");

        Clergy jesus = new Clergy();
        jesus.setHash(jesusHash);
        jesus.setName("Jesus Cristo");
        jesus.setRole(Clergy.Role.ROOT);
        jesus.setStartDate(java.time.LocalDate.ofEpochDay(0));
        jesus.setParentHash(null);
        clergyRepository.save(jesus);
        System.out.println("Jesus salvo: " + jesusHash);

        Clergy peter = new Clergy();
        peter.setHash(peterHash);
        peter.setName(dto.getPeterName());
        peter.setRole(Clergy.Role.POPE);
        peter.setStartDate(dto.getPeterStartDate());
        peter.setParentHash(jesusHash);
        peter.setPapacyStartDate(dto.getPeterStartDate());
        clergyRepository.save(peter);
        System.out.println("Pedro salvo: " + peterHash);

        System.out.println("=== GENESIS COMPLETO ===");
    }

    // -------------------------------------------------------------------------
    // MÉTODOS AUXILIARES
    // -------------------------------------------------------------------------

    /**
     * Aguarda confirmação on-chain da transação (polling até 60s).
     * Lança RuntimeException se a TX falhar ou não confirmar no tempo limite.
     */
   /**
 * Aguarda confirmação on-chain da transação (polling até 60s).
 * Lança RuntimeException se a TX falhar ou não confirmar no tempo limite.
 */
private void waitForConfirmation(String txSignature) throws Exception {
    System.out.println("=== AGUARDANDO CONFIRMAÇÃO DA SOLANA...");
    for (int i = 0; i < 30; i++) {
        Thread.sleep(2000);
        try {
            var statuses = solanaConnection.getApi()
                    .getSignatureStatuses(List.of(txSignature), true);

            if (statuses != null
                    && statuses.getValue() != null
                    && !statuses.getValue().isEmpty()
                    && statuses.getValue().get(0) != null) {

                var status = statuses.getValue().get(0);
                String confirmation = status.getConfirmationStatus();

                System.out.println("Tentativa " + (i + 1) + " — status: " + confirmation);

                if ("finalized".equals(confirmation) || "confirmed".equals(confirmation)) {
                    System.out.println("=== TX CONFIRMADA: " + confirmation);
                    return;
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception ignored) {
            System.out.println("Tentativa " + (i + 1) + " — RPC sem resposta ainda.");
        }
    }
    throw new RuntimeException(
        "TX não confirmada em 60s. Verifique: https://explorer.solana.com/tx/"
        + txSignature + "?cluster=devnet"
    );
}

    private String generateDeterministicHash(ClergyDTO dto) {
        String input = (dto.getParentHash() != null ? dto.getParentHash() : "ROOT")
                + "_" + dto.getName()
                + "_" + dto.getRole()
                + "_" + dto.getStartDate();
        return generateHashRaw(input);
    }

    private String generateHashRaw(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "0x" + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converte hash "0x<hex64>" para [u8; 32].
     * Usado como seed no initialize_genesis (Rust recebe [u8;32]).
     */
    private byte[] hashToSeedBytes(String hash) {
        String hex = hash.startsWith("0x") ? hash.substring(2) : hash;
        return hexStringToBytes(hex);
    }

    private byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Borsh String: u32 (little-endian) com tamanho + bytes UTF-8 */
    private void putAnchorString(ByteBuffer buffer, String val) {
        byte[] bytes = val.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    // -------------------------------------------------------------------------
    // SEND TRANSACTION — create_clergy
    // Rust: seeds = [b"clergy", hash.as_bytes()]
    // Java: seed = dto.getHash().getBytes(UTF_8)  ← String "0x<hex64>"
    // -------------------------------------------------------------------------
    private String sendTransactionToSolana(ClergyDTO dto) {
        try {
            PublicKey programId = new PublicKey(programIdString);

            // SEED = bytes UTF-8 da String hash — igual ao hash.as_bytes() do Rust
            PublicKey pda = PublicKey.findProgramAddress(
                    Arrays.asList(
                        "clergy".getBytes(StandardCharsets.UTF_8),
                        dto.getHash().getBytes(StandardCharsets.UTF_8)  // ← corrigido
                    ),
                    programId
            ).getAddress();

            System.out.println("=== sendTransactionToSolana ===");
            System.out.println("PDA: " + pda.toBase58());

            List<AccountMeta> keys = new ArrayList<>();
            keys.add(new AccountMeta(pda, false, true));
            keys.add(new AccountMeta(adminWallet.getPublicKey(), true, true));
            keys.add(new AccountMeta(new PublicKey("11111111111111111111111111111111"), false, false));

            String recentBlockhash = solanaConnection.getApi().getLatestBlockhash().getValue().getBlockhash();

            Transaction transaction = new Transaction();
            transaction.setRecentBlockHash(recentBlockhash);
            transaction.addInstruction(
                new TransactionInstruction(programId, keys, buildAnchorInstructionData(dto))
            );

            String sig = solanaConnection.getApi().sendTransaction(transaction, adminWallet);
            System.out.println("TX: " + sig);
            System.out.println("Explorer: https://explorer.solana.com/tx/" + sig + "?cluster=devnet");
            return sig;

        } catch (Exception e) {
            System.out.println("=== ERRO sendTransactionToSolana: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    public Page<Clergy> findByRole(String role, Pageable pageable) {
    return clergyRepository.findByRole(role, pageable);
}
    private byte[] buildAnchorInstructionData(ClergyDTO dto) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Discriminator fixo para "create_clergy" — gere via AnchorDiscriminator se preferir
        buffer.put(new byte[]{
            (byte) 152, (byte) 46, (byte) 13, (byte) 116,
            (byte) 75, (byte) 132, (byte) 64, (byte) 118
        });

        putAnchorString(buffer, dto.getHash());
        putAnchorString(buffer, dto.getParentHash() != null ? dto.getParentHash() : "");
        putAnchorString(buffer, dto.getName());
        buffer.put((byte) dto.getRole().ordinal());      // enum: Bishop=0, Pope=1, Root=2
        buffer.putLong(dto.getStartDate().toEpochDay()); // i64

        if (dto.getPapacyStartDate() != null) {
            buffer.put((byte) 1);
            buffer.putLong(dto.getPapacyStartDate().toEpochDay());
        } else {
            buffer.put((byte) 0);
        }

        return Arrays.copyOf(buffer.array(), buffer.position());
    }
}