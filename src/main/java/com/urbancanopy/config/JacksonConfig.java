package com.urbancanopy.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * JSON serialisation policy.
 *
 * <p><strong>{@link BigDecimal} is serialised as a JSON string.</strong> This is a
 * deliberate correctness decision, not a stylistic one. JavaScript numbers are IEEE-754
 * doubles: a lifetime-carbon figure such as {@code 3571428.571428571} silently loses
 * precision the moment it is parsed as a number, and the loss then appears in a PDF that
 * claims to be a scientific estimate. Sending the value as a string keeps the server's
 * exact decimal intact; the browser formats it for display and only converts to a number
 * where arithmetic is genuinely needed (chart scaling), where the loss is harmless.</p>
 *
 * <p>Consumed on the client by {@code format.js}, which is the only place a figure is
 * turned into display text.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule exactDecimalModule() {
        SimpleModule module = new SimpleModule("UrbanCanopyExactDecimals");
        module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
        return module;
    }
}
