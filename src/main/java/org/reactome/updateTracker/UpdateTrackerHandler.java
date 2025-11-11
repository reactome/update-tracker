package org.reactome.updateTracker;

import static org.reactome.updateTracker.utils.DBUtils.getMostRecentReleaseInstance;

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
import org.reactome.updateTracker.utils.GraphDBConverter;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class UpdateTrackerHandler {
    private final static Logger logger = LogManager.getLogger(UpdateTracker.class);

    private EventComparer eventComparer;

    private DbAdaptorMap dbAdaptorMap;
    private long personId;
    private InstanceEdit createdInstanceEdit;

    private CuratorToolWSAPI curatorToolWSAPI;
    private SimpleInstance releaseInstance;

    public UpdateTrackerHandler(
        MySQLAdaptor sourceDBA, MySQLAdaptor currentSliceDBA, MySQLAdaptor previousSliceDBA, long personId
    ) {
        DbAdaptorMap.DbAdaptorMapBuilder dbAdaptorMapBuilder = new DbAdaptorMap.DbAdaptorMapBuilder();
        dbAdaptorMapBuilder.setOlderDbAdaptor(previousSliceDBA);
        dbAdaptorMapBuilder.setNewerDbAdaptor(currentSliceDBA);
        dbAdaptorMapBuilder.setTargetDbAdaptor(sourceDBA);

        curatorToolWSAPI = new CuratorToolWSAPI();

        this.dbAdaptorMap = dbAdaptorMapBuilder.build();
        this.personId = personId;
        this.createdInstanceEdit = createInstanceEdit(personId);

    }

    public void handleUpdateTrackerInstances(boolean uploadUpdateTrackerInstancesToSource) throws Exception {
        if (uploadUpdateTrackerInstancesToSource) {
            logger.info("Storing release instance in source database");
            storeReleaseInstanceInSourceDatabase();
        }
        logger.info("Creating event update tracker instances");
        createAndStoreUpdateTrackerInstances(ComparisonType.EVENT, uploadUpdateTrackerInstancesToSource);
        logger.info("Creating physical entity update tracker instances");
        createAndStoreUpdateTrackerInstances(ComparisonType.PHYSICAL_ENTITY, uploadUpdateTrackerInstancesToSource);
    }
    
    private void storeReleaseInstanceInSourceDatabase() throws Exception {
        GKInstance releaseInstanceFromSlice = getMostRecentReleaseInstance(getCurrentSliceDBA());
        releaseInstance = cloneReleaseInstance(releaseInstanceFromSlice);

        curatorToolWSAPI.commit(releaseInstance);
    }
    
    private SimpleInstance cloneReleaseInstance(GKInstance releaseInstance) throws Exception {
        SimpleInstance newReleaseInstanceGraph = GraphDBConverter.convertGKInstanceToSimpleInstance(
            releaseInstance
        );
        newReleaseInstanceGraph.setDefaultPersonId(1551959L);

        return newReleaseInstanceGraph;
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

    private void createAndStoreUpdateTrackerInstances(
        ComparisonType comparisonType, boolean uploadUpdateTrackerInstancesToSource) throws Exception {

        UpdateTracker.UpdateTrackerBuilder sourceUpdateTrackerBuilder =
            getUpdateTrackerBuilder();
        UpdateTracker.UpdateTrackerBuilder sliceUpdateTrackerBuilder =
            getUpdateTrackerBuilder();

        InstanceMatcher instanceMatcher = getInstanceMatcher(comparisonType);

        logger.info("Getting " + comparisonType.name() + " instance pairs...");
        Set<Map.Entry<GKInstance,GKInstance>> equivalentInstancePairs =
            instanceMatcher.getCurationCurrentToPreviousInstances().entrySet();

        logger.info("Instance pairs size: " + equivalentInstancePairs.size());
        List<SimpleInstance> toBeUploadedToSrcDBA = new ArrayList<>();
        for (Map.Entry<GKInstance, GKInstance> equivalentInstancePair : equivalentInstancePairs) {
            Set<Action> actions = getInstanceComparer(comparisonType, equivalentInstancePair)
                .getChanges(equivalentInstancePair);

            if (!actions.isEmpty()) {
                logger.info("Actions " + actions);
                GKInstance currentInstance = equivalentInstancePair.getValue();

                if (uploadUpdateTrackerInstancesToSource) {
                    logger.info("Adding toBeUploadedToSrcDBA " + currentInstance);
                    SimpleInstance sourceInstance = curatorToolWSAPI.findDatabaseObjectByDbId(currentInstance.getDBID());
                    SimpleInstance updateTracker = sourceUpdateTrackerBuilder
                        .build(sourceInstance, actions)
                        .createUpdateTrackerInstance();
                    toBeUploadedToSrcDBA.add(updateTracker);
                }

//                logger.info("Storing instance in current slice dba " + currentInstance);
//                getCurrentSliceDBA().storeInstance(
//                    sliceUpdateTrackerBuilder
//                        .build(currentInstance, actions)
//                        .createUpdateTrackerInstance(getCurrentSliceDBA())
//                );
            }
        }
        commitToSourceDB(toBeUploadedToSrcDBA);
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
            this.eventComparer = new EventComparer(new EventMatcher(getPreviousSliceDBA(), getCurrentSliceDBA(), getSourceDBA()));
        }
        return this.eventComparer;
    }

    private InstanceMatcher getInstanceMatcher(ComparisonType comparisonType)
        throws Exception {
        InstanceMatcher instanceMatcher;
        if (comparisonType == ComparisonType.EVENT) {
            instanceMatcher = new EventMatcher(
                getDbAdaptorMap().getOlderDbAdaptor(),
                getDbAdaptorMap().getNewerDbAdaptor(),
                getDbAdaptorMap().getTargetDbAdaptor());
        } else {
            instanceMatcher = new PhysicalEntityMatcher(
                getDbAdaptorMap().getOlderDbAdaptor(),
                getDbAdaptorMap().getNewerDbAdaptor(),
                getDbAdaptorMap().getTargetDbAdaptor());
        }
        return instanceMatcher;
    }

    private void commitToSourceDB(List<SimpleInstance> instances) throws Exception {


        if (instances == null || instances.size() == 0)
            return; // Nothing to do.

        for (SimpleInstance instance : instances) {
            curatorToolWSAPI.commit(instance);
        }
    }

    private DbAdaptorMap getDbAdaptorMap() {
        return this.dbAdaptorMap;
    }

    private MySQLAdaptor getSourceDBA() {
        return getDbAdaptorMap().getTargetDbAdaptor();
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

    private UpdateTracker.UpdateTrackerBuilder getUpdateTrackerBuilder() throws Exception {
        return UpdateTracker.UpdateTrackerBuilder.createUpdateTrackerBuilder(releaseInstance, getCreatedInstanceEdit());
    }

    private InstanceEdit getCreatedInstanceEdit() {
        return this.createdInstanceEdit;
    }

    private static class DbAdaptorMap {
        private MySQLAdaptor olderDbAdaptor;
        private MySQLAdaptor newerDbAdaptor;
        private MySQLAdaptor targetDbAdaptor;

        private DbAdaptorMap() {}

        public MySQLAdaptor getOlderDbAdaptor() {
            return this.olderDbAdaptor;
        }

        public MySQLAdaptor getNewerDbAdaptor() {
            return this.newerDbAdaptor;
        }

        public MySQLAdaptor getTargetDbAdaptor() {
            return this.targetDbAdaptor;
        }

        private void setOlderDbAdaptor(MySQLAdaptor olderDbAdaptor) {
            this.olderDbAdaptor = olderDbAdaptor;
        }

        private void setNewerDbAdaptor(MySQLAdaptor newerDbAdaptor) {
            this.newerDbAdaptor = newerDbAdaptor;
        }

        private void setTargetDbAdaptor(MySQLAdaptor targetDbAdaptor) {
            this.targetDbAdaptor = targetDbAdaptor;
        }

        private static class DbAdaptorMapBuilder {
            private MySQLAdaptor olderDbAdaptor;
            private MySQLAdaptor newerDbAdaptor;
            private MySQLAdaptor targetDbAdaptor;

            public DbAdaptorMapBuilder() {}

            private void setOlderDbAdaptor(MySQLAdaptor olderDbAdaptor) {
                this.olderDbAdaptor = olderDbAdaptor;
            }

            private void setNewerDbAdaptor(MySQLAdaptor newerDbAdaptor) {
                this.newerDbAdaptor = newerDbAdaptor;
            }

            private void setTargetDbAdaptor(MySQLAdaptor targetDbAdaptor) {
                this.targetDbAdaptor = targetDbAdaptor;
            }

            public DbAdaptorMap build() {
                DbAdaptorMap dbAdaptorMap = new DbAdaptorMap();
                dbAdaptorMap.setOlderDbAdaptor(this.olderDbAdaptor);
                dbAdaptorMap.setNewerDbAdaptor(this.newerDbAdaptor);
                dbAdaptorMap.setTargetDbAdaptor(this.targetDbAdaptor);
                return dbAdaptorMap;
            }
        }
    }

    private enum ComparisonType {
        EVENT,
        PHYSICAL_ENTITY
    }
}
