package dev.leilaalgarve.jogoacoes.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Spec 05-011: logs, at DEBUG, the entry (method + arguments) and exit (return value, or
 * exception) of every method of every {@code @RestController} -- {@code @within} matches the
 * class-level annotation, so a module gaining a new controller needs no update here.
 */
@Aspect
@Component
public class ControllerLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ControllerLoggingAspect.class);

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!logger.isDebugEnabled()) {
            return joinPoint.proceed();
        }
        // Not toShortString() -- it already embeds "(..)"/"()" itself, which doubled up with
        // the "(args)" this appends (confirmed by running it, not obvious from the AspectJ docs).
        String signature = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        logger.debug("--> {}({})", signature, formatArgs(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            logger.debug("<-- {} returned {}", signature, LogFormatter.summarize(bodyOf(result)));
            return result;
        } catch (Throwable ex) {
            logger.debug("<-- {} threw {}", signature, ex);
            throw ex;
        }
    }

    private String formatArgs(Object[] args) {
        return Arrays.stream(args).map(LogFormatter::summarize).collect(Collectors.joining(", "));
    }

    // Every controller here returns ResponseEntity<T> -- logging the wrapper itself would hide
    // T (e.g. a List) behind one opaque object, so truncation (LogFormatter) never gets a
    // chance to apply to what's actually the interesting part of the response.
    private Object bodyOf(Object result) {
        return result instanceof ResponseEntity<?> response ? response.getBody() : result;
    }
}
