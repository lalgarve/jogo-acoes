package dev.leilaalgarve.jogoacoes.emailservice;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "dev.leilaalgarve.jogoacoes.emailservice")
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "not @requires-real-ses")
public class RunCucumberTest {
}
