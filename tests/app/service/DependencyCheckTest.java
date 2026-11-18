package app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** C10: comprobacion de las versiones de VTK y PyQt5 contra requeriments.txt. */
class DependencyCheckTest {
    private static final List<String> REQ = List.of(
            "PyQt5==5.15.11",
            "vtk==9.4.1; python_version < \"3.14\"",
            "vtk>=9.6.2,<10; python_version >= \"3.14\"",
            "psycopg[binary]>=3.2",
            "numpy>=1.24");

    @Test
    void elMarcadorDePythonSeleccionaElRequisitoDeVtk() {
        assertEquals("==9.4.1", DependencyCheck.parseRequirements(REQ, "3.12.7").get("vtk"));
        assertEquals(">=9.6.2,<10", DependencyCheck.parseRequirements(REQ, "3.14.4").get("vtk"));
        Map<String, String> r = DependencyCheck.parseRequirements(REQ, "3.14.4");
        assertEquals("==5.15.11", r.get("pyqt5"));
        assertEquals(">=3.2", r.get("psycopg"), "los extras [binary] se ignoran");
    }

    @Test
    void versionesCorrectasNoDanProblemas() {
        var req = DependencyCheck.parseRequirements(REQ, "3.14.4");
        assertEquals(List.of(), DependencyCheck.check(req, Map.of("PyQt5", "5.15.11", "vtk", "9.6.2")));
        assertEquals(List.of(), DependencyCheck.check(req, Map.of("PyQt5", "5.15.11", "vtk", "9.9.0")));
        var req312 = DependencyCheck.parseRequirements(REQ, "3.12.7");
        assertEquals(List.of(), DependencyCheck.check(req312, Map.of("PyQt5", "5.15.11", "vtk", "9.4.1")));
    }

    @Test
    void versionesIncorrectasSimuladasDanUnMensajeClaro() {
        var req = DependencyCheck.parseRequirements(REQ, "3.14.4");
        List<String> p = DependencyCheck.check(req, Map.of("PyQt5", "5.15.14", "vtk", "10.0.0"));
        assertEquals(2, p.size());
        assertTrue(p.get(0).contains("PyQt5 5.15.14") && p.get(0).contains("==5.15.11"), p.get(0));
        assertTrue(p.get(1).contains("vtk 10.0.0") && p.get(1).contains(">=9.6.2"), p.get(1));
        assertEquals(1, DependencyCheck.check(req, Map.of("PyQt5", "5.15.11", "vtk", "9.6.1")).size(), "vtk demasiado antiguo");
        // Un paquete ausente se nombra como no instalado.
        List<String> falta = DependencyCheck.check(req, Map.of("PyQt5", "5.15.11"));
        assertEquals(1, falta.size());
        assertTrue(falta.get(0).contains("vtk no esta instalado"), falta.get(0));
        String msg = DependencyCheck.message(p, "requeriments.txt");
        assertTrue(msg.contains("pip install -r requeriments.txt") && msg.contains("PyQt5 5.15.14"), msg);
    }

    @Test
    void comparaVersionesNumericamente() {
        assertTrue(DependencyCheck.satisfies("9.10.0", ">=9.6.2,<10"), "9.10 es mayor que 9.6");
        assertFalse(DependencyCheck.satisfies("9.6.2rc1", "==9.6.3"));
        assertTrue(DependencyCheck.satisfies("5.15.11", "==5.15.11"));
        assertTrue(DependencyCheck.satisfies("5.15", "==5.15.0"));
        assertTrue(DependencyCheck.satisfies("1.0", ""), "sin condicion cualquier version vale");
    }

    @Test
    void leeLaSalidaDelSondeoYIgnoraLoQueNoEsDelSondeo() {
        Map<String, String> v = DependencyCheck.parseProbeOutput("Warning: ruido de VTK\nVERSIONES:py=3.14.4;PyQt5=5.15.11;vtk=NO\n");
        assertEquals(Map.of("py", "3.14.4", "PyQt5", "5.15.11"), v);
        assertTrue(DependencyCheck.parseProbeOutput("nada util").isEmpty());
    }

    @Test
    void elRequeriments_txtDelProyectoSeInterpretaParaPython314() throws Exception {
        Path file = Path.of("aplicacion_tres_d/aplicacion_tres_d/requeriments.txt");
        assumeTrue(Files.isRegularFile(file));
        var req = DependencyCheck.parseRequirements(Files.readAllLines(file), "3.14.4");
        assertEquals("==5.15.11", req.get("pyqt5"));
        assertTrue(req.get("vtk").contains(">=9.6.2"));
    }

    /** Con el Python real del proyecto: sus versiones cumplen el archivo y una exigencia falsa se detecta. */
    @Test
    void comprobacionRealConElPythonDelProyecto(@TempDir Path tmp) throws Exception {
        Path python = Path.of("aplicacion_tres_d/aplicacion_tres_d/.venv/Scripts/python.exe").toAbsolutePath();
        assumeTrue(Files.isRegularFile(python), "no hay entorno .venv en este equipo");
        Path carpeta = Path.of("aplicacion_tres_d/aplicacion_tres_d").toAbsolutePath();
        assertNull(Visor3DService.comprobarVersiones(List.of(python.toString()), carpeta));

        Files.writeString(tmp.resolve("requeriments.txt"), "PyQt5==1.0.0\nvtk==1.0.0\n");
        String msg = Visor3DService.comprobarVersiones(List.of(python.toString()), tmp);
        assertNotNull(msg);
        assertTrue(msg.contains("PyQt5 5.15.11 no cumple ==1.0.0"), msg);
        assertTrue(msg.contains("vtk "), msg);
    }
}
