package io.github.overlordsiii.util;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import io.github.overlordsiii.api.Shift;
import io.github.overlordsiii.config.EventColor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.overlordsiii.Main.CONFIG;
import static io.github.overlordsiii.Main.SERVICE;

public class CalendarUtil {
    public static void insertEvents(Map<LocalDate, List<Shift>> map, ZoneId zone, String calendarId, boolean archnet) throws IOException {
        for (List<Shift> value : map.values()) {
            for (Shift pair : value) {
                String configKey = archnet ? "arch-event-color" : "dc-event-color";

                Event newEvent = new Event()
                        .setSummary(archnet ? "ArchNet" : "Digital Commons")
                        .setDescription("")
                        .setStart(TimeUtil.toEventDateTime(pair.getStart(), zone))
                        .setEnd(TimeUtil.toEventDateTime(pair.getEnd(), zone))
                        .setColorId(CONFIG.getConfigOption(configKey, str -> Util.parseEnumSafe(str, EventColor.class).getId()));

                SERVICE.events().insert(calendarId, newEvent).execute();
            }
        }
    }

    public static List<Event> getAllEvents(String calendarId) throws IOException {
        List<Event> items = new ArrayList<>();
        Events events = SERVICE.events().list(calendarId).execute();
        while (events != null) {
            items.addAll(events.getItems());

            if (events.getNextPageToken() != null) {
                events = SERVICE
                        .events()
                        .list(calendarId)
                        .setPageToken(events.getNextPageToken())
                        .execute();
            } else {
                break;
            }
        }
        return items;
    }
}
