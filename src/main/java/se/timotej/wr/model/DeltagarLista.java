package se.timotej.wr.model;

import java.util.List;

public record DeltagarLista(
        String namn,
        List<Deltagare> deltagare) {
}
