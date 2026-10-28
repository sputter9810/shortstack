package io.github.sputter9810.shortstack.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random;

    public ShortCodeGenerator() {
        this(new SecureRandom());
    }

    ShortCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        String code;
        do {
            StringBuilder candidate = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                candidate.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = candidate.toString();
            // Only "actuator" among the reserved names fits eight Base62 characters.
        } while (code.equalsIgnoreCase("actuator"));
        return code;
    }
}
