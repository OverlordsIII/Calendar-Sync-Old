package io.github.overlordsiii;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import io.github.overlordsiii.config.PropertiesHandler;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

// TODO Goals
// Make it so that we actually check the secondary calendar id
// if it exists, we don't just want to blindly create a new calendar
// rather we want to store all the event ids we copied from the old calendar to the new calendar
// and then iterate through all events and check if those events are in our event id store
// if not, then we add them to our existing calendar
// otherwise we add all events to a brand new calendar
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
		.setFileName("calendar-sync.properties")
		.build();


	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	public static void main(String[] args) throws IOException, GeneralSecurityException {
		initConfigs();

		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		SERVICE =
			new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
				.setApplicationName("WhenIWorkCalendarSync")
				.build();

		String cbeId = getCBECalendarId();
		Objects.requireNonNull(cbeId, "Could not find the CBE Calendar that should be imported from WhenIWork!");
		CONFIG.setConfigOption("primary-calendar-id", cbeId);
		CONFIG.reload();
		String secondaryCalendar = getOrCreateCopy(cbeId);
		CONFIG.setConfigOption("secondary-calendar-id", secondaryCalendar);
		CONFIG.reload();
	}

	private static String getOrCreateCopy(String cbeId) throws IOException {
		com.google.api.services.calendar.model.Calendar calendar = new com.google.api.services.calendar.model.Calendar();
		calendar.setSummary("CBE-IT Schedule");
		calendar.setTimeZone("America/Los_Angeles");

		calendar = SERVICE.calendars().insert(calendar).execute();

		for (Event event : getAllEvents(cbeId)) {
			Main.LOGGER.info("Copying event: " + event.getSummary() + " at " + event.getStart());

			boolean archnet = event.getSummary().contains("ArchNet");

			Event newEvent = new Event()
				.setSummary(archnet ? "ArchNet" : "Digital Commons")
				.setDescription(event.getDescription())
				.setStart(event.getStart())
				.setEnd(event.getEnd())
				.setColorId(archnet ? "11" : "1");

			SERVICE.events().insert(calendar.getId(), newEvent).execute();
		}



		return calendar.getId();
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
