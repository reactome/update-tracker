package org.reactome.updateTracker.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.gk.model.GKInstance;
import org.gk.model.InstanceNotFoundException;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Before;
import org.junit.Test;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.InstanceEdit;
import org.reactome.server.graph.domain.model.Person;
import org.reactome.updateTracker.Main;

import java.io.IOException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 2/8/2026
 */
public class CuratorToolWSAPITest {
	private CuratorToolWSAPI curatorToolWSAPI;

	@Before
	public void initCuratorToolWSAPI() {
		curatorToolWSAPI = new CuratorToolWSAPI();
	}

	@Test
	public void findDatabaseObjectByDbIdTest() {
		final long goReferenceDatabaseDbId = 1L;
		final String goReferenceDatabaseDisplayName = "GO";

		SimpleInstance simpleInstance = curatorToolWSAPI.findDatabaseObjectByDbId(goReferenceDatabaseDbId);

		assertEquals(simpleInstance.getDisplayName(), goReferenceDatabaseDisplayName);
	}

	@Test
	public void findDatabaseObjectsByDbIdsTest() {
		final List<Long> dbIds = List.of(1L); //List.of(109581L, 1640170L);
		final List<String> expectedDisplayNames = List.of("GO"); //List.of("Apoptosis", "Cell Cycle");

		List<SimpleInstance> simpleInstances = curatorToolWSAPI.findDatabaseObjectsByDbIds(dbIds);

		assertEquals(simpleInstances.stream().map(SimpleInstance::getDisplayName).collect(Collectors.toList()), expectedDisplayNames);
	}

//	@Test
//	public void commitUpdateTrackerInstanceTest() throws Exception {
//		MySQLAdaptor currentSliceDBA = getCurrentSliceDBA();
//		GKInstance updateTrackerInstance =
//			(GKInstance) currentSliceDBA.fetchInstancesByClass(ReactomeJavaConstants._UpdateTracker).iterator().next();
//		System.out.println(updateTrackerInstance);
//		SimpleInstance updateTrackerSimpleInstance =
//			GraphDBConverter.convertGKInstanceToSimpleInstance(updateTrackerInstance);
//		curatorToolWSAPI.commit(updateTrackerSimpleInstance);
//	}

	@Test
	public void commitTest() throws Exception {
		SimpleInstance simpleInstance = new SimpleInstance();
		simpleInstance.setDefaultPersonId(1551959L);
		simpleInstance.setDbId(-1L);
		simpleInstance.setSchemaClassName("Summation");
		simpleInstance.setDisplayName("Test Update Tracker");
		simpleInstance.setCreated(createInstanceEdit(simpleInstance));
		curatorToolWSAPI.commit(simpleInstance);
	}

	private MySQLAdaptor getCurrentSliceDBA() throws SQLException, IOException {
		final Properties configProps = getConfigProps();
		final String prefix = "currentslice";
		String userName = configProps.getProperty(prefix + ".user", "root");
		String password = configProps.getProperty(prefix + ".password", "root");
		String dbName = configProps.getProperty(prefix + ".dbName");
		String host = configProps.getProperty(prefix + ".host", "localhost");
		int port = Integer.parseInt(configProps.getProperty(prefix + ".port", "3306"));

		return new MySQLAdaptor(host, dbName, userName, password, port);
	}

	private Properties getConfigProps() throws IOException {
		Properties props = new Properties();
		props.load(Main.class.getClassLoader().getResourceAsStream("config.properties"));
		return props;
	}

	public InstanceEdit createInstanceEdit(SimpleInstance instance) throws Exception {
		Long personId = instance.getDefaultPersonId();
		if (personId == null) {
			//logger.error("Person dbId is not defined!");
			throw new IllegalArgumentException("personId is null");
		}
		DatabaseObject person = curatorToolWSAPI.fetchPersonInstance(personId);
		if (person == null) {
			//logger.error("Cannot find Person with dbId: " + personId);
			throw new InstanceNotFoundException(ReactomeJavaConstants.Person, personId);
		}
		InstanceEdit ie = new InstanceEdit();
		// Need to specify author and datetime
		ie.setAuthor(Collections.singletonList((Person)person));
		ie.setDateTime(this.getDateTime());
		// Generate display name for it
		// Technically we should have a place to manage this for all instances
		// However, this work has been moved to the front-end. We just limit it
		// to InstanceEdit here
		String displayName = person.getDisplayName() + ", " + ie.getDateTime().split(" ")[0];
		ie.setDisplayName(displayName);
		return ie;
	}

	private String getDateTime() {
		// Use GMT to ensure the same time zone for all curators
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		return now.format(formatter);
	}
}
