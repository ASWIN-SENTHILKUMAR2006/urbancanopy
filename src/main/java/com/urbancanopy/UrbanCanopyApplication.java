package com.urbancanopy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for UrbanCanopy — a decision-support system that estimates the annual
 * carbon sequestration, oxygen generation, air-pollutant removal and canopy coverage of
 * a planned urban tree plantation.
 *
 * <p>The application performs <em>deterministic estimation</em>, not prediction: every
 * figure it reports is the arithmetic product of a user-supplied quantity and a curated
 * scientific reference value held in the database. There is no model, no inference and
 * no external data source.</p>
 *
 * @see com.urbancanopy.calculation.EnvironmentalCalculationEngine
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UrbanCanopyApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrbanCanopyApplication.class, args);
    }
}
