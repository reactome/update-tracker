package org.reactome.updateTracker.matcher;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.updateTracker.utils.CuratorToolWSAPI;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public abstract class InstanceMatcher {
    private MySQLAdaptor previousDBA;
    private MySQLAdaptor currentDBA;

    private Map<GKInstance, GKInstance> previousToCurrentInstances;
    private Map<GKInstance, GKInstance> currentToPreviousInstances;

    private Map<GKInstance, GKInstance> curationPreviousToCurrentInstances;
    private Map<GKInstance, GKInstance> curationCurrentToPreviousInstances;

    private List<GKInstance> removedInstances;
    private List<GKInstance> addedInstances;

    public InstanceMatcher(MySQLAdaptor previousDBA, MySQLAdaptor currentDBA)
        throws Exception {

        this.previousDBA = previousDBA;
        this.currentDBA = currentDBA;

        analyzeInstances();
    }

    public MySQLAdaptor getPreviousDBA() {
        return this.previousDBA;
    }

    public MySQLAdaptor getCurrentDBA() {
        return this.currentDBA;
    }

    public Map<GKInstance, GKInstance> getCurrentToPreviousInstanceMap() {
        return this.currentToPreviousInstances;
    }

    public Map<GKInstance, GKInstance> getPreviousToCurrentInstanceMap() {
        return this.previousToCurrentInstances;
    }

//    public Map<GKInstance, GKInstance> getCurationPreviousToCurrentInstanceMap() {
//        return this.curationPreviousToCurrentInstances;
//    }
//
//    public Map<GKInstance, GKInstance> getCurationCurrentToPreviousInstances() {
//        return this.curationCurrentToPreviousInstances;
//    }

    public List<GKInstance> getRemovedInstances() {
        return this.removedInstances;
    }

    public List<GKInstance> getAddedInstances() {
        return this.addedInstances;
    }

    protected abstract List<GKInstance> getManuallyCuratedInstances(MySQLAdaptor dbAdaptor) throws Exception;

    protected GKInstance getEquivalentInstance(
        GKInstance instance, List<GKInstance> instancesToCheck, boolean checkClass) {
        GKInstance equivalentInstance = instancesToCheck.stream().filter(
            instanceToCheck -> instanceToCheck.getDBID().equals(instance.getDBID())
        ).findFirst().orElse(null);

        if (equivalentInstance != null) {
            // Quality Check
            if (differentSchemaClasses(equivalentInstance, instance)) {
                if (checkClass) {
                    System.err.println("Equivalent instance: " + equivalentInstance + " and original instance: " +
                        instance + " have different classes");
                    return null;
                } else {
                    return null;
                }
            }
        }

        return equivalentInstance;
    }

    protected List<SimpleInstance> getEquivalentInstances(List<GKInstance> instances, boolean checkClass) {
        CuratorToolWSAPI curatorToolWSAPI = new CuratorToolWSAPI();
        List<Long> instanceDbIds = instances.stream().map(GKInstance::getDBID).collect(Collectors.toList());
        List<SimpleInstance> equivalentInstancesAsSimpleInstances =
            curatorToolWSAPI.findDatabaseObjectsByDbIds(instanceDbIds);

//        if (equivalentInstanceAsSimpleInstance == null) {
//            return null;
//        }

//        if (differentSchemaClasses(equivalentInstanceAsSimpleInstance, instance)) {
//            if (checkClass) {
//                System.err.println("Equivalent instance: " + equivalentInstanceAsSimpleInstance +
//                    " and original instance: " + instance + " have different classes");
//            }
//            return null;
//        }

        return equivalentInstancesAsSimpleInstances;
    }

    protected boolean differentSchemaClasses(GKInstance previousInstance, GKInstance currentInstance) {
        String previousInstanceSchemaClass = previousInstance.getSchemClass().getName();
        String currentInstanceSchemaClass = currentInstance.getSchemClass().getName();

        return !previousInstanceSchemaClass.equals(currentInstanceSchemaClass);
    }

    @SuppressWarnings("unchecked")
    protected List<GKInstance> getAllInstancesFromDBA(MySQLAdaptor dba) throws Exception {
        return new ArrayList<>((Collection<GKInstance>) dba.fetchInstancesByClass(getInstanceType()));
    }

    protected abstract String getInstanceType();

    private void analyzeInstances() throws Exception {
        Map<GKInstance, GKInstance> previousToCurrentInstances = new LinkedHashMap<>();
        Map<GKInstance, GKInstance> currentToPreviousInstances = new LinkedHashMap<>();

        //Map<GKInstance, GKInstance> curationPreviousToCurrentInstances = new LinkedHashMap<>();
        //Map<GKInstance, GKInstance> curationCurrentToPreviousInstances = new LinkedHashMap<>();

        List<GKInstance> removedInstances = new ArrayList<>();
        List<GKInstance> addedInstances = new ArrayList<>();

        List<GKInstance> previousInstances = getManuallyCuratedInstances(this.previousDBA);
        List<GKInstance> currentInstances = getManuallyCuratedInstances(this.currentDBA);

        //List<SimpleInstance> equivalentCurationInstances = getEquivalentInstances(currentInstances, false);
        //Map<GKInstance, SimpleInstance> currentToCurationInstances = getCurrentToCurationInstanceMapping(currentInstances, equivalentCurationInstances);

        for (GKInstance currentInstance : currentInstances) {
            GKInstance previousInstance = getEquivalentInstance(currentInstance, previousInstances, true);
            if (previousInstance == null) {
                addedInstances.add(currentInstance);
            } else {
                if (previousToCurrentInstances.containsKey(previousInstance)) {
                    throw new IllegalStateException(
                        "Trying to add " + currentInstance + " but previous instance " +
                            previousInstance + " already has a " + "current instance: " +
                            previousToCurrentInstances.get(previousInstance)
                    );
                } else if (currentToPreviousInstances.containsKey(currentInstance)) {
                    throw new IllegalStateException(
                        "Trying to add " + previousInstance + " but current instance " +
                            currentInstance + " already has a previous instance: " +
                            currentToPreviousInstances.get(currentInstance)
                    );
                }

                previousToCurrentInstances.put(previousInstance, currentInstance);
                currentToPreviousInstances.put(currentInstance, previousInstance);

//                SimpleInstance curationInstance = currentToCurationInstances.get(currentInstance);
//                if (curationInstance != null) {
//                    curationPreviousToCurrentInstances.put(previousInstance, currentInstance);
//                    curationCurrentToPreviousInstances.put(currentInstance, previousInstance);
//                }
            }
        }

        for (GKInstance previousInstance : previousInstances) {
            if (!previousToCurrentInstances.containsKey(previousInstance)) {
                removedInstances.add(previousInstance);
            }
        }

        this.previousToCurrentInstances = previousToCurrentInstances;
        this.currentToPreviousInstances = currentToPreviousInstances;
        //this.curationPreviousToCurrentInstances = curationPreviousToCurrentInstances;
        //this.curationCurrentToPreviousInstances = curationCurrentToPreviousInstances;
        this.addedInstances = addedInstances;
        this.removedInstances = removedInstances;
    }

    private Map<GKInstance, SimpleInstance> getCurrentToCurationInstanceMapping(List<GKInstance> currentInstances, List<SimpleInstance> curationInstances) {
        Map<Long, GKInstance> dbIdToCurrentInstance = currentInstances.stream().collect(Collectors.toMap(
            GKInstance::getDBID,
            instance -> instance
        ));
        Map<Long, SimpleInstance> dbIdToCurationInstance = curationInstances.stream().collect(Collectors.toMap(
            SimpleInstance::getDbId,
            instance -> instance
        ));

        Map<GKInstance, SimpleInstance> currentToCurationInstance = new HashMap<>();
        for (long dbId : dbIdToCurrentInstance.keySet()) {
            GKInstance currentInstance = dbIdToCurrentInstance.get(dbId);
            SimpleInstance curationInstance = dbIdToCurationInstance.get(dbId);

            if (currentToCurationInstance.containsKey(currentInstance)) {
                throw new IllegalStateException("Already seen " + currentInstance);
            }

            currentToCurationInstance.put(currentInstance, curationInstance);
        }
        return currentToCurationInstance;
    }


    private boolean differentSchemaClasses(SimpleInstance previousInstance, GKInstance currentInstance) {
        String previousInstanceSchemaClass = previousInstance.getSchemaClassName();
        String currentInstanceSchemaClass = currentInstance.getSchemClass().getName();

        return !previousInstanceSchemaClass.equals(currentInstanceSchemaClass);
    }
}
