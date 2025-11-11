package org.reactome.updateTracker.utils;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.Schema;
import org.gk.schema.SchemaAttribute;
import org.reactome.curation.model.SimpleInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 9/9/2025
 */
public class GraphDBConverter {

	public static SimpleInstance convertGKInstanceToSimpleInstance(GKInstance gkInstance) throws Exception {
		if (gkInstance == null) {
			return null;
		}

		SimpleInstance simpleInstance = new SimpleInstance();
		simpleInstance.setDbId(-1L);
		simpleInstance.setDisplayName(gkInstance.getDisplayName());
		simpleInstance.setSchemaClassName(getSchemaClassName(gkInstance));

		Collection<SchemaAttribute> attributes = gkInstance.getSchemClass().getAttributes();
		for (SchemaAttribute attribute : attributes) {
			if (attribute.getName().equals(ReactomeJavaConstants.DB_ID) ||
				attribute.getName().equals(ReactomeJavaConstants._displayName)) {
				continue;
			}

			if (valueIsNull(gkInstance, attribute)) {
				continue;
			}

			if (attribute.isInstanceTypeAttribute()) {
				if (!attribute.isMultiple()) {
					GKInstance attributeValue = (GKInstance) gkInstance.getAttributeValue(attribute);
					SimpleInstance simpleInstanceAttributeValue = convertGKInstanceToSimpleInstance(attributeValue);
					simpleInstance.setAttribute(attribute.getName(), simpleInstanceAttributeValue);

				} else {
					List<GKInstance> attributeValues = gkInstance.getAttributeValuesList(attribute);
					List<SimpleInstance> simpleInstanceAttributeValues = new ArrayList<>();
					for (GKInstance attributeValue : attributeValues) {
						simpleInstanceAttributeValues.add(convertGKInstanceToSimpleInstance(attributeValue));
					}
					simpleInstance.setAttribute(attribute.getName(), simpleInstanceAttributeValues);
				}
 			} else {
				if (!attribute.isMultiple()) {
					Object attributeValue = gkInstance.getAttributeValue(attribute);
					simpleInstance.setAttribute(attribute.getName(), attributeValue);
				} else {
					List<Object> attributeValues = gkInstance.getAttributeValuesList(attribute);
					simpleInstance.setAttribute(attribute.getName(), attributeValues);
				}
			}
		}
		return simpleInstance;
	}

	private static String getSchemaClassName(GKInstance gkInstance) {
		String schemaClassName = gkInstance.getSchemClass().getName();
		if (schemaClassName.startsWith("_")) {
			return schemaClassName.replaceFirst("_", "");
		}
		return schemaClassName;
	}

	private static boolean valueIsNull(GKInstance gkInstance, SchemaAttribute attribute) throws Exception {
		return gkInstance.getAttributeValue(attribute) == null;
	}
}
