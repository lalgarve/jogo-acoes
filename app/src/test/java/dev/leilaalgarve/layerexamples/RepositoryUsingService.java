package dev.leilaalgarve.layerexamples;

import org.springframework.stereotype.Repository;

/** Breaks repositoriesDoNotDependOnServicesOrControllers. */
@Repository
public class RepositoryUsingService {

    final ExampleService service;

    public RepositoryUsingService(ExampleService service) {
        this.service = service;
    }
}
