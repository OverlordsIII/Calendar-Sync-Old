package io.github.overlordsiii;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
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
import io.github.overlordsiii.config.EventColor;
import io.github.overlordsiii.config.PropertiesHandler;
import io.github.overlordsiii.config.TimeZone;
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
		.addConfigOption("time-zone", TimeZone.PACIFIC)
		.addConfigOption("arch-event-color", EventColor.RED)
		.addConfigOption("dc-event-color", EventColor.PALE_BLUE)
		.setFileName("calendar-sync.properties")
		.build();


	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	public static void main(String[] args) throws IOException, GeneralSecurityException {
		initConfigs();

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
		SERVICE.calendars().clear(calendarId).execute();
	}

	private static <T extends Enum<T>> T parseEnumSafe(String str, Class<T> clazz) {
		try {
			return Enum.valueOf(clazz, str);
		} catch (IllegalArgumentException e) {
			LOGGER.error("Error while parsing enum constants, please make sure you pass one of the following: " + Arrays.deepToString(clazz.getEnumConstants()));
			throw new IllegalArgumentException("Illegal enum constant passed - please check logs for correct constants!");
		}
    }

	private static String createCalendar() throws IOException {
		com.google.api.services.calendar.model.Calendar calendar = new com.google.api.services.calendar.model.Calendar();
		calendar.setSummary(CONFIG.getConfigOption("calendar-name"));
		calendar.setTimeZone(CONFIG.getConfigOption("time-zone", str -> parseEnumSafe(str, TimeZone.class).getZone()));

		calendar = SERVICE.calendars().insert(calendar).execute();

		return calendar.getId();
	}

	private static void copyEventsFromCalendarToCalendar(String sourceCalendarId, String destCalendarId) throws IOException {
		for (Event event : getAllEvents(sourceCalendarId)) {
			Main.LOGGER.info("Copying event: " + event.getSummary() + " at " + event.getStart());

			boolean archnet = event.getSummary().contains("ArchNet");

			String configKey = archnet ? "arch-event-color" : "dc-event-color";

			Event newEvent = new Event()
				.setSummary(archnet ? "ArchNet" : "Digital Commons")
				.setDescription(event.getDescription())
				.setStart(event.getStart())
				.setEnd(event.getEnd())
				.setColorId(CONFIG.getConfigOption(configKey, str -> parseEnumSafe(str, EventColor.class).getId()));

			SERVICE.events().insert(destCalendarId, newEvent).execute();
		}
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

	private static List<Event> getAllEvents(String calendarId) throws IOException {
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

	private static void initConfigs() {
		try {
			if (!Files.exists(CONFIG_HOME_DIRECTORY)) {
				Files.createDirectory(CONFIG_HOME_DIRECTORY);
			} if (!Files.exists(TOKENS_DIRECTORY_PATH)) {
				Files.createDirectory(TOKENS_DIRECTORY_PATH);
			}
		} catch (IOException e) {
			Main.LOGGER.error("Unable to create config/token directory at: \"" + CONFIG_HOME_DIRECTORY + "\" or \"" + TOKENS_DIRECTORY_PATH + "\"", e);
			e.printStackTrace();
		}
		if (!Files.exists(CREDENTIALS_FILE_PATH)) {
			throw new IllegalArgumentException("Credentials file at: \"" + CREDENTIALS_FILE_PATH + "\" not found!");
		}
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
