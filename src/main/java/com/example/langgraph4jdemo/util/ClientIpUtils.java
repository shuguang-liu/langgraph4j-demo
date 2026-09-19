package com.example.langgraph4jdemo.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * @author liushug
 * @description 解析客户端真实 IP（未登录用户用 IP 当 threadId）
 */
public final class ClientIpUtils {

    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };
    private static final Pattern IPV4_WITH_PORT = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}:\\d+$");

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        for (String header : PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (isBlankOrUnknown(value)) {
                continue;
            }
            // X-Forwarded-For 可能是逗号分隔的多级代理，第一个才是客户端真实 IP
            String first = value.split(",")[0].trim();
            return normalize(stripPort(first));
        }
        return normalize(request.getRemoteAddr());
    }

    private static boolean isBlankOrUnknown(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim());
    }

    private static String stripPort(String ip) {
        return IPV4_WITH_PORT.matcher(ip).matches() ? ip.substring(0, ip.indexOf(':')) : ip;
    }

    private static String normalize(String ip) {
        return "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip;
    }

}
