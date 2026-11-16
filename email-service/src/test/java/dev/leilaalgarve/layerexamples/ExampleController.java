package dev.leilaalgarve.layerexamples;

import org.springframework.web.bind.annotation.RestController;

/** Allowed: a controller calls a service. */
@RestController
public class ExampleController {

    final ExampleService service;

    public ExampleController(ExampleService service) {
        this.service = service;
    }
}
