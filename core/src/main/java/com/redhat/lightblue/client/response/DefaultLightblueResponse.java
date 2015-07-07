package com.redhat.lightblue.client.response;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.redhat.lightblue.client.util.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultLightblueResponse implements LightblueResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLightblueResponse.class);
    private String text;
    private JsonNode json;
    private final ObjectMapper mapper;

    public DefaultLightblueResponse(ObjectMapper mapper) {
        if (mapper == null) {
            throw new NullPointerException("ObjectMapper instance cannot be null");
        }
        this.mapper = mapper;
    }

    public DefaultLightblueResponse() {
        this(JSON.getDefaultObjectMapper());
    }

    public DefaultLightblueResponse(String responseText) {
        this(responseText, JSON.getDefaultObjectMapper());
    }

    public DefaultLightblueResponse(String responseText, ObjectMapper mapper) {
        this(mapper);
        this.text = responseText;
        try {
            json = mapper.readTree(responseText);
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse response: " + responseText, e);
        }
    }

    @Override
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public JsonNode getJson() {
        return json;
    }

    public void setJson(JsonNode json) {
        this.json = json;
    }

    @Override
    public boolean hasDataErrors() {
        JsonNode err=json.get("dataErrors");
        return err!=null&&!(err instanceof NullNode)&&err.size()>0;
    }
 
    @Override
    public boolean hasError() {
        JsonNode objectTypeNode = json.get("status");
        if (objectTypeNode == null) {
            return false;
        }
        JsonNode err=json.get("errors");
        if(err!=null&&!(err instanceof NullNode))
            return true;
        err=json.get("dataErrors");
        if(err!=null&&(err instanceof ArrayNode))
            if(err.size()>0)
                return true;
        return objectTypeNode.textValue().equalsIgnoreCase("error")
                || objectTypeNode.textValue().equalsIgnoreCase("partial");
    }

    @Override
    public JsonNode[] getErrors() {
        return getErrors("errors");
    }

    @Override
    public JsonNode[] getDataErrors() {
        return getErrors("dataErrors");
    }
    
    private  JsonNode[] getErrors(String fld) {
        List<JsonNode> list=new ArrayList<>();
        JsonNode err=json.get(fld);
        if(err instanceof ObjectNode)
            list.add(err);
        else if(err instanceof ArrayNode)
            for(Iterator<JsonNode> itr=((ArrayNode)err).elements();itr.hasNext();)
                list.add(itr.next());
        else
            return null;
        return list.toArray(new JsonNode[list.size()]);
    }

    
    @Override
    public int parseModifiedCount() {
        return parseInt("modifiedCount");
    }

    @Override
    public int parseMatchCount() {
        return parseInt("matchCount");
    }

    private int parseInt(String fieldName) {
        JsonNode field = json.findValue(fieldName);
        if (field == null || field.isNull()) {
            return 0;
        }
        return field.asInt();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T parseProcessed(final Class<T> type)
            throws LightblueResponseParseException {
        if (hasError()) {
            throw new LightblueErrorResponseException("Error returned in response: " + getText());
        }

        try {
            JsonNode processedNode = json.path("processed");

            //if null or an empty array
            if (processedNode == null
                    || processedNode.isNull()
                    || processedNode.isMissingNode()
                    || (processedNode.isArray() && !((ArrayNode) processedNode).iterator().hasNext())) {
                if (type.isArray()) {
                    return (T) Array.newInstance(type.getComponentType(), 0);
                }
                return null;
            }
            if (!type.isArray()){
                if (processedNode.size() > 1){
                    throw new LightblueResponseParseException("Was expecting single result:" + getText() + "\n");
                } else {
                    return mapper.readValue(processedNode.get(0).traverse(), type);
                }
            } else {
                return mapper.readValue(processedNode.traverse(), type);
            }
        } catch (RuntimeException | IOException e) {
            throw new LightblueResponseParseException("Error parsing lightblue response: " + getText() + "\n", e);
        }
    }

}
