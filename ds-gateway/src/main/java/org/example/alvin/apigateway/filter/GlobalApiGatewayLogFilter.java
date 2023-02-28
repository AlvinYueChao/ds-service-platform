package org.example.alvin.apigateway.filter;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.example.alvin.apigateway.ApiGatewayLogInfoFactory;
import org.example.alvin.apigateway.constant.ApiGatewayLogType;
import org.example.alvin.apigateway.model.ApiGatewayLog;
import org.example.alvin.apigateway.util.IpUtils;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Order(-100)
public class GlobalApiGatewayLogFilter implements GlobalFilter {

  private final List<HttpMessageReader<?>> messageReaders = HandlerStrategies.withDefaults().messageReaders();
  private static final List<HttpMessageReader<?>> MESSAGE_READERS = HandlerStrategies.withDefaults().messageReaders();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // log for all requests for now, we can improve to add custom logger access check later
    ApiGatewayLog apiGatewayLog = parseApiGatewayLog(exchange);
    ServerHttpRequest request = exchange.getRequest();
    MediaType contentType = request.getHeaders().getContentType();
    if (contentType == null) {
      return writeDefaultLog(exchange, chain, apiGatewayLog);
    }
    apiGatewayLog.setRequestContentType(contentType.getType() + "/" + contentType.getSubtype());
    if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType) || MediaType.APPLICATION_XML.isCompatibleWith(contentType)) {
      return writeBodyLog(exchange, chain, apiGatewayLog);
    } else {
      return writeBasicLog(exchange, chain, apiGatewayLog);
    }
  }

  private Mono<Void> writeBasicLog(ServerWebExchange exchange, GatewayFilterChain chain, ApiGatewayLog apiGatewayLog) {
    return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {
      DataBufferUtils.retain(dataBuffer);
      final Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
      final ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
        @Override
        public Flux<DataBuffer> getBody() {
          return cachedFlux;
        }

        @Override
        public MultiValueMap<String, String> getQueryParams() {
          return UriComponentsBuilder.fromUri(exchange.getRequest().getURI()).build().getQueryParams();
        }
      };
      StringBuilder builder = new StringBuilder();
      MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
      if (!CollectionUtils.isEmpty(queryParams)) {
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
          builder.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
        }
      }
      apiGatewayLog.setRequestBody(builder.toString());
      ServerHttpResponseDecorator decoratedResponse = recordResponseLog(exchange, apiGatewayLog);
      return chain.filter(exchange.mutate().request(mutatedRequest).response(decoratedResponse).build()).then(Mono.fromRunnable(() -> ApiGatewayLogInfoFactory.log(ApiGatewayLogType.BASIC_REQUEST, apiGatewayLog)));
    });
  }

  private Mono<Void> writeBodyLog(ServerWebExchange exchange, GatewayFilterChain chain, ApiGatewayLog apiGatewayLog) {
    ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);
    Mono<String> modifiedBody = serverRequest.bodyToMono(String.class).flatMap(body -> {
      apiGatewayLog.setRequestBody(body);
      return Mono.just(body);
    });
    BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
    HttpHeaders headers = new HttpHeaders();
    headers.putAll(exchange.getRequest().getHeaders());
    // the new content type will be computed by bodyInserter and then set in the request decorator
    headers.remove(HttpHeaders.CONTENT_LENGTH);
    CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
    return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
      ServerHttpRequest decoratedRequest = requestDecorate(exchange, headers, outputMessage);
      ServerHttpResponseDecorator decoratedResponse = recordResponseLog(exchange, apiGatewayLog);
      return chain.filter(exchange.mutate().request(decoratedRequest).response(decoratedResponse).build()).then(Mono.fromRunnable(() -> ApiGatewayLogInfoFactory.log(ApiGatewayLogType.APPLICATION_JSON_REQUEST, apiGatewayLog)));
    }));
  }

  private ServerHttpResponseDecorator recordResponseLog(ServerWebExchange exchange, ApiGatewayLog apiGatewayLog) {
    ServerHttpResponse response = exchange.getResponse();
    DataBufferFactory bufferFactory = response.bufferFactory();
    return new ServerHttpResponseDecorator(response) {
      @Override
      public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        if (body instanceof Flux) {
          long executeTime = OffsetDateTime.now().toInstant().toEpochMilli() - apiGatewayLog.getRequestStartTime().toInstant().toEpochMilli();
          apiGatewayLog.setExecutionTime(executeTime);
          String originalResponseContentType = exchange.getAttribute(ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);//
          apiGatewayLog.setStatusCode(Objects.requireNonNull(this.getStatusCode()).value());
          if (Objects.equals(this.getStatusCode(), HttpStatus.OK) && !StringUtils.isEmpty(originalResponseContentType)) {
            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
            return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
              DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
              DataBuffer join = dataBufferFactory.join(dataBuffers);
              byte[] content = new byte[join.readableByteCount()];
              join.read(content);
              DataBufferUtils.release(join);
              return bufferFactory.wrap(content);
            }));
          }
        }
        return super.writeWith(body);
      }
    };
  }

  private ServerHttpRequestDecorator requestDecorate(ServerWebExchange exchange, HttpHeaders headers, CachedBodyOutputMessage outputMessage) {
    return new ServerHttpRequestDecorator(exchange.getRequest()) {
      @Override
      public HttpHeaders getHeaders() {
        long contentLength = headers.getContentLength();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.putAll(super.getHeaders());
        if (contentLength > 0) {
          httpHeaders.setContentLength(contentLength);
        } else {
          httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
        }
        return httpHeaders;
      }

      @Override
      public Flux<DataBuffer> getBody() {
        return outputMessage.getBody();
      }
    };
  }

  private Mono<Void> writeDefaultLog(ServerWebExchange exchange, GatewayFilterChain chain, ApiGatewayLog apiGatewayLog) {
    return chain.filter(exchange).then(Mono.fromRunnable(() -> {
      ServerHttpResponse response = exchange.getResponse();
      int value = Objects.requireNonNull(response.getStatusCode()).value();
      apiGatewayLog.setStatusCode(value);
      long executeTime = OffsetDateTime.now().toInstant().toEpochMilli() - apiGatewayLog.getRequestStartTime().toInstant().toEpochMilli();
      apiGatewayLog.setExecutionTime(executeTime);
      ServerHttpRequest request = exchange.getRequest();
      MultiValueMap<String, String> queryParams = request.getQueryParams();
      Map<String, String> paramsMap = new HashMap<>();
      if (!CollectionUtils.isEmpty(queryParams)) {
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
          paramsMap.put(entry.getKey(), String.join(",", entry.getValue()));
        }
      }
      apiGatewayLog.setRequestParams(paramsMap);
      ApiGatewayLogInfoFactory.log(ApiGatewayLogType.DEFAULT_REQUEST, apiGatewayLog);
    }));
  }

  private Route getApiGatewayRoute(ServerWebExchange exchange) {
    return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
  }

  private ApiGatewayLog parseApiGatewayLog(ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest();
    Route apiGatewayRoute = getApiGatewayRoute(exchange);
    String ip = IpUtils.getIpAddress(request);
    return ApiGatewayLog.builder()
        .method(request.getMethodValue())
        .uri(request.getURI().toString())
        .targetServer(Objects.requireNonNull(apiGatewayRoute).getId())
        .ip(ip)
        .requestStartTime(OffsetDateTime.now())
        .build();
  }
}
