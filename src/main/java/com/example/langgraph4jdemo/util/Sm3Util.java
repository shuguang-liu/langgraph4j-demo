package com.example.langgraph4jdemo.util;

import org.bouncycastle.crypto.digests.SM3Digest;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * SM3 国密摘要工具：库里的密码只存 SM3 散列，不存明文
 *
 * @author liushug
 * @description SM3 摘要工具
 */
public final class Sm3Util {

    private Sm3Util() {
    }

    public static String hashHex(String input) {
        SM3Digest digest = new SM3Digest();
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes, 0, bytes.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return HexFormat.of().formatHex(out);
    }

}
