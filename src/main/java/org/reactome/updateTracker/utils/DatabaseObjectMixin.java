package org.reactome.updateTracker.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 10/4/2025
 */
@JsonIgnoreProperties({ "created", "modified" })
public abstract class DatabaseObjectMixin { }
