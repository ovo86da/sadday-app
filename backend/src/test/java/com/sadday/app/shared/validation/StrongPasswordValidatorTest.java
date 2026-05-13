package com.sadday.app.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrongPasswordValidator — Unit Tests")
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void valida_passwordCompleta_retornaTrue() {
        assertTrue(validator.isValid("Abc123!@#", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void valida_valorNuloOVacio_retornaTrue(String value) {
        assertTrue(validator.isValid(value, null));
    }

    @Test
    void valida_sinMinuscula_retornaFalse() {
        assertFalse(validator.isValid("ABC123!!", null));
    }

    @Test
    void valida_sinMayuscula_retornaFalse() {
        assertFalse(validator.isValid("abc123!!", null));
    }

    @Test
    void valida_sinDigito_retornaFalse() {
        assertFalse(validator.isValid("Abcdefg!!", null));
    }

    @Test
    void valida_sinSimbolo_retornaFalse() {
        assertFalse(validator.isValid("Abcdef123", null));
    }

    @Test
    void valida_soloPalabras_retornaFalse() {
        assertFalse(validator.isValid("solominusculas", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"P@ssw0rd!", "S3cur3#Pass", "C0mpl3x$1", "MyP@ss1word"})
    void valida_passwordsValidas_retornaTrue(String password) {
        assertTrue(validator.isValid(password, null));
    }

    @Test
    void valida_cortaConTodosCaracteres_retornaTrue() {
        assertTrue(validator.isValid("A1a!", null));
    }
}
