package dev.leilaalgarve.jogoacoes.blackboxproxy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one control endpoint of this proxy (spec 05-020) -- reads/writes the device profile every
 * other request gets, without touching the forwarded call's own body/headers. Two methods
 * because reading and writing are different operations with different contracts ({@code GET} is
 * side-effect-free, {@code POST} replaces state) -- not because either one alone couldn't do it.
 */
@RestController
public class DeviceHeaderController {

    private final DeviceHeaderStore store;

    public DeviceHeaderController(DeviceHeaderStore store) {
        this.store = store;
    }

    @GetMapping("/blackbox/proxy/headers")
    public DeviceHeaders read() {
        return store.get();
    }

    @PostMapping("/blackbox/proxy/headers")
    public ResponseEntity<Void> write(@RequestBody(required = false) DeviceHeaders headers) {
        store.set(headers);
        return ResponseEntity.noContent().build();
    }
}
