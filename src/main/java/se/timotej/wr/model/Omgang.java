package se.timotej.wr.model;

import java.util.List;

public record Omgang(String kon, String namn, List<Heat> heat) {
}
