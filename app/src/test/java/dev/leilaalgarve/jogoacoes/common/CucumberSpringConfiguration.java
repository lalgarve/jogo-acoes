package dev.leilaalgarve.jogoacoes.common;

import dev.leilaalgarve.jogoacoes.common.testsupport.SwitchableEmailSender;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SwitchableEmailSender.class)
public class CucumberSpringConfiguration {
}
