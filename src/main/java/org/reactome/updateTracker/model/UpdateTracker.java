package org.reactome.updateTracker.model;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.server.graph.domain.model.InstanceEdit;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.reactome.updateTracker.utils.DBUtils.getExtendedDisplayName;
import static org.reactome.updateTracker.utils.DBUtils.getSchemaClass;

/**
 * This class is used to model _UpdateTracker class. The constructor is private. The client should 
 * get the embedded UpdateTrackerBuilder to construct UpdateTracker objects.
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class UpdateTracker {
    private SimpleInstance _Release;
    private int releaseNumber;
    private long personId;
    private Set<Action> actions;
    private SimpleInstance updatedInstance;
    private InstanceEdit createdInstanceEdit;

    private UpdateTracker(SimpleInstance _Release, int releaseNumber, long personId, InstanceEdit createdInstanceEdit,
                          Set<Action> actions, SimpleInstance updatedInstance) {
        this._Release = _Release;
        this.releaseNumber = releaseNumber;
        this.personId = personId;
        this.createdInstanceEdit = createdInstanceEdit;
        this.actions = actions;
        this.updatedInstance = updatedInstance;
    }

    public SimpleInstance getReleaseInstance() {
        return this._Release;
    }

    public Set<Action> getActions() {
        return this.actions;
    }

    public SimpleInstance getUpdatedInstance() {
        return this.updatedInstance;
    }

    private SchemaClass getUpdateTrackerSchemaClass(MySQLAdaptor dbAdaptor) throws Exception {
        return getSchemaClass(dbAdaptor, "_UpdateTracker");
    }

    public SimpleInstance createUpdateTrackerInstance() {
        SimpleInstance updateTrackerInstance = new SimpleInstance();
        updateTrackerInstance.setDbId(-1L);
        updateTrackerInstance.setDefaultPersonId(getPersonId());
        updateTrackerInstance.setSchemaClassName("UpdateTracker");

        updateTrackerInstance.setAttribute("release", getReleaseInstance());
        updateTrackerInstance.setAttribute("action", getActionsAsStrings());
        updateTrackerInstance.setAttribute("updatedInstance", Collections.singletonList(getUpdatedInstance()));
        //updateTrackerInstance.setAttribute(ReactomeJavaConstants.created, getCreatedInstanceEdit());

        updateTrackerInstance.setDisplayName(generateDisplayName());

        return updateTrackerInstance;
    }


    private String generateDisplayName() {
        return String.format(
            "Update Tracker - %s - v%d:%s",
            getExtendedDisplayName(getUpdatedInstance()),
            getReleaseNumber(),
            getActionsAsStrings()
        );
    }

    private List<String> getActionsAsStrings() {
        return getActions().stream()
            .map(Action::toString)
            .collect(Collectors.toList());
    }

    private long getPersonId() {
        return this.personId;
    }

    private InstanceEdit getCreatedInstanceEdit() {
        return this.createdInstanceEdit;
    }

    private String getSchemaClassName(GKInstance instance) {
        return updatedInstance.getSchemaClassName();
    }

    private int getReleaseNumber() {
        return this.releaseNumber;
    }
    
    /**
     * The factory class to build UpdateTracker instance.
     * @author wug
     *
     */
    public static class UpdateTrackerBuilder {
        private Integer releaseNumber;
        private long personId;
        private SimpleInstance _Release;
        private InstanceEdit createdInstanceEdit;

        public static UpdateTrackerBuilder createUpdateTrackerBuilder(SimpleInstance _Release,
                                                                      long personId,
                                                                      InstanceEdit createdInstanceEdit) {
            return new UpdateTrackerBuilder(_Release, personId, createdInstanceEdit);
        }

        private UpdateTrackerBuilder(SimpleInstance _Release, long personId, InstanceEdit createdInstanceEdit) {
            this._Release = _Release;
            this.personId = personId;
            this.createdInstanceEdit = createdInstanceEdit;
            this.releaseNumber = getReleaseNumber();
        }

        private int getReleaseNumber() {
            if (this.releaseNumber == null) {
                try {
                    this.releaseNumber = (int) this._Release.getAttribute(ReactomeJavaConstants.releaseNumber);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to get release number from " +
                        (this._Release != null ? getExtendedDisplayName(this._Release) : null), e);
                }
            }
            return this.releaseNumber;
        }

        private long getPersonId() {
            return this.personId;
        }

        public UpdateTracker build(SimpleInstance updatedInstance, Set<Action> actions) {
            return new UpdateTracker(
                this._Release, this.releaseNumber, this.personId, this.createdInstanceEdit, actions, updatedInstance);
        }
    }
}
