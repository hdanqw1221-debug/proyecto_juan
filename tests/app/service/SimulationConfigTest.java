package app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigTest {
    @Test
    void visualEditorPreservesAdditionalSettingsAndEscapesText() {
        String original = """
                {"habilitado":true,"tema":"anatomia","nivel":"avanzado",
                 "script3d":"cavidad_toracica.py","tiempoMinutos":45,
                 "objetivos":["Ver corazon, pulmones","Identificar \\"aorta\\""],
                 "instrucciones":["Abrir visor"],
                 "escenario":{"titulo":"Torax","descripcion":"Explora la cavidad","nota":"conservar"},
                 "opcionesExtra":{"camara":{"zoom":2},"etiquetas":[1,2]}}
                """;
        SimulationConfig settings = SimulationConfig.fromJson(original);
        assertEquals(List.of("Ver corazon, pulmones", "Identificar \"aorta\""), settings.strings("objetivos"));
        settings.putStrings("instrucciones", List.of("Haz clic en \"corazon\"", "Observa la region\nventral"));
        settings.putScenario("Torax equino", "Incluye vasos, pulmones y corazon");
        settings.putInt("tiempoMinutos", 60);

        SimulationConfig saved = SimulationConfig.fromJson(settings.toJson());
        assertEquals(60, saved.integer("tiempoMinutos", 0));
        assertEquals(List.of("Haz clic en \"corazon\"", "Observa la region\nventral"),
                saved.strings("instrucciones"));
        assertEquals("Torax equino", saved.scenario("titulo", ""));
        assertTrue(saved.toJson().contains("\"nota\": \"conservar\""));
        assertTrue(saved.toJson().contains("\"opcionesExtra\": {\"camara\":{\"zoom\":2},\"etiquetas\":[1,2]}"));
    }

    @Test
    void rejectsIncompleteJson() {
        assertThrows(IllegalArgumentException.class, () -> SimulationConfig.fromJson("{\"objetivos\": [\"uno\""));
    }
}
