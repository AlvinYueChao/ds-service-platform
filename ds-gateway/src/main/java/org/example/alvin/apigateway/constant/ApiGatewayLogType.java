package org.example.alvin.apigateway.constant;

public class ApiGatewayLogType {

  private ApiGatewayLogType() {
  }

  /**
   * request body is json format
   */
  public static final String APPLICATION_JSON_REQUEST = "applicationJsonRequest";
  /**
   * request body is xml format
   */
  public static final String APPLICATION_XML_REQUEST = "applicationXmlRequest";
  /**
   * other request body format
   */
  public static final String BASIC_REQUEST = "basicRequest";
  /**
   * request with no-body, general used for get request
   */
  public static final String DEFAULT_REQUEST = "defaultRequest";
}
