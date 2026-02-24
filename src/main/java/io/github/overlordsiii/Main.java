package io.github.overlordsiii;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import io.github.overlordsiii.api.Shift;
import io.github.overlordsiii.config.EventColor;
import io.github.overlordsiii.config.PropertiesHandler;
import io.github.overlordsiii.config.TimeZone;
import io.github.overlordsiii.util.CalendarUtil;
import io.github.overlordsiii.util.TimeUtil;
import io.github.overlordsiii.util.Util;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

// TODO Goals
// 1. Make it so that we actually check the secondary calendar id
// if it exists, we don't just want to blindly create a new calendar
// rather we want to store all the event ids we copied from the old calendar to the new calendar
// and then iterate through all events and check if those events are in our event id store
// if not, then we add them to our existing calendar
// otherwise we add all events to a brand new calendar
// if event was deleted then we also want to track that if its not in the current event store and then remove it
// from our event list
// 2. fix gradle build to shadowjar all deps and run as an executable
// 3. add all naming/magic numbers as configurable constants (besides configuration names themselves)
// 4. instead of copying each event, take union of events
// ie if we have mutliple shifts that all have a collective range from 9-3, just make one block for 9-3 coverage
public class Main {
	public static final Logger LOGGER = LogManager.getLogger("WhenIWorkCalendarSync");

	public static final Path CONFIG_HOME_DIRECTORY = Paths.get(System.getProperty("user.home")).resolve("WhenIWorkCalendarSyncConfig");

	public static final Path CREDENTIALS_FILE_PATH = CONFIG_HOME_DIRECTORY.resolve("private_config.json");

	public static final Path TOKENS_DIRECTORY_PATH = CONFIG_HOME_DIRECTORY.resolve("tokens");

	private static final Set<String> SCOPES =
		CalendarScopes.all();

	public static Calendar SERVICE;

	public static final PropertiesHandler CONFIG = PropertiesHandler
		.builder()
		.addConfigOption("secondary-calendar-id", "")
		.addConfigOption("primary-calendar-id", "")
		.addConfigOption("google-redirect-path", "/Callback")
		.addConfigOption("google-redirect-host", "localhost")
		.addConfigOption("google-redirect-port", 8888)
		.addConfigOption("application-name", "WhenIWorkCalendarSync")
		.addConfigOption("calendar-name", "CBE-IT Schedule")
		// needs to be time zone of all events too
		.addConfigOption("time-zone", TimeZone.PACIFIC)
		.addConfigOption("arch-event-color", EventColor.RED)
		.addConfigOption("dc-event-color", EventColor.PALE_BLUE)
		.addConfigOption("description", "Calendar that gives general availability for DC/Archnet Locations")
		.setFileName("calendar-sync.properties")
		.build();

	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

	static {
		Util.initConfigs();
	}

	public static void main(String[] args) throws IOException, GeneralSecurityException {
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		SERVICE =
			new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
				.setApplicationName(CONFIG.getConfigOption("application-name"))
				.build();

		String cbeId = getCBECalendarId();
		Objects.requireNonNull(cbeId, "Could not find the CBE Calendar that should be imported from WhenIWork!");
        if (!cbeId.equals(CONFIG.getConfigOption("primary-calendar-id"))) {
            LOGGER.info("Found new calendar ID " + cbeId +", updating...");
        }
		CONFIG.setConfigOption("primary-calendar-id", cbeId);
		CONFIG.reload();
		if (CONFIG.hasConfigOption("secondary-calendar-id")) {
			deleteAllEvents(CONFIG.getConfigOption("secondary-calendar-id"));
		} else {
			String secondaryCalendar = createCalendar();
			CONFIG.setConfigOption("secondary-calendar-id", secondaryCalendar);
		}
		copyEventsFromCalendarToCalendar(cbeId, CONFIG.getConfigOption("secondary-calendar-id"));
		CONFIG.reload();
	}

	private static void deleteAllEvents(String calendarId) throws IOException {
		for (Event event : CalendarUtil.getAllEvents(calendarId)) {
			LOGGER.info("Deleting event " + event.getSummary() + " from " + TimeUtil.formatEventDateTime(event.getStart()) + " to " + TimeUtil.formatEventDateTime(event.getEnd()));
			SERVICE.events().delete(calendarId, event.getId()).execute();
		}

		LOGGER.info("Finished deleting events from calendar!...");
	}

