package org.reactome.updateTracker.utils;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaAttribute;
import org.reactome.curation.model.SimpleInstance;

import java.util.*;

import static org.reactome.updateTracker.utils.DBUtils.getSchemaClassName;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 9/9/2025
 */
public class GraphDBConverter {
	public static SimpleInstance convertGKInstanceToSimpleInstance(GKInstance gkInstance) throws Exception {
		return convertGKInstanceToSimpleInstance(gkInstance, new HashMap<>());
	}

	private static SimpleInstance convertGKInstanceToSimpleInstance(
		GKInstance gkInstance,
		Map<Long, SimpleInstance> visited) throws Exception {

		final long personId = 1551959L;
		if (gkInstance == null) {
			return null;
		}

		long dbId = gkInstance.getDBID();

		if (visited.containsKey(dbId)) {
			return visited.get(dbId);
		}

		SimpleInstance simpleInstance = new SimpleInstance();
		simpleInstance.setDbId(dbId);
		simpleInstance.setDisplayName(gkInstance.getDisplayName());
		simpleInstance.setSchemaClassName(getSchemaClassName(gkInstance));
		simpleInstance.setDefaultPersonId(personId);

		visited.put(dbId, simpleInstance);

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

					GKInstance attributeValue =
						(GKInstance) gkInstance.getAttributeValue(attribute);

					SimpleInstance converted = fetchFromGraphDb(attributeValue);
					if (converted == null) {
						converted = convertGKInstanceToSimpleInstance(attributeValue, visited);
					}

					if (!attribute.getName().equals(ReactomeJavaConstants._release)) {
						if (!attribute.getName().equals("updatedInstance")) {
							simpleInstance.setAttribute(attribute.getName(), converted);
						} else {
							simpleInstance.setAttribute(attribute.getName(), Collections.singletonList(converted));
						}
					} else {
						simpleInstance.setAttribute("release", converted);
					}

				} else {

					List<GKInstance> attributeValues =
						gkInstance.getAttributeValuesList(attribute);

					List<SimpleInstance> convertedList = new ArrayList<>();

					for (GKInstance attributeValue : attributeValues) {
						SimpleInstance converted = fetchFromGraphDb(attributeValue);
						if (converted == null) {
							converted = convertGKInstanceToSimpleInstance(attributeValue, visited);
						}
						convertedList.add(converted);


//						convertedList.add(
//							convertGKInstanceToSimpleInstance(attributeValue, visited)
//						);
					}

					simpleInstance.setAttribute(attribute.getName(), convertedList);
				}

			} else {

				if (!attribute.isMultiple()) {
					simpleInstance.setAttribute(
						attribute.getName(),
						gkInstance.getAttributeValue(attribute)
					);
				} else {
					simpleInstance.setAttribute(
						attribute.getName(),
						gkInstance.getAttributeValuesList(attribute)
					);
				}
			}
		}

		return simpleInstance;
	}


	private static boolean valueIsNull(GKInstance gkInstance, SchemaAttribute attribute) throws Exception {
		return gkInstance.getAttributeValue(attribute) == null;
	}

	private static SimpleInstance fetchFromGraphDb(GKInstance instance) {
		System.out.println("Fetching: " + instance);
		CuratorToolWSAPI curatorToolWSAPI = new CuratorToolWSAPI();
		return curatorToolWSAPI.findDatabaseObjectByDbId(instance.getDBID());
	}
}
