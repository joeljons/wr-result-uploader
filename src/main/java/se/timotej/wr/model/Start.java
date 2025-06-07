package se.timotej.wr.model;

public record Start(String farg,
                    Integer licensNr,
                    String hundNamn,
                    String kon,
                    Double tid,
                    Integer placering,
                    String agare,
                    String sektion,
                    String kommentar,
                    Boolean struken,
                    Boolean licensHund) {
}
