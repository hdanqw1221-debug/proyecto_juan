package app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class Visor3DServiceTest {

    @Test
    public void testCatalogoTieneLosCuatroVisores() {
        assertEquals(List.of("cavidad_toracica.py", "cavidad_abdominal.py", "region_inguinal.py", "cavidades_equinas.py"),
                Visor3DService.MODELOS.stream().map(Visor3DService.Modelo::script).toList());
        assertEquals("Region inguinal", Visor3DService.titulo("REGION_INGUINAL.py"));
        assertEquals("Cavidad toracica", Visor3DService.titulo("aplicacion_tres_d/cavidad_toracica.py"));
        assertEquals("Otro modelo", Visor3DService.titulo("otro_modelo.py"));
        assertNull(Visor3DService.modelo("sistema_oseo.py"));
    }

    @Test
    public void testDisponiblesExigeLaEscenaCompartida(@TempDir Path carpeta) throws Exception {
        Files.writeString(carpeta.resolve("region_inguinal.py"), "");
        Files.writeString(carpeta.resolve("cavidad_toracica.py"), "");
        Files.writeString(carpeta.resolve("sistema_oseo.py"), "");
        assertTrue(Visor3DService.disponibles(carpeta).isEmpty());

        Path manifiesto = carpeta.resolve(Visor3DService.MANIFIESTO);
        Files.createDirectories(manifiesto.getParent());
        Files.writeString(manifiesto, "{}");
        assertEquals(List.of("cavidad_toracica.py", "region_inguinal.py"),
                Visor3DService.disponibles(carpeta).stream().map(Visor3DService.Modelo::script).toList());
        assertTrue(Visor3DService.disponibles(null).isEmpty());
    }

    @Test
    public void testVisorSinConsola(@TempDir Path carpeta) throws Exception {
        Path python = Files.writeString(carpeta.resolve("python.exe"), "");
        assertEquals(List.of(python.toString(), "-X"), Visor3DService.sinConsola(List.of(python.toString(), "-X")));
        Path pythonw = Files.writeString(carpeta.resolve("pythonw.exe"), "");
        assertEquals(List.of(pythonw.toString(), "-X"), Visor3DService.sinConsola(List.of(python.toString(), "-X")));
        assertEquals(List.of("py", "-3"), Visor3DService.sinConsola(List.of("py", "-3")));
    }

    @Test
    public void testLeeLosEventosDelVisor() {
        String linea = "{\"evento\": \"seleccion\", \"t\": 1760000000.5, \"objeto_id\": \"ING_TESTICULO_IZQ\", "
                + "\"ficha_id\": \"ING_TESTICULO\", \"origen\": \"clic\", \"capa\": 1, \"modelo\": \"inguinal\"}";
        assertEquals("seleccion", Visor3DService.campo(linea, "evento"));
        assertEquals("ING_TESTICULO", Visor3DService.campo(linea, "ficha_id"));
        assertNull(Visor3DService.campo(linea, "no_existe"));
    }

    @Test
    public void testEjecutarRecogeLaSesion(@TempDir Path carpeta) throws Exception {
        List<String> python = List.of("python");
        assumeTrue(Visor3DService.ejecutaCodigo(python, "pass"), "Python no disponible en este equipo");
        Path script = carpeta.resolve("visor_falso.py");
        Files.writeString(script, String.join("\n",
                "import json",
                "def emitir(evento, **datos):",
                "    print(json.dumps({'evento': evento, **datos}), flush=True)",
                "emitir('inicio', modelo='inguinal', objetos=25)",
                "print('aviso de VTK')",
                "emitir('seleccion', objeto_id='ING_TESTICULO_IZQ', ficha_id='ING_TESTICULO')",
                "emitir('seleccion', objeto_id='ING_TESTICULO_DER', ficha_id='ING_TESTICULO')",
                "emitir('seleccion', objeto_id='ING_EPIDIDIMO_IZQ', ficha_id='ING_EPIDIDIMO')",
                "emitir('cierre', modelo='inguinal', duracion_s=1.0)",
                ""));
        AtomicBoolean abierto = new AtomicBoolean(false);
        Visor3DService.Resultado resultado = Visor3DService.ejecutar(script, python, () -> abierto.set(true));
        assertTrue(resultado.correcto(), resultado.salida());
        assertTrue(abierto.get());
        assertEquals(List.of("ING_TESTICULO", "ING_EPIDIDIMO"), List.copyOf(resultado.estructuras()));
        assertEquals("aviso de VTK", resultado.salida().strip());
        String resumen = Visor3DService.resumen(script, resultado);
        assertTrue(resumen.contains("2 estructura(s) consultada(s)"), resumen);
        assertTrue(resumen.contains("ING_TESTICULO, ING_EPIDIDIMO"), resumen);
    }

    @Test
    public void testFalloAlLiberarTrasElCierreNoEsUnError(@TempDir Path carpeta) throws Exception {
        List<String> python = List.of("python");
        assumeTrue(Visor3DService.ejecutaCodigo(python, "pass"), "Python no disponible en este equipo");
        Path cierra = Files.writeString(carpeta.resolve("cierra.py"), String.join("\n",
                "print('{\"evento\": \"inicio\"}', flush=True)",
                "print('{\"evento\": \"cierre\"}', flush=True)",
                "raise SystemExit(3)", ""));
        assertTrue(Visor3DService.ejecutar(cierra, python, null).correcto());
        Path falla = Files.writeString(carpeta.resolve("falla.py"), String.join("\n",
                "print('{\"evento\": \"inicio\"}', flush=True)",
                "raise RuntimeError('sin OpenGL')", ""));
        Visor3DService.Resultado resultado = Visor3DService.ejecutar(falla, python, null);
        assertFalse(resultado.correcto());
        assertTrue(resultado.abierto() && !resultado.cerrado());
        assertTrue(resultado.salida().contains("sin OpenGL"), resultado.salida());
    }

    @Test
    public void testPlantillaPorModelo() {
        for (Visor3DService.Modelo modelo : Visor3DService.MODELOS) {
            String json = SimulationService.defaultConfigJson(modelo.script());
            SimulationService.validateConfigJson(json);
            assertEquals(modelo.script(), SimulationService.readString(json, "script3d", ""));
            assertEquals(modelo.titulo(), SimulationService.readString(json, "titulo", ""));
            List<String> objetivos = SimulationService.readArray(json, "objetivos");
            assertEquals(2, objetivos.size());
            assertEquals(modelo.objetivo(), objetivos.get(0));
            assertEquals(3, SimulationService.readArray(json, "instrucciones").size());
        }
        assertEquals(SimulationService.defaultConfigJson("cavidad_toracica.py"), SimulationService.defaultConfigJson());
    }
}
