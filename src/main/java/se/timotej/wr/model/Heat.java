package se.timotej.wr.model;

import java.util.List;

public record Heat(Integer nr, String klass, String sponsor, List<Start> starter) {
}
