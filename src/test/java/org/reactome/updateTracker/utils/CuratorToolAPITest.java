package org.reactome.updateTracker.utils;

import org.gk.model.InstanceNotFoundException;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Before;
import org.junit.Test;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.InstanceEdit;
import org.reactome.server.graph.domain.model.Person;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 2/8/2026
 */
public class CuratorToolAPITest {
	private CuratorToolAPI curatorToolAPI;

	@Before
	public void initCuratorToolWSAPI() {
		curatorToolAPI = new CuratorToolAPI();
	}

	@Test
	public void findDatabaseObjectByDbIdTest() {
		final long goReferenceDatabaseDbId = 1L;
		final String goReferenceDatabaseDisplayName = "GO";

		SimpleInstance simpleInstance = curatorToolAPI.findDatabaseObjectByDbId(goReferenceDatabaseDbId);

		assertEquals(goReferenceDatabaseDisplayName, simpleInstance.getDisplayName());
	}

	@Test
	public void commitTest() throws Exception {
		SimpleInstance simpleInstance = new SimpleInstance();
		simpleInstance.setDefaultPersonId(1551959L);
		simpleInstance.setDbId(-1L);
		simpleInstance.setSchemaClassName("Summation");
		simpleInstance.setDisplayName("Test Update Tracker");
		simpleInstance.setCreated(createInstanceEdit(simpleInstance));

		curatorToolAPI.commit(simpleInstance);
	}

	public InstanceEdit createInstanceEdit(SimpleInstance instance) throws Exception {
		Long personId = instance.getDefaultPersonId();
		if (personId == null) {
			//logger.error("Person dbId is not defined!");
			throw new IllegalArgumentException("personId is null");
		}
		DatabaseObject person = curatorToolAPI.fetchPersonInstance(personId);
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
