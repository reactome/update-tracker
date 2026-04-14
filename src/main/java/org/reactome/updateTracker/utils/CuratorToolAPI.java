package org.reactome.updateTracker.utils;

import org.reactome.curation.CuratorToolWsApplication;
import org.reactome.curation.controller.CurationController;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author Guanming Wu
 * @author Joel Weiser
 */
public class CuratorToolAPI {
    private static final Logger logger = LoggerFactory.getLogger(CuratorToolAPI.class);
    private static CurationController controller;

    private ConfigurableApplicationContext applicationContext;

    public CuratorToolAPI() {
        if (controller == null) {
            controller = this.initController();
            if (controller == null) {
                throw new IllegalStateException("Failed to initialize CuratorToolAPI: controller is null");
            }
        }
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

    public SimpleInstance commit(SimpleInstance simpleInstance) {
        return controller.commit(simpleInstance);
    }

    public SimpleInstance findDatabaseObjectByDbId(long dbId) {
        DatabaseObject databaseObject = controller.findByDdId(dbId);
        if (databaseObject == null) {
            return null;
        }

        try {
            return controller.getConverter().convert(databaseObject);
        } catch (Exception e) {
            throw new RuntimeException("Unable to convert DatabaseObject " + databaseObject + " to SimpleInstance", e);
        }
    }

    public Person fetchPersonInstance(long personDbId) {
        return (Person) controller.findByDdId(personDbId);
    }

    public void close() {
        applicationContext.close();
    }
}