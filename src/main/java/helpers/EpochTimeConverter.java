package helpers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class EpochTimeConverter {

    static String timeZone = "Europe/Istanbul";

    public static String convertEpochToDateTime(long epochTimeMillis) {
        try {
            Instant instant = Instant.ofEpochMilli(epochTimeMillis);
            ZoneId zoneId = ZoneId.of(timeZone);
            ZonedDateTime zonedDateTime = instant.atZone(zoneId);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE MMMM d, yyyy HH:mm:ss");
            String formattedDate = zonedDateTime.format(formatter);

            return formattedDate;
        } catch (Exception e) {
            return "Error: Unable to convert epoch time. " + e.getMessage();
        }
    }

}
