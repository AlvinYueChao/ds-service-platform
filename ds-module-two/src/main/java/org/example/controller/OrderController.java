package org.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/order")
public class OrderController {

  @GetMapping("/getOrderById")
  public Mono<String> getOrderById(@RequestParam("orderId") Long orderId) {
    return Mono.just("OK --- " + orderId);
  }
}
