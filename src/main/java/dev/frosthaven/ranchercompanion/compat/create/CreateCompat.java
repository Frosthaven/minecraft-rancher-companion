package dev.frosthaven.ranchercompanion.compat.create;

import dev.frosthaven.ranchercompanion.ranch.Seats;

/** Create, when it is loaded: its seats for the whistle's Go here. The job hats are client only (client/CreateClient). */
public final class CreateCompat {
    private CreateCompat() {}

    /** both sides; touches no client class (a dedicated server has none) */
    public static void init() { Seats.Holder.seats = new CreateSeats(); }
}
