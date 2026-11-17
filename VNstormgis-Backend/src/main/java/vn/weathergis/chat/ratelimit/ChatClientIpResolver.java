package vn.weathergis.chat.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import vn.weathergis.chat.config.ChatRateLimitProperties;

/** Resolves an IP literal without DNS and trusts forwarded hops only behind configured proxies. */
public class ChatClientIpResolver {
    private final List<Cidr> trusted;

    public ChatClientIpResolver(ChatRateLimitProperties properties) {
        trusted = new ArrayList<>();
        for (String entry : properties.getTrustedProxies()) {
            trusted.add(Cidr.parse(entry.trim()));
        }
    }

    public String resolve(String remoteAddress, String forwardedFor) {
        byte[] remote = parseLiteral(remoteAddress);
        String fallback = remote == null ? "unknown" : format(remote);
        if (remote == null
                || !isTrusted(remote)
                || forwardedFor == null
                || forwardedFor.isBlank()) {
            return fallback;
        }
        String[] hops = forwardedFor.split(",", -1);
        for (int i = hops.length - 1; i >= 0; i--) {
            byte[] hop = parseLiteral(hops[i].trim());
            if (hop == null) {
                return fallback;
            }
            if (!isTrusted(hop)) {
                return format(hop);
            }
        }
        return fallback;
    }

    private boolean isTrusted(byte[] address) {
        return trusted.stream().anyMatch(cidr -> cidr.contains(address));
    }

    static byte[] parseLiteral(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        int zone = candidate.indexOf('%');
        if (zone >= 0) {
            candidate = candidate.substring(0, zone);
        }
        if (candidate.contains(":")) {
            if (!candidate.matches("[0-9a-fA-F:.]+")) {
                return null;
            }
        } else {
            String[] parts = candidate.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (!parts[i].matches("[0-9]{1,3}")) {
                    return null;
                }
                int octet = Integer.parseInt(parts[i]);
                if (octet > 255) {
                    return null;
                }
                bytes[i] = (byte) octet;
            }
            return bytes;
        }
        try {
            return InetAddress.getByName(candidate).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    static String format(byte[] address) {
        if (address.length == 4) {
            return (address[0] & 255)
                    + "."
                    + (address[1] & 255)
                    + "."
                    + (address[2] & 255)
                    + "."
                    + (address[3] & 255);
        }
        int[] words = new int[8];
        for (int i = 0; i < 8; i++) {
            words[i] = ((address[2 * i] & 255) << 8) | (address[2 * i + 1] & 255);
        }
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < 8; ) {
            if (words[i] != 0) {
                i++;
                continue;
            }
            int end = i;
            while (end < 8 && words[end] == 0) {
                end++;
            }
            if (end - i > bestLength) {
                bestStart = i;
                bestLength = end - i;
            }
            i = end;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 8; ) {
            if (i == bestStart) {
                result.append("::");
                i += bestLength;
                continue;
            }
            if (!result.isEmpty() && result.charAt(result.length() - 1) != ':') {
                result.append(':');
            }
            result.append(Integer.toHexString(words[i++]));
        }
        return result.toString();
    }

    private record Cidr(byte[] address, int bits) {
        static Cidr parse(String value) {
            String[] parts = value.split("/", -1);
            byte[] address = parseLiteral(parts[0]);
            if (address == null || parts.length > 2) {
                throw new IllegalStateException("Invalid trusted proxy address");
            }
            int bits;
            try {
                bits = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid trusted proxy CIDR", ex);
            }
            if (bits < 0 || bits > address.length * 8) {
                throw new IllegalStateException("Invalid trusted proxy CIDR prefix");
            }
            return new Cidr(address, bits);
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != address.length) {
                return false;
            }
            int fullBytes = bits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != candidate[i]) {
                    return false;
                }
            }
            int remainder = bits % 8;
            return remainder == 0
                    || ((address[fullBytes] ^ candidate[fullBytes]) & (0xff << (8 - remainder)))
                            == 0;
        }
    }
}
