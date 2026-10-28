package io.github.sputter9810.shortstack.service;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ShortCodeGeneratorTests {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    void usesEightBoundedSecureRandomDrawsWithTheApprovedAlphabetOrder() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(62)).thenReturn(0, 25, 26, 51, 52, 61, 1, 27);

        assertThat(new ShortCodeGenerator(random).generate()).isEqualTo("AZaz09Bb");

        verify(random, times(8)).nextInt(62);
        verifyNoMoreInteractions(random);
    }

    @Test
    void everyAlphabetIndexProducesExactlyEightApprovedCharacters() {
        SecureRandom random = mock(SecureRandom.class);
        ShortCodeGenerator generator = new ShortCodeGenerator(random);
        for (int index = 0; index < ALPHABET.length(); index++) {
            when(random.nextInt(62)).thenReturn(index);
            assertThat(generator.generate()).isEqualTo(String.valueOf(ALPHABET.charAt(index)).repeat(8));
        }
    }

    @Test
    void productionGeneratorProducesTheApprovedFormat() {
        ShortCodeGenerator generator = new ShortCodeGenerator();
        for (int sample = 0; sample < 100; sample++) {
            assertThat(generator.generate()).matches("[A-Za-z0-9]{8}");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"actuator", "ACTUATOR", "AcTuAtOr"})
    void redrawsReservedNamesBeforeReturningACandidate(String reserved) {
        SecureRandom random = mock(SecureRandom.class);
        AtomicInteger position = new AtomicInteger();
        String draws = reserved + "AZaz09Bb";
        when(random.nextInt(62)).thenAnswer(invocation -> ALPHABET.indexOf(draws.charAt(position.getAndIncrement())));

        assertThat(new ShortCodeGenerator(random).generate()).isEqualTo("AZaz09Bb");

        verify(random, times(16)).nextInt(62);
    }
}
