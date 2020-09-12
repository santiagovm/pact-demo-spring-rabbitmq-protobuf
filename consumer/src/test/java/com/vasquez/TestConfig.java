package com.vasquez;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestConfig {

    BeerVerificationServiceMock beerVerificationServiceMock = new BeerVerificationServiceMock();

    @Bean
    public BeerVerificationListener createBeerVerificationListener() {
        return new BeerVerificationListener(beerVerificationServiceMock);
    }

    @Bean
    public BeerVerificationServiceMock createBeerVerificationServiceMock() {
        return beerVerificationServiceMock;
    }
}
