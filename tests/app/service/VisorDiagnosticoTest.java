package app.service;

import app.service.VisorDiagnostico.Causa;
import app.service.VisorDiagnostico.Diagnostico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** C13 (PF-22): el mensaje de error del visor distingue la causa en lugar de culpar siempre a los graficos. */
class VisorDiagnosticoTest {

    @Test
    void pythonNoEncontrado() {
        Diagnostico d = VisorDiagnostico.sinEntornoPython(false, null);
        assertEquals(Causa.PYTHON_NO_ENCONTRADO, d.causa());
        assertTrue(d.mensaje().contains("No se encontro Python"), d.mensaje());
        assertFalse(d.mensaje().toLowerCase().contains("grafic"), d.mensaje());
    }

    @Test
    void hayPythonPeroFaltanBibliotecas() {
        Diagnostico d = VisorDiagnostico.sinEntornoPython(true, "vtk");
        assertEquals(Causa.DEPENDENCIAS_FALTANTES, d.causa());
        assertTrue(d.mensaje().contains("(vtk)") && d.mensaje().contains("pip install -r requeriments.txt"), d.mensaje());
        assertEquals(Causa.DEPENDENCIAS_FALTANTES, VisorDiagnostico.sinEntornoPython(true, null).causa());
    }

    @Test
    void importErrorEnLaSalidaDelVisorEsUnaDependenciaFaltante() {
        String salida = "Traceback (most recent call last):\n  File \"visor.py\", line 21, in <module>\n"
                + "ModuleNotFoundError: No module named 'vtkmodules'";
        Diagnostico d = VisorDiagnostico.deEjecucion(1, salida, false, false, null, null);
        assertEquals(Causa.DEPENDENCIAS_FALTANTES, d.causa());
        assertTrue(d.mensaje().contains("«vtkmodules»"), d.mensaje());
        assertFalse(d.mensaje().toLowerCase().contains("grafic"));
    }

    @Test
    void archivosDelModeloAusentes(@TempDir Path carpeta) throws Exception {
        Path script = Files.writeString(carpeta.resolve("cavidad_toracica.py"), "");
        List<String> faltan = VisorDiagnostico.archivosAusentes(script, carpeta);
        assertTrue(faltan.contains(Visor3DService.MANIFIESTO), faltan.toString());
        assertTrue(faltan.contains("cavidades/modelos/obj"));
        assertTrue(faltan.contains("cavidades/datos/fichas_anatomicas.json"));
        assertFalse(faltan.contains("cavidad_toracica.py"));
        Diagnostico d = VisorDiagnostico.archivosAusentes(faltan);
        assertEquals(Causa.ARCHIVOS_AUSENTES, d.causa());
        assertTrue(d.mensaje().contains("manifiesto.json"), d.mensaje());

        // Completo: no falta nada.
        Files.createDirectories(carpeta.resolve("cavidades/modelos/obj"));
        Files.writeString(carpeta.resolve(Visor3DService.MANIFIESTO), "{}");
        Files.createDirectories(carpeta.resolve("cavidades/datos"));
        Files.writeString(carpeta.resolve("cavidades/datos/fichas_anatomicas.json"), "[]");
        assertEquals(List.of(), VisorDiagnostico.archivosAusentes(script, carpeta));
        // El propio script ausente tambien se informa.
        assertEquals(List.of("otro.py"), VisorDiagnostico.archivosAusentes(carpeta.resolve("otro.py"), carpeta));
    }

    @Test
    void versionesIncorrectas() {
        String msg = DependencyCheck.message(List.of("vtk 10.0.0 no cumple >=9.6.2 y <10"), "requeriments.txt");
        Diagnostico d = VisorDiagnostico.deEjecucion(-1, msg, false, false, new IllegalStateException(msg), null);
        assertEquals(Causa.VERSIONES_INCORRECTAS, d.causa());
        assertTrue(d.mensaje().contains("vtk 10.0.0"));
    }

    @Test
    void cierreAnormalConCodigoDeSalida() {
        Diagnostico d = VisorDiagnostico.deEjecucion(3, "Segmentation\nfallo raro", true, false, null, null);
        assertEquals(Causa.CIERRE_ANORMAL, d.causa());
        assertTrue(d.mensaje().contains("se cerro de forma inesperada durante la sesion"), d.mensaje());
        assertTrue(d.mensaje().contains("codigo de salida 3"), d.mensaje());
        assertTrue(d.mensaje().contains("fallo raro"), "incluye las ultimas lineas del visor");
        assertFalse(d.mensaje().contains("graficos"), "un codigo 3 sin pistas graficas no culpa a los graficos: " + d.mensaje());
    }

    @Test
    void noLlegoAAbrirLaVentana() {
        Diagnostico d = VisorDiagnostico.deEjecucion(1, "", false, false, null, null);
        assertTrue(d.mensaje().contains("no llego a abrir su ventana"), d.mensaje());
        assertEquals("El visor no se abrio", d.titulo());
    }

    @Test
    void violacionDeAccesoYErroresDeOpenGlSiMencionanLosGraficos() {
        Diagnostico d = VisorDiagnostico.deEjecucion((int) 0xC0000005L, "", true, false, null, null);
        assertTrue(d.mensaje().contains("0xC0000005") && d.mensaje().contains("violacion de acceso"), d.mensaje());
        assertTrue(d.mensaje().contains("graficos"), d.mensaje());
        Diagnostico gl = VisorDiagnostico.deEjecucion(1, "vtkWin32OpenGLRenderWindow: failed to create OpenGL context", false, false, null, null);
        assertTrue(gl.mensaje().contains("graficos"), gl.mensaje());
    }

    @Test
    void errorAlLanzarElProceso() {
        Diagnostico d = VisorDiagnostico.deEjecucion(-1, "", false, false, new java.io.IOException("Access is denied"), null);
        assertEquals(Causa.ERROR_AL_LANZAR, d.causa());
        assertTrue(d.mensaje().contains("Access is denied"), d.mensaje());
    }

    @Test
    void elMensajeIncluyeLaRutaDelRegistroDeFallos() {
        Diagnostico d = VisorDiagnostico.deEjecucion(1, "x", true, false, null, Path.of("registros"));
        assertTrue(d.mensaje().contains("visor_<modelo>.log"), d.mensaje());
    }
}
