package com.example.langgraph4jdemo.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * SM4 国密对称加密工具（依赖 BouncyCastle）
 * <p>
 * - token 加密：SM4/CBC/PKCS7Padding，随机 IV 前置拼接后整体 Base64Url 编码
 * - threadId 派生：确定性加密（IV 固定取密钥本身），同一明文永远得到同一密文
 *
 * @author liushug
 * @description SM4 加密工具
 */
public final class Sm4Util {

    private static final String ALGORITHM = "SM4/CBC/PKCS7Padding";
    private static final String KEY_ALGORITHM = "SM4";
    private static final int BLOCK_SIZE = 16;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Util() {
    }

    /**
     * 随机 IV 加密，输出 Base64Url(IV + 密文)，用于 token：每次登录生成的 token 都不同
     */
    public static String encryptToBase64Url(byte[] key, byte[] plain) {
        byte[] iv = new byte[BLOCK_SIZE];
        new SecureRandom().nextBytes(iv);
        byte[] cipher = encryptCbc(key, iv, plain);
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipher.length);
        buffer.put(iv).put(cipher);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    /**
     * 解密 Base64Url(IV + 密文)，失败抛异常
     */
    public static byte[] decryptFromBase64Url(byte[] key, String text) {
        byte[] all = Base64.getUrlDecoder().decode(text);
        if (all.length < BLOCK_SIZE * 2) {
            throw new IllegalArgumentException("SM4 密文长度不合法");
        }
        byte[] iv = Arrays.copyOfRange(all, 0, BLOCK_SIZE);
        byte[] cipher = Arrays.copyOfRange(all, BLOCK_SIZE, all.length);
        return decryptCbc(key, iv, cipher);
    }

    /**
     * 确定性加密，输出 Hex：IV 固定取密钥本身，同一明文永远得到同一结果，用于生成永久 threadId
     */
    public static String encryptToHexDeterministic(byte[] key, byte[] plain) {
        return HexFormat.of().formatHex(encryptCbc(key, key, plain));
    }

    public static byte[] encryptCbc(byte[] key, byte[] iv, byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
            return cipher.doFinal(plain);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SM4 加密失败", e);
        }
    }

    public static byte[] decryptCbc(byte[] key, byte[] iv, byte[] cipher) {
        try {
            Cipher cipherInstance = Cipher.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipherInstance.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
            return cipherInstance.doFinal(cipher);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SM4 解密失败", e);
        }
    }

}
