package org.example.alvin.apigateway.model;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiGatewayLog implements Serializable {

  private static final long serialVersionUID = 9213127123819243749L;
  private String targetServer;
  private String requestPath;
  private String method;
  private String schema;
  private String ip;
  private OffsetDateTime requestStartTime;
  private Map<String, String> requestParams;
  private String requestBody;
  private Long executionTime;
  private String requestContentType;
  private int statusCode;
}
