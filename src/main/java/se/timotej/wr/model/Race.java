package se.timotej.wr.model;

import java.util.List;

public record Race (String sektion, String datum, List<Omgang> omgangar){
}
