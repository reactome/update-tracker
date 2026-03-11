package org.reactome.updateTracker;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;

import org.gk.persistence.MySQLAdaptor;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.server.graph.domain.model.InstanceEdit;
import org.reactome.server.graph.domain.model.Person;
import org.reactome.updateTracker.comparer.EventComparer;
import org.reactome.updateTracker.comparer.InstanceComparer;
import org.reactome.updateTracker.comparer.physicalentity.PhysicalEntityComparerFactory;
import org.reactome.updateTracker.matcher.EventMatcher;
import org.reactome.updateTracker.matcher.InstanceMatcher;
import org.reactome.updateTracker.matcher.PhysicalEntityMatcher;
import org.reactome.updateTracker.model.Action;
import org.reactome.updateTracker.model.UpdateTracker;
import org.reactome.updateTracker.utils.CuratorToolWSAPI;
import org.reactome.updateTracker.utils.DBUtils;
import org.reactome.updateTracker.utils.GraphDBConverter;

import static org.reactome.updateTracker.utils.DBUtils.getMostRecentReleaseInstance;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class UpdateTrackerHandler {
    private final static Logger logger = LogManager.getLogger(UpdateTracker.class);

    private EventComparer eventComparer;

    private DbAdaptorMap dbAdaptorMap;
    private long personId;

    private SimpleInstance releaseInstance;
    private InstanceEdit createdInstanceEdit;

    private CuratorToolWSAPI curatorToolWSAPI;

    public UpdateTrackerHandler(
        MySQLAdaptor currentSliceDBA, MySQLAdaptor previousSliceDBA, long personId
    ) {
        DbAdaptorMap.DbAdaptorMapBuilder dbAdaptorMapBuilder = new DbAdaptorMap.DbAdaptorMapBuilder();
        dbAdaptorMapBuilder.setOlderDbAdaptor(previousSliceDBA);
        dbAdaptorMapBuilder.setNewerDbAdaptor(currentSliceDBA);

        this.curatorToolWSAPI = new CuratorToolWSAPI();

        this.dbAdaptorMap = dbAdaptorMapBuilder.build();
        this.personId = personId;
        this.createdInstanceEdit = createInstanceEdit(personId);
    }

    public void handleUpdateTrackerInstances(boolean uploadUpdateTrackerInstancesToSource) throws Exception {
        List<GKInstance> updateTrackerInstances = createUpdateTrackerInstances();

        storeUpdateTrackerInstancesInSliceDatabase(updateTrackerInstances);

        if (uploadUpdateTrackerInstancesToSource) {
            storeReleaseInstanceInSourceDatabase();

            storeUpdateTrackerInstancesInSourceDatabase(updateTrackerInstances);
        }
    }

    private List<GKInstance> createUpdateTrackerInstances() throws Exception {
        List<GKInstance> updateTrackerInstances = new ArrayList<>();

        logger.info("Creating event update tracker instances");
        updateTrackerInstances.addAll(createUpdateTrackerInstances(ComparisonType.EVENT));

        logger.info("Creating physical entity update tracker instances");
        updateTrackerInstances.addAll(createUpdateTrackerInstances(ComparisonType.PHYSICAL_ENTITY));

        return updateTrackerInstances;
    }

    private void storeUpdateTrackerInstancesInSliceDatabase(List<GKInstance> updateTrackerInstances) throws Exception {
        logger.info("Storing update tracker instances in slice db");

        for (GKInstance updateTrackerInstance : updateTrackerInstances) {
            getCurrentSliceDBA().storeInstance(updateTrackerInstance);
        }
    }
    
    private void storeReleaseInstanceInSourceDatabase() throws Exception {
        logger.info("Storing release instance in source database");

        GKInstance releaseInstanceFromSlice = getMostRecentReleaseInstance(getCurrentSliceDBA());
        releaseInstance = curatorToolWSAPI.commit(cloneReleaseInstance(releaseInstanceFromSlice));
    }

    private void storeUpdateTrackerInstancesInSourceDatabase(List<GKInstance> updateTrackerInstances)
        throws Exception {

        logger.info("Storing update tracker instances in source database");

        List<SimpleInstance> updateTrackerSimpleInstances = new ArrayList<>();
        for (GKInstance updateTrackerInstance : updateTrackerInstances) {
            updateTrackerSimpleInstances.add(convertUpdateTrackerToSimpleInstance(updateTrackerInstance));
        }
        commitToSourceDB(updateTrackerSimpleInstances);
    }

    private SimpleInstance cloneReleaseInstance(GKInstance releaseInstance) throws Exception {
        SimpleInstance newReleaseInstanceGraph = GraphDBConverter.convertGKInstanceToSimpleInstance(
            releaseInstance, getPersonId()
        );
        newReleaseInstanceGraph.setDbId(-1L);
        newReleaseInstanceGraph.setCreated(getCreatedInstanceEdit());
        newReleaseInstanceGraph.setDefaultPersonId(getPersonId());

        return newReleaseInstanceGraph;
    }

    private List<GKInstance> createUpdateTrackerInstances(ComparisonType comparisonType) throws Exception {

        UpdateTracker.UpdateTrackerBuilder sliceUpdateTrackerBuilder = getUpdateTrackerBuilder();

        InstanceMatcher instanceMatcher = getInstanceMatcher(comparisonType);

        logger.info("Getting " + comparisonType.name() + " instance pairs...");
        Set<Map.Entry<GKInstance,GKInstance>> equivalentInstancePairs =
            instanceMatcher.getCurrentToPreviousInstanceMap().entrySet();

        logger.info("Instance pairs size: " + equivalentInstancePairs.size());
        List<GKInstance> updateTrackerInstances = new ArrayList<>();
        for (Map.Entry<GKInstance, GKInstance> equivalentInstancePair : equivalentInstancePairs) {
            Set<Action> actions = getInstanceComparer(comparisonType, equivalentInstancePair)
                .getChanges(equivalentInstancePair);

            if (!actions.isEmpty()) {
                GKInstance currentInstance = equivalentInstancePair.getValue();

                logger.info("Actions " + actions + " for " + currentInstance);

                GKInstance updateTrackerGKInstance = sliceUpdateTrackerBuilder
                    .build(currentInstance, actions)
                    .createUpdateTrackerInstance(getCurrentSliceDBA());
                updateTrackerInstances.add(updateTrackerGKInstance);
            }
        }
        return updateTrackerInstances;
    }

    private SimpleInstance convertUpdateTrackerToSimpleInstance(GKInstance updateTrackerGKInstance) throws Exception {
        SimpleInstance updateTrackerSimpleInstance = GraphDBConverter.convertGKInstanceToSimpleInstance(
            updateTrackerGKInstance, getPersonId());
        updateTrackerSimpleInstance.setDbId(-1L);
        updateTrackerSimpleInstance.setAttribute("release", getReleaseInstance());
        updateTrackerSimpleInstance.setCreated(getCreatedInstanceEdit());
        return updateTrackerSimpleInstance;
    }

    private InstanceComparer getInstanceComparer(ComparisonType comparisonType, Map.Entry<GKInstance, GKInstance> equivalentInstancePair) throws Exception {
        InstanceComparer instanceComparer;
        if (comparisonType == ComparisonType.EVENT) {
            instanceComparer = getEventComparer();
        } else {
            instanceComparer = PhysicalEntityComparerFactory.create(equivalentInstancePair);
        }
        return instanceComparer;
    }

    private EventComparer getEventComparer() throws Exception {
        if (this.eventComparer == null) {
            this.eventComparer = new EventComparer(new EventMatcher(getPreviousSliceDBA(), getCurrentSliceDBA()));
        }
        return this.eventComparer;
    }

    private InstanceMatcher getInstanceMatcher(ComparisonType comparisonType)
        throws Exception {
        InstanceMatcher instanceMatcher;
        if (comparisonType == ComparisonType.EVENT) {
            instanceMatcher = new EventMatcher(
                getDbAdaptorMap().getOlderDbAdaptor(),
                getDbAdaptorMap().getNewerDbAdaptor());
        } else if (comparisonType == ComparisonType.PHYSICAL_ENTITY) {
            instanceMatcher = new PhysicalEntityMatcher(
                getDbAdaptorMap().getOlderDbAdaptor(),
                getDbAdaptorMap().getNewerDbAdaptor());
        } else {
            throw new IllegalStateException("Unsupported matcher type: " + comparisonType);
        }
        return instanceMatcher;
    }

    private void commitToSourceDB(List<SimpleInstance> instances) throws Exception {
        if (instances == null || instances.isEmpty())
            return; // Nothing to do.

        for (SimpleInstance instance : instances) {
            commitToSourceDB(instance);
        }
    }

    private void commitToSourceDB(SimpleInstance instance) throws Exception {
        curatorToolWSAPI.commit(instance);
    }

    private DbAdaptorMap getDbAdaptorMap() {
        return this.dbAdaptorMap;
    }

    private MySQLAdaptor getCurrentSliceDBA() {
        return getDbAdaptorMap().getNewerDbAdaptor();
    }

    private MySQLAdaptor getPreviousSliceDBA() {
        return getDbAdaptorMap().getOlderDbAdaptor();
    }

    private long getPersonId() {
        return this.personId;
    }

    private InstanceEdit getCreatedInstanceEdit() {
        return this.createdInstanceEdit;
    }

    private SimpleInstance getReleaseInstance() {
        return this.releaseInstance;
    }

    private UpdateTracker.UpdateTrackerBuilder getUpdateTrackerBuilder() throws Exception {
        GKInstance releaseInstance = getMostRecentReleaseInstance(getCurrentSliceDBA());

        return UpdateTracker.UpdateTrackerBuilder.createUpdateTrackerBuilder(
            releaseInstance, getPersonId(), DBUtils.getCreatedInstanceEdit(getCurrentSliceDBA(), personId)
        );
    }

    private InstanceEdit createInstanceEdit(long personDbId) {
        Person person = curatorToolWSAPI.fetchPersonInstance(personDbId);
        if (person == null) {
            logger.error("Cannot find Person with dbId: " + personDbId);
            throw new RuntimeException("Person " + personDbId + " not found");
        } else {
            InstanceEdit ie = new InstanceEdit();
            ie.setAuthor(Collections.singletonList(person));
            ie.setDateTime(this.getDateTime());
            String personDisplayName = person.getDisplayName();
            String displayName = personDisplayName + ", " + ie.getDateTime().split(" ")[0];
            ie.setDisplayName(displayName);

            return ie;
        }
    }

    private String getDateTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    private static class DbAdaptorMap {
        private MySQLAdaptor olderDbAdaptor;
        private MySQLAdaptor newerDbAdaptor;

        private DbAdaptorMap() {}

        public MySQLAdaptor getOlderDbAdaptor() {
            return this.olderDbAdaptor;
        }

        public MySQLAdaptor getNewerDbAdaptor() {
            return this.newerDbAdaptor;
        }

        private void setOlderDbAdaptor(MySQLAdaptor olderDbAdaptor) {
            this.olderDbAdaptor = olderDbAdaptor;
        }

        private void setNewerDbAdaptor(MySQLAdaptor newerDbAdaptor) {
            this.newerDbAdaptor = newerDbAdaptor;
        }

        private static class DbAdaptorMapBuilder {
            private MySQLAdaptor olderDbAdaptor;
            private MySQLAdaptor newerDbAdaptor;

            public DbAdaptorMapBuilder() {}

            private void setOlderDbAdaptor(MySQLAdaptor olderDbAdaptor) {
                this.olderDbAdaptor = olderDbAdaptor;
            }

            private void setNewerDbAdaptor(MySQLAdaptor newerDbAdaptor) {
                this.newerDbAdaptor = newerDbAdaptor;
            }

            public DbAdaptorMap build() {
                DbAdaptorMap dbAdaptorMap = new DbAdaptorMap();
                dbAdaptorMap.setOlderDbAdaptor(this.olderDbAdaptor);
                dbAdaptorMap.setNewerDbAdaptor(this.newerDbAdaptor);
                return dbAdaptorMap;
            }
        }
    }

    private enum ComparisonType {
        EVENT,
        PHYSICAL_ENTITY
    }
}
