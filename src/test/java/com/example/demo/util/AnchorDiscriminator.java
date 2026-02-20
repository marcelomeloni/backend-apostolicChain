package com.example.demo.util;

import java.security.MessageDigest;

public class AnchorDiscriminator {

    public static byte[] forInstruction(String instructionName) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(("global:" + instructionName).getBytes("UTF-8"));
        byte[] discriminator = new byte[8];
        System.arraycopy(hash, 0, discriminator, 0, 8);
        return discriminator;
    }
}