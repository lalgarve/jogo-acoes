package dev.leilaalgarve.layerexamples;

import org.springframework.stereotype.Service;

/** Allowed: a service calls a repository. */
@Service
public class ExampleService {

    final ExampleRepository repository;

    public ExampleService(ExampleRepository repository) {
        this.repository = repository;
    }
}
