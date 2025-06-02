package com.sav.springchatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper customObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Personnalisez votre mapper ici
        // Enregistre le module JavaTime pour LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());
        // Evite les erreurs de sérialisation par défaut
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return objectMapper;
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper customObjectMapper) {
        // Utilisation du constructeur au lieu de setObjectMapper()
        return new MappingJackson2HttpMessageConverter(customObjectMapper);
    }
}