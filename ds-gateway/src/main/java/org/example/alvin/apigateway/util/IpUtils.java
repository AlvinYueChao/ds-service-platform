package org.example.alvin.apigateway.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Slf4j
public class IpUtils {

  private static final String IP_UTILS_FLAG = ",";
  private static final String UNKNOWN = "unknown";
  private static final String LOCALHOST_IP = "0:0:0:0:0:0:0:1";
  private static final String LOCALHOST_IP1 = "127.0.0.1";

  private IpUtils() {
  }

  public static String getIpAddress(ServerHttpRequest request) {
    String ip = request.getHeaders().getFirst("X-Forwarded-For");
    if (StringUtils.isEmpty(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
      ip = request.getHeaders().getFirst("x-forwarded-for");
      if (ip != null && ip.length() != 0 && !UNKNOWN.equalsIgnoreCase(ip) && ip.contains(IP_UTILS_FLAG)) {
        // there would be multiple ip address after multiple reverse proxies, the first address is actual ip address
        ip = ip.split(IP_UTILS_FLAG)[0];
      }
    }
    // get ip address from k8s
    if (StringUtils.isEmpty(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
      ip = Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
      if (LOCALHOST_IP1.equalsIgnoreCase(ip) || LOCALHOST_IP.equalsIgnoreCase(ip)) {
        // get ip address from NIC
        InetAddress iNet = null;
        try {
          iNet = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
          log.error("getClientIp error: ", e);
        }
        ip = Objects.requireNonNull(iNet).getHostAddress();
      }
    }
    return ip;
  }
}
