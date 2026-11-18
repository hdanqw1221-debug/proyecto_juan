package app.service;

import app.model.Simulation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** C06: la configuracion de las practicas se valida con un analizador JSON real (tipos, obligatorios, catalogo). */
class SimulationConfigValidationTest {

    private static final String VALIDA = """
            {"habilitado": true, "tema": "cavidades_equinas", "nivel": "intermedio",
             "script3d": "cavidad_toracica.py", "tiempoMinutos": 45,
             "objetivos": ["Reconocer pulmones", "Identificar \\"aorta\\", vena cava"],
             "instrucciones": ["Inicia el visor 3D"],
             "escenario": {"titulo": "Torax", "descripcion": "Explora"}}
            """;

    private static String con(String clave, String valor) {
        return VALIDA.replaceFirst("\"" + clave + "\": [^,\\n]*(,|\\n)", "\"" + clave + "\": " + valor + "$1");
    }

    @Test
    void aceptaLasPlantillasDeLosCuatroModelosYLaConfiguracionCompleta() {
        for (Visor3DService.Modelo m : Visor3DService.MODELOS) {
            assertDoesNotThrow(() -> SimulationService.validateConfigJson(SimulationService.defaultConfigJson(m.script())), m.script());
        }
        assertDoesNotThrow(() -> SimulationService.validateConfigJson(VALIDA));
        // Practica sin modelo 3D (como la practica 1 existente en la base): script3d es opcional.
        assertDoesNotThrow(() -> SimulationService.validateConfigJson(VALIDA.replace("\"script3d\": \"cavidad_toracica.py\",", "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no es json",
            "{'habilitado': true}",
            "{\"habilitado\": true,}",
            "{\"habilitado\": true} basura",
            "[1, 2]",
            "{habilitado: true}",
            "{\"habilitado\": true // comentario\n}",
    })
    void rechazaTextoQueNoEsJsonEstricto(String texto) {
        assertThrows(IllegalArgumentException.class, () -> SimulationService.validateConfigJson(texto));
    }

    @Test
    void rechazaTiposYValoresIncorrectos() {
        assertInvalid(con("habilitado", "\"true\""), "habilitado");
        assertInvalid(con("tiempoMinutos", "\"45\""), "tiempoMinutos");
        assertInvalid(con("tiempoMinutos", "0"), "tiempoMinutos");
        assertInvalid(con("tiempoMinutos", "45.5"), "tiempoMinutos");
        assertInvalid(con("tiempoMinutos", "601"), "tiempoMinutos");
        assertInvalid(con("tema", "\"  \""), "tema");
        assertInvalid(con("nivel", "3"), "nivel");
        assertInvalid(VALIDA.replace("[\"Inicia el visor 3D\"]", "[]"), "instrucciones");
        assertInvalid(VALIDA.replace("[\"Inicia el visor 3D\"]", "[\"ok\", 7]"), "instrucciones");
        assertInvalid(VALIDA.replace("\"objetivos\": [", "\"objetivos\": \"x\", \"otros\": ["), "objetivos");
        assertInvalid(VALIDA.replace("{\"titulo\": \"Torax\", \"descripcion\": \"Explora\"}", "\"Torax\""), "escenario");
    }

    @Test
    void elModeloDebeExistirEnElCatalogo() {
        assertInvalid(con("script3d", "\"sistema_oseo.py\""), "script3d");
        assertInvalid(con("script3d", "\"../../otro/cavidad_toracica.py\""), "script3d");
        assertInvalid(con("script3d", "\"C:\\\\temp\\\\cavidad_toracica.py\""), "script3d");
        assertInvalid(con("script3d", "12"), "script3d");
    }

    @Test
    void reuneTodosLosErroresEnUnMensaje() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SimulationService.validateConfigJson("{\"tema\": \"x\"}"));
        for (String k : List.of("habilitado", "nivel", "tiempoMinutos", "objetivos", "instrucciones")) {
            assertTrue(e.getMessage().contains(k), e.getMessage());
        }
    }

    /** Caso que la validacion anterior (busqueda de las claves como texto) aceptaba. */
    @Test
    void lasClavesDentroDeUnTextoNoCuentan() {
        String engano = "{\"descripcion\": \"\\\"habilitado\\\" \\\"tema\\\" \\\"nivel\\\" \\\"tiempoMinutos\\\" "
                + "\\\"objetivos\\\" \\\"instrucciones\\\"\"}";
        assertThrows(IllegalArgumentException.class, () -> SimulationService.validateConfigJson(engano));
    }

    @Test
    void losLectoresUsanElAnalizadorYToleranDatosAntiguos() {
        assertEquals(List.of("Reconocer pulmones", "Identificar \"aorta\", vena cava"), SimulationService.readArray(VALIDA, "objetivos"));
        assertEquals(45, SimulationService.readInt(VALIDA, "tiempoMinutos", 0));
        assertEquals("Torax", SimulationService.readString(VALIDA, "titulo", ""));
        assertEquals("fallback", SimulationService.readString("{roto", "tema", "fallback"));

        Simulation sim = new Simulation();
        sim.setConfigJson(VALIDA);
        assertTrue(SimulationService.isEnabled(sim));
        sim.setConfigJson(con("habilitado", "false"));
        assertFalse(SimulationService.isEnabled(sim));
        sim.setConfigJson("{\"habilitado\": \"true\"}");
        assertFalse(SimulationService.isEnabled(sim), "un texto \"true\" no habilita la practica");
        sim.setConfigJson("{\"nota\": \"\\\"habilitado\\\":true\"}");
        assertFalse(SimulationService.isEnabled(sim), "antes bastaba con que el texto apareciera en cualquier parte");
    }

    private static void assertInvalid(String json, String expectedKey) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SimulationService.validateConfigJson(json), json);
        assertTrue(e.getMessage().contains(expectedKey), "el mensaje debe nombrar '" + expectedKey + "': " + e.getMessage());
    }
}
