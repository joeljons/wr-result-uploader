package se.timotej.wr.model;

import java.util.List;

public record Deltagare(Integer licensNr,
                        String hundNamn,
                        String fodelseDatum,
                        String agare,
                        String kon,
                        String sektion,
                        String uppfodare,
                        String address,
                        String mor,
                        String far,
                        List<Deltagande> deltagande) {
}
