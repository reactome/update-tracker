package org.reactome.updateTracker;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;

import org.gk.persistence.MySQLAdaptor;
import org.reactome.curation.model.SimpleInstance;
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
    private GKInstance createdInstanceEdit;

    private CuratorToolWSAPI curatorToolWSAPI;

    public UpdateTrackerHandler(
        MySQLAdaptor currentSliceDBA, MySQLAdaptor previousSliceDBA, long personId
    ) throws Exception {
        DbAdaptorMap.DbAdaptorMapBuilder dbAdaptorMapBuilder = new DbAdaptorMap.DbAdaptorMapBuilder();
        dbAdaptorMapBuilder.setOlderDbAdaptor(previousSliceDBA);
        dbAdaptorMapBuilder.setNewerDbAdaptor(currentSliceDBA);

        this.curatorToolWSAPI = new CuratorToolWSAPI();

        this.dbAdaptorMap = dbAdaptorMapBuilder.build();
        this.personId = personId;
        this.createdInstanceEdit = DBUtils.getCreatedInstanceEdit(currentSliceDBA, personId);

    }

    public void handleUpdateTrackerInstances(boolean uploadUpdateTrackerInstancesToSource) throws Exception {
//        if (uploadUpdateTrackerInstancesToSource) {
//            logger.info("Storing release instance in source database");
//            storeReleaseInstanceInSourceDatabase();
//        }

        logger.info("Creating event update tracker instances");
        createAndStoreUpdateTrackerInstances(ComparisonType.EVENT, uploadUpdateTrackerInstancesToSource);

        logger.info("Creating physical entity update tracker instances");
        createAndStoreUpdateTrackerInstances(ComparisonType.PHYSICAL_ENTITY, uploadUpdateTrackerInstancesToSource);
    }
    
//    private void storeReleaseInstanceInSourceDatabase() throws Exception {
//        GKInstance releaseInstanceFromSlice = getMostRecentReleaseInstance(getCurrentSliceDBA());
//        releaseInstance = cloneReleaseInstance(releaseInstanceFromSlice);
//
//        SimpleInstance committedReleaseInstance = curatorToolWSAPI.commit(releaseInstance);
//        releaseInstance.setDbId(committedReleaseInstance.getDbId());
//    }

    private void createAndStoreUpdateTrackerInstances(
        ComparisonType comparisonType, boolean uploadUpdateTrackerInstancesToSource) throws Exception {

        UpdateTracker.UpdateTrackerBuilder sliceUpdateTrackerBuilder =
            getUpdateTrackerBuilder();

        InstanceMatcher instanceMatcher = getInstanceMatcher(comparisonType);

        logger.info("Getting " + comparisonType.name() + " instance pairs...");
        Set<Map.Entry<GKInstance,GKInstance>> equivalentInstancePairs =
            instanceMatcher.getCurrentToPreviousInstanceMap().entrySet();

        logger.info("Instance pairs size: " + equivalentInstancePairs.size());
        //List<SimpleInstance> toBeUploadedToSrcDBA = new ArrayList<>();
        for (Map.Entry<GKInstance, GKInstance> equivalentInstancePair : equivalentInstancePairs) {
            Set<Action> actions = getInstanceComparer(comparisonType, equivalentInstancePair)
                .getChanges(equivalentInstancePair);

            if (!actions.isEmpty()) {
                logger.info("Actions " + actions);
                GKInstance currentInstance = equivalentInstancePair.getValue();

                logger.info("Storing instance in current slice dba " + currentInstance);

                GKInstance updateTrackerGKInstance = sliceUpdateTrackerBuilder
                    .build(currentInstance, actions)
                    .createUpdateTrackerInstance(getCurrentSliceDBA());
                getCurrentSliceDBA().storeInstance(updateTrackerGKInstance);

                if (uploadUpdateTrackerInstancesToSource) {
                    logger.info("Adding toBeUploadedToSrcDBA " + currentInstance);

                    SimpleInstance updateTracker = GraphDBConverter.convertGKInstanceToSimpleInstance(updateTrackerGKInstance);
                    commitToSourceDB(updateTracker);
                    //toBeUploadedToSrcDBA.add(updateTracker);
                }
            }
        }
        //commitToSourceDB(toBeUploadedToSrcDBA);
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

    private UpdateTracker.UpdateTrackerBuilder getUpdateTrackerBuilder() throws Exception {
        GKInstance releaseInstance = getMostRecentReleaseInstance(getCurrentSliceDBA());

        return UpdateTracker.UpdateTrackerBuilder.createUpdateTrackerBuilder(
            releaseInstance, getPersonId(), getCreatedInstanceEdit());
    }

    private GKInstance getCreatedInstanceEdit() {
        return this.createdInstanceEdit;
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
