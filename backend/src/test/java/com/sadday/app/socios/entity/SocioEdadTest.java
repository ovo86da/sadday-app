package com.sadday.app.socios.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Edad y antigüedad del socio.
 *
 * <p>Estos casos no se podían escribir antes: ambos métodos leían
 * {@code LocalDate.now()}, así que el resultado dependía del día en que se
 * corrieran los tests. La sobrecarga con fecha es lo que los hace posibles.
 */
class SocioEdadTest {

    private static Socio nacidoEl(LocalDate fecha) {
        Socio s = new Socio();
        s.setFechaNacimiento(fecha);
        return s;
    }

    private static Socio ingresadoEl(LocalDate fecha) {
        Socio s = new Socio();
        s.setFechaIngreso(fecha);
        return s;
    }

    @Nested
    @DisplayName("Regresión: el día del año se desplaza en los bisiestos")
    class Bisiestos {

        /**
         * La fórmula anterior restaba {@code getDayOfYear()}. Para quien nació
         * en año bisiesto a partir del 1 de marzo, su día del año es uno mayor
         * que el de la misma fecha en un año normal, así que la resta le
         * quitaba un año de más. Estos tres casos daban 73, 69 y 73.
         */
        @ParameterizedTest(name = "nacido {0}, el {1} tiene {2} años")
        @CsvSource({
            "1952-03-01, 2026-03-01, 74",
            "1956-06-15, 2026-06-15, 70",
            "1952-12-31, 2026-12-31, 74",
            "2000-03-01, 2026-03-01, 26",
        })
        void edadCorrectaEnCumpleanosDeNacidoEnBisiesto(LocalDate nac, LocalDate hoy, int esperada) {
            assertThat(nacidoEl(nac).calcularEdad(hoy)).isEqualTo(esperada);
        }

        @Test
        @DisplayName("la antigüedad tenía el mismo fallo")
        void antiguedadCorrectaEnBisiesto() {
            assertThat(ingresadoEl(LocalDate.of(2012, 5, 20)).calcularAntiguedad(LocalDate.of(2026, 5, 20)))
                    .isEqualTo(14);
        }
    }

    @Nested
    @DisplayName("Límites alrededor del cumpleaños")
    class Limites {

        @ParameterizedTest(name = "el {1} tiene {2}")
        @CsvSource({
            "1990-07-15, 2026-07-14, 35",   // el día antes: aún no cumple
            "1990-07-15, 2026-07-15, 36",   // el día exacto: ya cumplió
            "1990-07-15, 2026-07-16, 36",   // el día después
        })
        void cambiaExactamenteElDiaDelCumpleanos(LocalDate nac, LocalDate hoy, int esperada) {
            assertThat(nacidoEl(nac).calcularEdad(hoy)).isEqualTo(esperada);
        }

        @Test
        @DisplayName("nacido un 29 de febrero, en año no bisiesto cumple el 1 de marzo")
        void veintinueveDeFebrero() {
            Socio s = nacidoEl(LocalDate.of(2000, 2, 29));
            assertThat(s.calcularEdad(LocalDate.of(2026, 2, 28))).isEqualTo(25);
            assertThat(s.calcularEdad(LocalDate.of(2026, 3, 1))).isEqualTo(26);
        }

        @Test
        @DisplayName("el mismo día de nacer tiene 0 años")
        void recienNacido() {
            LocalDate hoy = LocalDate.of(2026, 10, 11);
            assertThat(nacidoEl(hoy).calcularEdad(hoy)).isZero();
        }
    }

    @Nested
    @DisplayName("Datos ausentes")
    class SinDatos {

        @Test
        @DisplayName("sin fecha de nacimiento la edad es null, no 0")
        void edadNulaSinFecha() {
            assertThat(new Socio().calcularEdad(LocalDate.of(2026, 10, 11))).isNull();
        }

        @Test
        @DisplayName("sin fecha de ingreso la antigüedad es 0")
        void antiguedadCeroSinFecha() {
            assertThat(new Socio().calcularAntiguedad(LocalDate.of(2026, 10, 11))).isZero();
        }
    }

    @Test
    @DisplayName("la sobrecarga sin fecha delega en la de hoy")
    void sinArgumentoUsaHoy() {
        Socio s = nacidoEl(LocalDate.now().minusYears(30));
        assertThat(s.calcularEdad()).isEqualTo(s.calcularEdad(LocalDate.now())).isEqualTo(30);
    }
}
