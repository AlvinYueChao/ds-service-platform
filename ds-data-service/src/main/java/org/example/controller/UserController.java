package org.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user")
public class UserController {

  @GetMapping("/getCurrentUser")
  public Mono<String> getCurrentUser() {
    return Mono.just("OK");
  }
}
