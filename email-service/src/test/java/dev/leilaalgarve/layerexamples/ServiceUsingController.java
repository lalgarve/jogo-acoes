package dev.leilaalgarve.layerexamples;

import org.springframework.stereotype.Service;

/** Breaks nothingDependsOnControllers. */
@Service
public class ServiceUsingController {

    void call(ExampleController controller) {
        controller.toString();
    }
}