	private static String createCalendar() throws IOException {
		com.google.api.services.calendar.model.Calendar calendar = new com.google.api.services.calendar.model.Calendar();
		calendar.setSummary(CONFIG.getConfigOption("calendar-name"));
		calendar.setTimeZone(CONFIG.getConfigOption("time-zone", str -> Util.parseEnumSafe(str, TimeZone.class).getZone()));
		calendar.setDescription(CONFIG.getConfigOption("description"));

		calendar = SERVICE.calendars().insert(calendar).execute();

		return calendar.getId();
	}
	private static void copyEventsFromCalendarToCalendar(String sourceCalendarId, String destCalendarId) throws IOException {
		Main.LOGGER.info(String.format("Starting copyEventsFromCalendarToCalendar from %s to %s", sourceCalendarId, destCalendarId));

		Map<LocalDate, List<Shift>> archShifts = new HashMap<>();
		Map<LocalDate, List<Shift>> dcShifts = new HashMap<>();

		List<Event> allEvents = CalendarUtil.getAllEvents(sourceCalendarId);
		Main.LOGGER.info(String.format("Fetched %s events from source calendar %s", allEvents.size(), sourceCalendarId));

		for (Event event : allEvents) {
			String summary = event.getSummary();
			Main.LOGGER.info(String.format("Processing event '%s', start: %s, end: %s",
					summary, TimeUtil.formatEventDateTime(event.getStart()), TimeUtil.formatEventDateTime(event.getEnd())));

			LocalDateTime start = TimeUtil.toLocalDateTime(event.getStart(),
					CONFIG.getConfigOption("time-zone", str -> ZoneId.of(Util.parseEnumSafe(str, TimeZone.class).getZone())));
			LocalDateTime end = TimeUtil.toLocalDateTime(event.getEnd(),
					CONFIG.getConfigOption("time-zone", str -> ZoneId.of(Util.parseEnumSafe(str, TimeZone.class).getZone())));

			LocalDate date = start.toLocalDate();
			boolean archnet = summary.contains("ArchNet");
			Map<LocalDate, List<Shift>> shiftMap = archnet ? archShifts : dcShifts;

			List<Shift> intervals = shiftMap.computeIfAbsent(date, k -> new ArrayList<>());
			Shift mergedInterval = new Shift(start, end);

			List<Shift> newIntervals = new ArrayList<>();

			for (Shift interval : intervals) {
				if (interval.getStart().isAfter(mergedInterval.getEnd())) {
					newIntervals.add(mergedInterval);
					mergedInterval = interval;
				} else {
					mergedInterval = new Shift(
							TimeUtil.minDate(mergedInterval.getStart(), interval.getStart()),
							TimeUtil.maxDate(mergedInterval.getEnd(), interval.getEnd())
					);
				}
			}

			newIntervals.add(mergedInterval);


			shiftMap.put(date, newIntervals);
		}

		ZoneId timeZone = CONFIG.getConfigOption("time-zone", str -> ZoneId.of(Util.parseEnumSafe(str, TimeZone.class).getZone()));

		Main.LOGGER.info(String.format("Inserting %d ArchNet shifts into destination calendar %s", archShifts.values().stream().mapToInt(List::size).sum(), destCalendarId));
		CalendarUtil.insertEvents(archShifts, timeZone, destCalendarId, true);

		Main.LOGGER.info(String.format("Inserting %d DC shifts into destination calendar %s", dcShifts.values().stream().mapToInt(List::size).sum(), destCalendarId));
		CalendarUtil.insertEvents(dcShifts, timeZone, destCalendarId, false);

		Main.LOGGER.info(String.format("Finished copyEventsFromCalendarToCalendar from %s to %s", sourceCalendarId, destCalendarId));
	}

	private static String getCBECalendarId() throws IOException {
		String cbeCalendarId = null;

		for (CalendarListEntry item : SERVICE.calendarList().list().execute().getItems()) {
			if (item.getSummary().contains("College of Built Environments")) {
				cbeCalendarId = item.getId();
			}
		}

		return cbeCalendarId;
	}

	private static Credential getCredentials(NetHttpTransport transport) throws IOException {
		InputStream stream = Files.newInputStream(CREDENTIALS_FILE_PATH);

		GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(stream));

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
			transport, JSON_FACTORY, secrets, SCOPES)
			.setDataStoreFactory(new FileDataStoreFactory(TOKENS_DIRECTORY_PATH.toFile()))
			.setAccessType("offline")
			.build();
		LocalServerReceiver receiver = new LocalServerReceiver.Builder()
				.setCallbackPath(CONFIG.getConfigOption("google-redirect-path"))
				.setHost(CONFIG.getConfigOption("google-redirect-host"))
				.setPort(CONFIG.getConfigOption("google-redirect-port", Integer::parseInt))
				.build();
		return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
	}

}
