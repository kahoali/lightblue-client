package com.redhat.lightblue.client.integration.test;

import static com.redhat.lightblue.client.expression.query.ValueQuery.withValue;
import static com.redhat.lightblue.client.projection.FieldProjection.includeField;
import static com.redhat.lightblue.util.test.AbstractJsonNodeTest.loadJsonNode;
import static org.junit.Assert.assertEquals;

import java.net.UnknownHostException;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.lightblue.client.Projection;
import com.redhat.lightblue.client.Query;
import com.redhat.lightblue.client.http.LightblueHttpClient;
import com.redhat.lightblue.client.integration.test.pojo.Country;
import com.redhat.lightblue.client.request.BulkLightblueDataRequest;
import com.redhat.lightblue.client.request.data.DataFindRequest;
import com.redhat.lightblue.client.request.data.DataInsertRequest;
import com.redhat.lightblue.client.response.BulkLightblueResponse;
import com.redhat.lightblue.client.response.LightblueException;
import com.redhat.lightblue.client.response.LightblueResponse;
import com.redhat.lightblue.client.response.LightblueResponseParseException;

/**
 * Testing your code against lightblue example.
 *
 * @author mpatercz
 *
 */
public class CountryDAOTest extends AbstractLightblueClientCRUDController {

    private LightblueHttpClient client;

    @Before
    public void before() throws UnknownHostException {
        client = getLightblueClient();
        cleanupMongoCollections(Country.objectType);
    }

    public CountryDAOTest() throws Exception {
        super();
    }

    @Override
    protected JsonNode[] getMetadataJsonNodes() throws Exception {
        return new JsonNode[] { loadJsonNode("metadata/country.json") };
    }

    private Country insertPL() throws LightblueException {
        Country c = new Country();
        c.setName("Poland");
        c.setIso2Code("PL");
        c.setIso3Code("POL");

        DataInsertRequest request = new DataInsertRequest(Country.objectType, Country.objectVersion);

        request.create(c);
        request.returns(includeField("*"));
        return client.data(request, Country[].class)[0];
    }

    @Test
    public void testInsertCountry() throws LightblueException {
        Assert.assertEquals("PL", insertPL().getIso2Code());
    }

    @Test
    public void testBulkFind() throws LightblueException, LightblueResponseParseException {
        Country country = insertPL();

        DataFindRequest request = new DataFindRequest(Country.objectType, Country.objectVersion);
        request.select(Projection.includeField("*"));
        request.where(Query.withValue("iso2Code", Query.eq, "PL"));

        DataFindRequest request2 = new DataFindRequest(Country.objectType, Country.objectVersion);
        request2.select(Projection.includeField("*"));
        request2.where(Query.withValue("name", Query.eq, "Poland"));

        DataFindRequest request3 = new DataFindRequest(Country.objectType, Country.objectVersion);
        request3.select(Projection.includeField("*"));
        request3.where(Query.withValue("name", Query.eq, "Russia"));

        BulkLightblueDataRequest bulkRequest = new BulkLightblueDataRequest();
        bulkRequest.add(request);
        bulkRequest.add(request2);
        bulkRequest.add(request3);

        BulkLightblueResponse bulkResponse = client.bulkData(bulkRequest);
        List<LightblueResponse> responses = bulkResponse.getResponses();

        assertEquals(3, responses.size());
        assertEquals(1, responses.get(0).parseMatchCount());
        assertEquals(1, responses.get(1).parseMatchCount());
        assertEquals(0, responses.get(2).parseMatchCount());

        Country c1 = responses.get(0).parseProcessed(Country.class);
        Country c2 = responses.get(1).parseProcessed(Country.class);

        assertEquals("PL", c1.getIso2Code());
        assertEquals("PL", c2.getIso2Code());

        assertEquals(responses.get(0), bulkResponse.getResponse(request));
    }

    @Test
    public void testDirectMongoCleanup() throws LightblueException, UnknownHostException {
        cleanupMongoCollections(Country.objectType);
        insertPL();

        cleanupMongoCollections(Country.objectType);

        DataFindRequest request = new DataFindRequest(Country.objectType, Country.objectVersion);
        request.where(withValue("objectType = country"));
        request.select(includeField("*"));
        Country[] countries = client.data(request, Country[].class);

        Assert.assertTrue(countries.length == 0);
    }

}