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

    // Input determinístico do nó raiz. Nunca deve mudar — qualquer alteração
    // invalida todas as PDAs derivadas na chain.
    private static final String JESUS_DETERMINISTIC_INPUT = "GOD_Jesus Cristo_ROOT_1970-01-01";

    public List<Clergy> getPopes() {
        return clergyRepository.findByRole(Clergy.Role.POPE.name());
    }

    public List<Clergy> getBishops() {
        return clergyRepository.findByRole(Clergy.Role.BISHOP.name());
    }

    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setInitialized(checkIfInitializedOnChain());
        stats.setTotalBishops(clergyRepository.countBishops());
        stats.setTotalPopes(clergyRepository.countPopes());
        stats.setTotalViews(0);
        return stats;
    }

    public Page<Clergy> findByRole(String role, Pageable pageable) {
        return clergyRepository.findByRole(role, pageable);
    }

    // Verifica se o nó raiz (Jesus) já foi inicializado na Solana.
    // A PDA usa raw bytes [u8;32] do hash — conforme initialize_genesis.rs:
    // seeds = [b"clergy", jesus_hash.as_ref()]
    private boolean checkIfInitializedOnChain() {
        try {
            String jesusHash = generateHashRaw(JESUS_DETERMINISTIC_INPUT);
            PublicKey programId = new PublicKey(programIdString);

            PublicKey pdaJesus = PublicKey.findProgramAddress(
                    Arrays.asList(
                        "clergy".getBytes(StandardCharsets.UTF_8),
                        hashToSeedBytes(jesusHash)
                    ),
                    programId
            ).getAddress();

            var accountInfo = solanaConnection.getApi().getAccountInfo(pdaJesus);
            return accountInfo != null && accountInfo.getValue() != null;

        } catch (Exception e) {
            System.out.println("checkIfInitializedOnChain error: " + e.getMessage());
            return false;
        }
    }

    // create_clergy.rs usa hash.as_bytes() — ou seja, a seed é a String UTF-8
    // "0x<hex64>", não os raw bytes. Diferente do genesis. Cuidado ao alterar.
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

    // initialize_genesis.rs usa jesus_hash.as_ref() como seed — raw bytes [u8;32].
    // Ambos Jesus e Peter são criados na mesma instrução Anchor.
    @Transactional
    public void initializeGenesis(GenesisDTO dto) throws Exception {
        String jesusHash = generateHashRaw(JESUS_DETERMINISTIC_INPUT);
        String peterInput = jesusHash + "_" + dto.getPeterName() + "_POPE_" + dto.getPeterStartDate();
        String peterHash = generateHashRaw(peterInput);

        PublicKey programId = new PublicKey(programIdString);
        byte[] jesusHashBytes = hashToSeedBytes(jesusHash);
        byte[] peterHashBytes = hashToSeedBytes(peterHash);

        PublicKey pdaJesus = PublicKey.findProgramAddress(
                Arrays.asList("clergy".getBytes(StandardCharsets.UTF_8), jesusHashBytes),
                programId
        ).getAddress();

        PublicKey pdaPeter = PublicKey.findProgramAddress(
                Arrays.asList("clergy".getBytes(StandardCharsets.UTF_8), peterHashBytes),
                programId
        ).getAddress();

        // Ordem das contas deve ser idêntica ao Rust: jesus, peter, user, system_program
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(pdaJesus, false, true));
        keys.add(new AccountMeta(pdaPeter, false, true));
        keys.add(new AccountMeta(adminWallet.getPublicKey(), true, true));
        keys.add(new AccountMeta(new PublicKey("11111111111111111111111111111111"), false, false));

        byte[] discriminator = AnchorDiscriminator.forInstruction("initialize_genesis");
        long peterStartDateEpochDay = dto.getPeterStartDate().toEpochDay();

        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.put(jesusHashBytes);
        buffer.put(peterHashBytes);
        putAnchorString(buffer, dto.getPeterName());
        buffer.putLong(peterStartDateEpochDay);

        byte[] instructionData = Arrays.copyOf(buffer.array(), buffer.position());

        String recentBlockhash = solanaConnection.getApi().getLatestBlockhash().getValue().getBlockhash();

        Transaction transaction = new Transaction();
        transaction.addInstruction(new TransactionInstruction(programId, keys, instructionData));
        transaction.setRecentBlockHash(recentBlockhash);

        String txSignature;
        try {
            txSignature = solanaConnection.getApi().sendTransaction(transaction, adminWallet);
        } catch (Exception e) {
            throw new RuntimeException("Erro na transação Solana: " + e.getMessage(), e);
        }

        if (txSignature == null || txSignature.isEmpty()) {
            throw new RuntimeException("Falha na transação Genesis — signature nula.");
        }

        waitForConfirmation(txSignature);

        Clergy jesus = new Clergy();
        jesus.setHash(jesusHash);
        jesus.setName("Jesus Cristo");
        jesus.setRole(Clergy.Role.ROOT);
        jesus.setStartDate(java.time.LocalDate.ofEpochDay(0));
        jesus.setParentHash(null);
        clergyRepository.save(jesus);

        Clergy peter = new Clergy();
        peter.setHash(peterHash);
        peter.setName(dto.getPeterName());
        peter.setRole(Clergy.Role.POPE);
        peter.setStartDate(dto.getPeterStartDate());
        peter.setParentHash(jesusHash);
        peter.setPapacyStartDate(dto.getPeterStartDate());
        clergyRepository.save(peter);
    }

    // Polling com timeout de 60s. Idealmente substituir por WebSocket subscription
    // quando o volume de transações aumentar.
    private void waitForConfirmation(String txSignature) throws Exception {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            try {
                var statuses = solanaConnection.getApi()
                        .getSignatureStatuses(List.of(txSignature), true);

                if (statuses != null
                        && statuses.getValue() != null
                        && !statuses.getValue().isEmpty()
                        && statuses.getValue().get(0) != null) {

                    String confirmation = statuses.getValue().get(0).getConfirmationStatus();

                    if ("finalized".equals(confirmation) || "confirmed".equals(confirmation)) {
                        return;
                    }
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception ignored) {
                // RPC pode demorar a indexar — continua tentando
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

    // Converte "0x<hex64>" para [u8;32] — seed esperada pelo programa Rust
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
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Borsh string encoding: u32 length prefix (LE) + bytes UTF-8
    private void putAnchorString(ByteBuffer buffer, String val) {
        byte[] bytes = val.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    private String sendTransactionToSolana(ClergyDTO dto) {
        try {
            PublicKey programId = new PublicKey(programIdString);

            // Seed é a String "0x<hex>" em UTF-8 — espelha hash.as_bytes() do Rust
            PublicKey pda = PublicKey.findProgramAddress(
                    Arrays.asList(
                        "clergy".getBytes(StandardCharsets.UTF_8),
                        dto.getHash().getBytes(StandardCharsets.UTF_8)
                    ),
                    programId
            ).getAddress();

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
            return sig;

        } catch (Exception e) {
            System.out.println("sendTransactionToSolana error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private byte[] buildAnchorInstructionData(ClergyDTO dto) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Discriminator hardcoded — gerar via AnchorDiscriminator se o IDL mudar
        buffer.put(new byte[]{
            (byte) 152, (byte) 46, (byte) 13, (byte) 116,
            (byte) 75, (byte) 132, (byte) 64, (byte) 118
        });

        putAnchorString(buffer, dto.getHash());
        putAnchorString(buffer, dto.getParentHash() != null ? dto.getParentHash() : "");
        putAnchorString(buffer, dto.getName());
        buffer.put((byte) dto.getRole().ordinal()); // Bishop=0, Pope=1, Root=2
        buffer.putLong(dto.getStartDate().toEpochDay());

        if (dto.getPapacyStartDate() != null) {
            buffer.put((byte) 1);
            buffer.putLong(dto.getPapacyStartDate().toEpochDay());
        } else {
            buffer.put((byte) 0);
        }

        return Arrays.copyOf(buffer.array(), buffer.position());
    }
}
