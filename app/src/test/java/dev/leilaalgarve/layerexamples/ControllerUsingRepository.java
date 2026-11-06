package dev.leilaalgarve.layerexamples;

import org.springframework.web.bind.annotation.RestController;

/** Breaks controllersDoNotUseRepositories. */
@RestController
public class ControllerUsingRepository {

    final ExampleRepository repository;

    public ControllerUsingRepository(ExampleRepository repository) {
        this.repository = repository;
    }
}
