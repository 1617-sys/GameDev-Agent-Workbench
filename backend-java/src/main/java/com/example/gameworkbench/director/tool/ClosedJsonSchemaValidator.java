package com.example.gameworkbench.director.tool;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;

public final class ClosedJsonSchemaValidator {
    public void validate(JsonNode value,JsonNode schema) {
        if(schema==null || !schema.isObject()) fail();
        String type=schema.path("type").asText();
        switch(type) {
            case "object" -> object(value,schema);
            case "string" -> { if(!value.isTextual()) fail(); string(value,schema); }
            case "integer" -> { if(!value.isIntegralNumber()) fail(); number(value,schema); }
            case "number" -> { if(!value.isNumber()) fail(); number(value,schema); }
            case "boolean" -> { if(!value.isBoolean()) fail(); }
            case "array" -> { if(!value.isArray()) fail(); for(JsonNode item:value) validate(item,schema.path("items")); }
            default -> fail();
        }
        if(schema.has("enum")){boolean found=false;for(JsonNode item:schema.path("enum"))found|=item.equals(value);if(!found)fail();}
    }
    private void object(JsonNode value,JsonNode schema) {
        if(!value.isObject() || !schema.path("additionalProperties").isBoolean() || schema.path("additionalProperties").asBoolean()) fail();
        JsonNode properties=schema.path("properties"); Set<String> required=new HashSet<>();
        if(schema.has("required")) for(JsonNode item:schema.path("required")) required.add(item.asText());
        for(String name:required) if(!value.has(name)) fail();
        Iterator<String> names=value.fieldNames();
        while(names.hasNext()){String name=names.next();if(!properties.has(name))fail();validate(value.get(name),properties.get(name));}
    }
    private void string(JsonNode value,JsonNode schema){
        String text=value.asText();if(schema.has("minLength")&&text.length()<schema.path("minLength").asInt())fail();
        if(schema.has("maxLength")&&text.length()>schema.path("maxLength").asInt())fail();
        if(schema.has("pattern")&&!Pattern.matches(schema.path("pattern").asText(),text))fail();
    }
    private void number(JsonNode value,JsonNode schema){double number=value.asDouble();if(schema.has("minimum")&&number<schema.path("minimum").asDouble())fail();if(schema.has("maximum")&&number>schema.path("maximum").asDouble())fail();}
    private void fail(){throw new IllegalArgumentException("arguments violate closed JSON schema");}
}
