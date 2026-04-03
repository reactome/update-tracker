package org.reactome.updateTracker.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.reactome.curation.CuratorToolWsApplication;
import org.reactome.curation.controller.CurationController;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.server.graph.domain.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 9/18/2025
 */
public class CuratorToolAPI {
	private static final Logger logger = LoggerFactory.getLogger(CuratorToolAPI.class);

	private ApplicationContext applicationContext;
	private CurationController controller;

	public CuratorToolAPI() {
		this.controller = this.initController();
		if (this.controller == null)
			throw new IllegalStateException("Failed to initialize CuratorToolAPI: controller is null");
	}

	// The following code is copied directly from the slicing tool project.
	private CurationController initController() {
		try {
			applicationContext = new SpringApplicationBuilder(CuratorToolWsApplication.class)
					.web(WebApplicationType.SERVLET)
					.properties("server.port=-1")  // disable HTTP server; keep full servlet context for correct AspectJ wiring
					.run();
			return applicationContext.getBean(CurationController.class);
		}
		catch (Exception e) {
			logger.error("GraphDBInstanceManager.initController(): " + e.getMessage(), e);
		}
		return null;
	}

	public SimpleInstance commit(SimpleInstance simpleInstance) throws JsonProcessingException {
		return controller.commit(simpleInstance);
	}

	public SimpleInstance findDatabaseObjectByDbId(long dbId) {
		return controller.findByDdIdInInstance(dbId);
	}

	public Person fetchPersonInstance(long personDbId) {
		return (Person) controller.findByDdId(personDbId);
	}
}