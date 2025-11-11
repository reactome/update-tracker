package org.reactome.updateTracker.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.curation.user.model.User;
import org.reactome.server.graph.domain.model.Person;

import java.io.IOException;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 9/18/2025
 */
public class CuratorToolWSAPI {
	private static final String HOST_URL = "http://localhost:9090/api/";
	private static final String AUTH_URL = HOST_URL + "authenticate";
	private static final String FIND_BY_DB_ID = HOST_URL + "curation/findByDbId/";
	private static final String FIND_DB_OBJ_BY_DB_ID = HOST_URL + "curation/findDatabaseObjectByDbId/";
	private static final String COMMIT_URL = HOST_URL + "curation/commit";

	private String jwtToken;

	public CuratorToolWSAPI() {
		this.jwtToken = this.fetchJwtToken("test", "password");
		System.out.println(this.jwtToken);
	}

	public void commit(SimpleInstance simpleInstance) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.addMixIn(org.reactome.curation.model.SimpleInstance.class, DatabaseObjectMixin.class);
		mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpPost post = new HttpPost(COMMIT_URL);
			post.setHeader("Content-Type", "application/json");
			post.setHeader("Authorization", "Bearer " + getJwtToken());

			String simpleInstanceJSON = mapper.writeValueAsString(simpleInstance);
			System.out.println(simpleInstance);
			System.out.println(simpleInstanceJSON);



			post.setEntity(new StringEntity(simpleInstanceJSON));
			HttpResponse response = httpClient.execute(post);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + statusCode);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error committing simple instance " + simpleInstance + " to API", e);
		}
	}

	public SimpleInstance findDatabaseObjectByDbId(long dbId) {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet request = new HttpGet(FIND_BY_DB_ID + dbId);
			request.setHeader("Accept", "application/json");
			request.setHeader("Authorization", "Bearer " + getJwtToken());
			HttpResponse response = httpClient.execute(request);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + statusCode);
			}
			String json = EntityUtils.toString(response.getEntity());
			System.out.println("JSON: " + json);
			ObjectMapper objectMapper = new ObjectMapper();
			return objectMapper.readValue(json, SimpleInstance.class);
		}
		catch (Exception e) {
			throw new RuntimeException("Error fetching SimpleInstance from API", e);
		}
	}

	public Person fetchPersonInstance(long personDbId) {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet request = new HttpGet(FIND_DB_OBJ_BY_DB_ID + personDbId);
			System.out.println(FIND_DB_OBJ_BY_DB_ID + personDbId);
			request.setHeader("Accept", "application/json");
			request.setHeader("Authorization", "Bearer " + getJwtToken());
			HttpResponse response = httpClient.execute(request);
			System.out.println(response);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + statusCode);
			}
			String json = EntityUtils.toString(response.getEntity());
			System.out.println("JSON: " + json);
			ObjectMapper objectMapper = new ObjectMapper();
			return objectMapper.readValue(json, Person.class);
		}
		catch (Exception e) {
			throw new RuntimeException("Error fetching SimpleInstance from API", e);
		}
	}

	private String fetchJwtToken(String username, String password) {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpPost post = new HttpPost(AUTH_URL);
			post.setHeader("Content-Type", "application/json");
			ObjectMapper mapper = new ObjectMapper();
			String jsonObj = mapper.writeValueAsString(new User(username, password));
			post.setEntity(new StringEntity(jsonObj));
			HttpResponse response = httpClient.execute(post);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + statusCode);
			}
			String jwt = EntityUtils.toString(response.getEntity());
			if (jwt.startsWith("\"") && jwt.endsWith("\"")) {
				jwt = jwt.substring(1, jwt.length() - 1);
			}
			this.jwtToken = jwt;
			return jwt;
		} catch (Exception e) {
			throw new RuntimeException("Error fetching JWT token from API", e);
		}
	}

	private String getJwtToken() {
		return this.jwtToken;
	}
}