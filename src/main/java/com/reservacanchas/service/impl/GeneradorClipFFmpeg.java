package com.reservacanchas.service.impl;

import com.reservacanchas.config.ClockConfig;
import com.reservacanchas.model.Clip;
import com.reservacanchas.model.Grabacion;
import com.reservacanchas.model.enums.EstadoClip;
import com.reservacanchas.service.IAlmacenamiento;
import com.reservacanchas.service.IGeneradorClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class GeneradorClipFFmpeg implements IGeneradorClip {

    private static final Logger log = LoggerFactory.getLogger(GeneradorClipFFmpeg.class);

    private final IAlmacenamiento almacenamiento;
    private final String rutaFFmpeg;
    private final boolean simulado;
    private final int defaultSegundosAntes;
    private final int defaultSegundosDespues;

    public GeneradorClipFFmpeg(
            IAlmacenamiento almacenamiento,
            @Value("${clips.ffmpeg.ruta:ffmpeg}") String rutaFFmpeg,
            @Value("${clips.ffmpeg.simulado:true}") boolean simulado,
            @Value("${clips.segundos-antes:15}") int defaultSegundosAntes,
            @Value("${clips.segundos-despues:8}") int defaultSegundosDespues) {
        this.almacenamiento = almacenamiento;
        this.rutaFFmpeg = rutaFFmpeg;
        this.simulado = simulado;
        this.defaultSegundosAntes = defaultSegundosAntes;
        this.defaultSegundosDespues = defaultSegundosDespues;
    }

    public String getRutaFFmpeg() {
        return rutaFFmpeg;
    }

    public boolean isSimulado() {
        return simulado;
    }

    @Override
    public Clip generar(Grabacion g, LocalDateTime instante, int antes, int despues) {
        int segAntes = antes > 0 ? antes : defaultSegundosAntes;
        int segDespues = despues > 0 ? despues : defaultSegundosDespues;

        Clip clip = inicializarClip(g, instante, segAntes, segDespues);

        if (simulado) {
            generarSimulado(g, instante, clip);
        } else {
            generarReal(g, instante, segAntes, segDespues, clip);
        }

        return clip;
    }

    private Clip inicializarClip(Grabacion g, LocalDateTime instante, int segAntes, int segDespues) {
        Clip clip = new Clip();
        clip.setGrabacion(g);
        if (g != null) {
            clip.setPartido(g.getPartido());
        }
        clip.setInstante(instante);
        clip.setSegundosAntes(segAntes);
        clip.setSegundosDespues(segDespues);
        clip.setDuracionSegundos(segAntes + segDespues);
        clip.setEstado(EstadoClip.PROCESANDO);
        clip.setVisible(true);
        clip.setVistas(0);
        return clip;
    }

    private void generarSimulado(Grabacion g, LocalDateTime instante, Clip clip) {
        Path dummyVideoPath = null;
        Path dummyThumbPath = null;
        try {
            dummyVideoPath = Files.createTempFile("clip_simulado_", ".mp4");
            Files.writeString(dummyVideoPath, "SIMULATED_CLIP_VIDEO_DATA_" + (g != null ? g.getId() : "0") + "_" + instante);
            dummyThumbPath = Files.createTempFile("thumb_simulado_", ".jpg");
            Files.writeString(dummyThumbPath, "SIMULATED_THUMBNAIL_DATA");

            String urlVideo = almacenamiento.subir(dummyVideoPath.toFile());
            String urlThumb = almacenamiento.subir(dummyThumbPath.toFile());

            clip.marcarListo(urlVideo, urlThumb);
        } catch (IOException e) {
            clip.marcarError("Error al generar clip simulado: " + e.getMessage());
        } finally {
            eliminarArchivo(dummyVideoPath);
            eliminarArchivo(dummyThumbPath);
        }
    }

    private void generarReal(Grabacion g, LocalDateTime instante, int segAntes, int segDespues, Clip clip) {
        Path tempVideoPath = null;
        try {
            long offset = 0;
            if (g != null && g.getInicio() != null && instante != null) {
                offset = Math.max(0, Duration.between(
                        g.getInicio().atZone(ClockConfig.ZONA_HORARIA),
                        instante.atZone(ClockConfig.ZONA_HORARIA)
                ).toSeconds() - segAntes);
            }
            int duracion = segAntes + segDespues;
            tempVideoPath = Files.createTempFile("clip_ffmpeg_", ".mp4");

            ProcessBuilder pb = new ProcessBuilder(
                    rutaFFmpeg, "-y",
                    "-ss", String.valueOf(offset),
                    "-i", (g != null && g.getUrlBase() != null) ? g.getUrlBase() : "input.mp4",
                    "-t", String.valueOf(duracion),
                    "-c", "copy",
                    tempVideoPath.toFile().getAbsolutePath()
            );
            Process process = pb.start();
            boolean terminado = process.waitFor(30, TimeUnit.SECONDS);

            if (terminado && process.exitValue() == 0) {
                String urlVideo = almacenamiento.subir(tempVideoPath.toFile());
                clip.marcarListo(urlVideo, urlVideo + ".jpg");
            } else {
                clip.marcarError("FFmpeg finalizo con codigo " + (terminado ? process.exitValue() : "TIMEOUT"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            clip.marcarError("Interrupción ejecutando FFmpeg: " + e.getMessage());
        } catch (Exception e) {
            clip.marcarError("Error ejecutando FFmpeg: " + e.getMessage());
        } finally {
            eliminarArchivo(tempVideoPath);
        }
    }

    private void eliminarArchivo(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo temporal {}: {}", path, e.getMessage());
            }
        }
    }
}

