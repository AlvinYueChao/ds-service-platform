package org.example.alvin.apigateway;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.alvin.apigateway.model.ApiGatewayLog;
import org.example.alvin.apigateway.constant.ApiGatewayLogType;

@Slf4j
public class ApiGatewayLogInfoFactory {

  private ApiGatewayLogInfoFactory() {
  }

  public static void log(String type, ApiGatewayLog apiGatewayLog) {
    switch (type) {
      case ApiGatewayLogType.APPLICATION_JSON_REQUEST:
      case ApiGatewayLogType.APPLICATION_XML_REQUEST:
      case ApiGatewayLogType.BASIC_REQUEST:
        log.info("[ip: {}], method: {}, uri: {}, route id: {}, status: {}, executionTime: {} ms, requestBody: {}",
            apiGatewayLog.getIp(), apiGatewayLog.getMethod(), apiGatewayLog.getUri(), apiGatewayLog.getTargetServer(),
            apiGatewayLog.getStatusCode(), apiGatewayLog.getExecutionTime(), StringUtils.remove(apiGatewayLog.getRequestBody(), System.lineSeparator()));
        break;
      case ApiGatewayLogType.DEFAULT_REQUEST:
        log.info("[ip: {}], method: {}, uri: {}, route id: {}, status: {}, executionTime: {} ms, RequestParams: {}",
            apiGatewayLog.getIp(), apiGatewayLog.getMethod(), apiGatewayLog.getUri(), apiGatewayLog.getTargetServer(),
            apiGatewayLog.getStatusCode(), apiGatewayLog.getExecutionTime(), apiGatewayLog.getRequestParams());
        break;
      default:
        log.warn("the request type {} is not supported", type);
        break;
    }
  }
}
