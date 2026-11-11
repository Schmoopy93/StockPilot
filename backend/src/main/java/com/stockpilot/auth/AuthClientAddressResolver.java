package com.stockpilot.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthClientAddressResolver {

    private final List<Network> trustedProxies;

    public AuthClientAddressResolver(
            @Value("${app.auth.rate-limit-trusted-proxies:}") String trustedProxyRanges) {
        this.trustedProxies = parseTrustedProxies(trustedProxyRanges);
    }

    public String resolve(HttpServletRequest request) {
        InetAddress peer = parseAddress(request.getRemoteAddr());
        if (peer == null) {
            return null;
        }
        if (!isTrusted(peer)) {
            return peer.getHostAddress();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return peer.getHostAddress();
        }

        String[] forwardedAddresses = forwardedFor.split(",");
        List<InetAddress> chain = new ArrayList<>(forwardedAddresses.length);
        for (String value : forwardedAddresses) {
            InetAddress address = parseAddress(value.trim());
            if (address == null) {
                return peer.getHostAddress();
            }
            chain.add(address);
        }

        InetAddress client = peer;
        for (int index = chain.size() - 1; index >= 0; index--) {
            if (!isTrusted(client)) {
                break;
            }
            client = chain.get(index);
        }
        return client.getHostAddress();
    }

    private List<Network> parseTrustedProxies(String configuredRanges) {
        List<Network> networks = new ArrayList<>();
        if (configuredRanges.isBlank()) {
            return List.of();
        }
        for (String value : configuredRanges.split(",")) {
            String range = value.trim();
            if (range.isEmpty()) {
                continue;
            }
            String[] parts = range.split("/", -1);
            InetAddress address = parseAddress(parts[0]);
            if (address == null || parts.length > 2) {
                throw new IllegalArgumentException("Invalid trusted proxy range: " + range);
            }
            int prefixLength;
            try {
                prefixLength = parts.length == 1
                        ? address.getAddress().length * Byte.SIZE
                        : Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy range: " + range, exception);
            }
            if (prefixLength < 0 || prefixLength > address.getAddress().length * Byte.SIZE) {
                throw new IllegalArgumentException("Invalid trusted proxy range: " + range);
            }
            networks.add(new Network(address.getAddress(), prefixLength));
        }
        return List.copyOf(networks);
    }

    private InetAddress parseAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String address = value.trim();
        int scope = address.indexOf('%');
        if (scope >= 0) {
            address = address.substring(0, scope);
        }
        if (address.contains(":")) {
            if (!address.matches("[0-9a-fA-F:.]+")) {
                return null;
            }
        } else if (!isIpv4Address(address)) {
            return null;
        }
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private boolean isIpv4Address(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (!octet.matches("\\d{1,3}") || Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private record Network(byte[] address, int prefixLength) {
        private boolean contains(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != address.length) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (candidateAddress[index] != address[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (Byte.SIZE - remainingBits);
            return (candidateAddress[fullBytes] & mask) == (address[fullBytes] & mask);
        }
    }
}
