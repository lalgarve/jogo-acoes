package dev.leilaalgarve.jogoacoes.blackboxproxy;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory, single-process state (spec 05-020) -- never persisted, never shared between
 * instances (that's what running more than one {@code blackbox-proxy} instance is for, see
 * {@code scripts/blackbox-proxy.sh}). {@link #set} always replaces the whole configuration, no
 * merge with whatever was set before -- each {@code POST /blackbox/proxy/headers} describes the
 * simulated device from scratch, so "field left out of this call" and "field explicitly
 * cleared" are the same thing: blank.
 */
@Component
public class DeviceHeaderStore {

    private final AtomicReference<DeviceHeaders> current = new AtomicReference<>(DeviceHeaders.BLANK);

    public DeviceHeaders get() {
        return current.get();
    }

    public void set(DeviceHeaders headers) {
        current.set(headers == null ? DeviceHeaders.BLANK : headers);
    }
}
