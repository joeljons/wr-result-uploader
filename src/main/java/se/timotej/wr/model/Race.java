package se.timotej.wr.model;

import java.util.List;
import java.util.Map;

public record Race(String sektion,
                   String datum,
                   List<Omgang> omgangar,
                   List<DeltagarLista> deltagarLista) {
}
