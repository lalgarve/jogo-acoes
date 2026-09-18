package dev.leilaalgarve.jogoacoes.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Spec 05-011: logs, at DEBUG, every call to a Spring Data repository method -- reads and
 * writes both go through these (`save`, `findBy*`, `delete*`, ...), so one pointcut covers
 * both without naming each repository.
 *
 * <p>No {@code within(dev.leilaalgarve.jogoacoes..*)} filter, despite plan.md's original
 * intent to exclude Spring Data's own infrastructure with it: confirmed by running the tests
 * that it never matches at all here, because a Spring Data repository bean is a plain JDK
 * dynamic proxy, not a class in this project's packages -- {@code within()} matches on where
 * the executing code is actually defined, and that's Spring Data's synthetic proxy class. It
 * turns out to be unnecessary anyway: the only {@code JpaRepository}-typed beans in this
 * application's context are the ones this project declares.
 */
@Aspect
@Component
public class RepositoryLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryLoggingAspect.class);

    @Around("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!logger.isDebugEnabled()) {
            return joinPoint.proceed();
        }
        String label = repositoryInterfaceName(joinPoint) + "." + joinPoint.getSignature().getName();
        logger.debug("--> {}({})", label, formatArgs(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            logger.debug("<-- {} returned {}", label, LogFormatter.summarize(result));
            return result;
        } catch (Throwable ex) {
            logger.debug("<-- {} threw {}", label, ex);
            throw ex;
        }
    }

    // AspectJ resolves the pointcut's own signature against JpaRepository's *declaring*
    // super-interface (e.g. ListCrudRepository.findAll, not CompetitionRepository.findAll) --
    // every repository's findAll() would then look identical in the log. The proxy's actual
    // interfaces (available on the target, not the matched signature) still carry the real one.
    private String repositoryInterfaceName(ProceedingJoinPoint joinPoint) {
        for (Class<?> candidate : joinPoint.getTarget().getClass().getInterfaces()) {
            if (Repository.class.isAssignableFrom(candidate)) {
                return candidate.getSimpleName();
            }
        }
        return joinPoint.getSignature().getDeclaringType().getSimpleName();
    }

    private String formatArgs(Object[] args) {
        return Arrays.stream(args).map(LogFormatter::summarize).collect(Collectors.joining(", "));
    }
}
