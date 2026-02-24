package io.github.overlordsiii.util;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.EventDateTime;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class TimeUtil {

    public static LocalDateTime toLocalDateTime(EventDateTime edt, ZoneId zone) {
        com.google.api.client.util.DateTime dt = edt.getDateTime() != null ? edt.getDateTime() : edt.getDate();

        Instant instant = Instant.ofEpochMilli(dt.getValue());

        return LocalDateTime.ofInstant(instant, zone);
    }

    public static EventDateTime toEventDateTime(LocalDateTime ldt, ZoneId zone) {
        ZonedDateTime zdt = ldt.atZone(zone);
        DateTime dt = new DateTime(zdt.toInstant().toEpochMilli(), zone.getRules().getOffset(zdt.toInstant()).getTotalSeconds() / 60);
        return new EventDateTime().setDateTime(dt).setTimeZone(zone.getId());
    }

    public static String formatEventDateTime(EventDateTime eventDateTime) {
        if (eventDateTime == null) return "";

        DateTime dt = eventDateTime.getDateTime() != null
                ? eventDateTime.getDateTime()
                : eventDateTime.getDate();

        if (dt == null) return "";

        boolean dateOnly = dt.isDateOnly();
        long millis = dt.getValue();
        Date date = new Date(millis);

        String pattern = dateOnly ? "MM/dd/yyyy" : "MM/dd/yyyy 'at' hh:mm a";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);

        if (!dateOnly) {

            date = new Date(millis);
        }

        return sdf.format(date);
    }

    public static String formatLocalDateTime(LocalDateTime dateTime) {
        return formatLocalDateTime(dateTime, false);
    }

    public static String formatLocalDateTime(LocalDateTime dateTime, boolean dateOnly) {
        if (dateTime == null) return "";

        DateTimeFormatter formatter = dateOnly
                ? DateTimeFormatter.ofPattern("MM/dd/yyyy")
                : DateTimeFormatter.ofPattern("MM/dd/yyyy 'at' hh:mm a");

        return dateTime.format(formatter);
    }

    public static LocalDate toLocalDate(EventDateTime eventDateTime, ZoneId targetZoneId) {
        if (eventDateTime == null) return null;

        DateTime dt = eventDateTime.getDateTime() != null
                ? eventDateTime.getDateTime()
                : eventDateTime.getDate();

        if (dt == null) return null;

        if (dt.isDateOnly()) {
            Instant instant = Instant.ofEpochMilli(dt.getValue());
            return instant.atZone(targetZoneId).toLocalDate();
        } else {
            Instant instant = Instant.ofEpochMilli(dt.getValue());
            return instant.atZone(targetZoneId).toLocalDate();
        }
    }

    public static LocalDateTime minDate(LocalDateTime d1, LocalDateTime d2) {
        if (d1.isBefore(d2)) {
            return d1;
        }

        return d2;
    }

    public static LocalDateTime maxDate(LocalDateTime d1, LocalDateTime d2) {
        if (d1.isAfter(d2)) {
            return d1;
        }

        return d2;
    }
}
